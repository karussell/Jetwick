/**
 * Copyright (C) 2010 Peter Karich <jetwick_@_pannous_._info>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.jetwick.hib;

import de.jetwick.data.YTag;
import de.jetwick.data.YUser;
import de.jetwick.util.Helper;
import java.io.BufferedReader;
import java.sql.Connection;
import java.sql.SQLException;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.cfg.Configuration;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.hbm2ddl.SchemaUpdate;

import java.io.File;
import java.io.IOException;
import liquibase.FileSystemFileOpener;
import liquibase.Liquibase;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.jdbc.Work;
import org.hibernate.stat.EntityStatistics;
import org.hibernate.stat.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Register POJOs as entities and provide a SessionFactory
 *
 * TODO refactor to delete HibernateUtil and use guice provider for configuration
 * => then we can easily change thread to managed (current_session_context_class)
 */
public final class HibernateUtil {

    private static Logger log = LoggerFactory.getLogger(HibernateUtil.class);
    private static final Class<?> mappedClasses[] = new Class<?>[]{
        YTag.class, YUser.class};
    private static Configuration configuration;
    private static SessionFactory sessionFactory;

    /**
     * Returns the configuration object. configure() was already called.
     */
    public static synchronized Configuration getConfiguration() {
        if (configuration == null) {
            log.debug("Configuring from annotations");
            AnnotationConfiguration annoCfg = new AnnotationConfiguration();
            for (Class<?> c : mappedClasses) {
                annoCfg = annoCfg.addAnnotatedClass(c);
            }

            // if we specify                   -Djetwick...
            // if this doesn't exist look at   ~/.jetwick/hib...
            // if this doesn't exist look into classpath
            String hibLoc = System.getProperty("jetwick.hibernate.file");
            if (hibLoc == null) {
                String homeDir = System.getProperty("user.home");
                hibLoc = homeDir + "/.jetwick/hibernate.cfg.xml";
            }

            File alternateFile = new File(hibLoc);
            if (alternateFile.exists()) {
                log.info("Using hibernate config from " + alternateFile);
                configuration = annoCfg.configure(alternateFile);
            } else {
                log.info("Using hibernate config from classpath, because " + alternateFile + " does not exist.");
                configuration = annoCfg.configure();
            }
            //configuration.setProperty("hibernate.connection.username", "user");
            //configuration.setProperty("hibernate.connection.password", "pw");
            log.info("Using DB schema: " + configuration.getProperty("hibernate.default_schema"));
            //log.info("Current directory:" + new File("").getAbsolutePath());
        }
        return configuration;
    }

    public static void setConfiguration(Configuration config) {
        configuration = config;
    }

    /**
     * Drop and recreate the schema
     */
    public static void recreateSchemaFromMapping() {
        SchemaExport schemaTool = new SchemaExport(getConfiguration());
        // do not print the ddl to console
        log.info("drop schema");
        schemaTool.drop(false, true);
        log.info("create schema");
        // only data is allowed to be imported!
        // schemaTool.setImportFile("data.sql");
        schemaTool.create(false, true);
    }

    /**
     * Creates the tables from the liquibase data-definition-sql-statements
     */
    public static void recreateSchemaFromLiquibase() {
        recreateSchemaFromLiquibase(null);
    }

    public static void recreateSchemaFromLiquibase(String tmp) {
        final String file;
        if (tmp == null)
            file = "src/main/resources/dbchangelog.xml";
        else
            file = tmp;

        new JdbcHibernateBridge(getSessionFactory().openSession()) {

            @Override
            public void execute(Connection connection) throws SQLException {
                try {
                    Liquibase liquibase = new Liquibase(file,
                            new FileSystemFileOpener(), connection);

                    // the following statement will fail if 1.9.5 -> so 1.9.4
                    // see http://liquibase.org/forum/index.php?topic=486.0
                    liquibase.dropAll();

                    liquibase.update("");
                } catch (Exception ex) {
                    throw new UnsupportedOperationException(ex);
                }
            }
        }.run();

        log.info("Finished liquibase migration");
    }

    public static void updateSchemaFromMapping() {
        log.info("update schema");
        new SchemaUpdate(getConfiguration()).execute(false, false);
    }

    static synchronized SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            sessionFactory = getConfiguration().buildSessionFactory();
        }
        return sessionFactory;
    }

    public static String getStats(SessionFactory sf) {
        Statistics stats = sf.getStatistics();

        double queryCacheHitCount = stats.getQueryCacheHitCount();
        double queryCacheMissCount = stats.getQueryCacheMissCount();

        double queryCacheHitRatio =
                queryCacheHitCount / (queryCacheHitCount + queryCacheMissCount);

        StringBuilder sb = new StringBuilder();
        sb.append("queryCacheMissCount:" + queryCacheMissCount);
        sb.append("\n");
        sb.append("queryCacheHitCount:" + queryCacheHitCount);
        sb.append("\n");
        sb.append("Query Hit ratio:" + queryCacheHitRatio);
        sb.append("\n");

        EntityStatistics entityStats = stats.getEntityStatistics(YUser.class.getName());
        long changes = entityStats.getInsertCount()
                + entityStats.getUpdateCount()
                + entityStats.getDeleteCount();

        sb.append("InsertCount:" + entityStats.getInsertCount() + "\n");
        sb.append("DeleteCount:" + entityStats.getDeleteCount() + "\n");
        sb.append("UpdateCount:" + entityStats.getUpdateCount() + "\n");
        sb.append(YUser.class.getName() + " changed " + changes + " times\n");
        stats.clear();
        return sb.toString();
    }

    public static int executeSQLScript(String sqlFileOnClasspath) {
        Session sess = getSessionFactory().openSession();
        int sqlCmd = 0;
        BufferedReader bReader = null;
        Transaction ta = null;
        try {
            ta = sess.beginTransaction();
            bReader = Helper.createBuffReaderCP(sqlFileOnClasspath);
            String line;
            while ((line = bReader.readLine()) != null) {
                if (line.startsWith("--") || line.trim().isEmpty())
                    continue;

                final String tmp = line;
                sess.doWork(new Work() {

                    @Override
                    public void execute(Connection connection) throws SQLException {
                        connection.createStatement().execute(tmp);
                    }
                });
                sqlCmd++;
            }
            ta.commit();
        } catch (Exception ex) {
            if (ta != null)
                ta.rollback();

            log.error("Couldn't execute " + sqlFileOnClasspath, ex);
        } finally {
            if (bReader != null) {
                try {
                    bReader.close();
                } catch (IOException ex) {
                }
            }
            sess.close();
        }

        return sqlCmd;
    }
}
