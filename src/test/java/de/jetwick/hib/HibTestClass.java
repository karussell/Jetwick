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

import com.google.inject.Module;
import com.wideplay.warp.persist.WorkManager;
import de.jetwick.JetwickTestClass;
import de.jetwick.config.DefaultModule;
import de.jetwick.config.HibModule;
import de.jetwick.data.DbTestInterface;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

/**
 * @author Peter Karich
 */
public class HibTestClass extends JetwickTestClass implements DbTestInterface {

    private static SessionFactory sessionFactory;
    private static String TEST_SCHEMA = "PUBLIC";

    static {
        HibernateUtil.setConfiguration(createConfig());
        sessionFactory = HibernateUtil.getSessionFactory();
        Session session = sessionFactory.getCurrentSession();

        // We only need to create the schema for in-memory databases and for newly created databases!
//        session.beginTransaction();
//        String CREATE_SCHEMA = "create schema " + TEST_SCHEMA;
//        if (HibernateUtil.getConfiguration().getProperty("hibernate.connection.driver_class").
//                equals("org.hsqldb.jdbcDriver")) {
//            CREATE_SCHEMA += " AUTHORIZATION DBA";
//        }
//        session.createSQLQuery(CREATE_SCHEMA).executeUpdate();
//        session.getTransaction().commit();
//        session = sessionFactory.getCurrentSession();

        session.beginTransaction();
        HibernateUtil.recreateSchemaFromLiquibase();
//        HibernateUtil.recreateSchemaFromMapping();
        session = HibernateUtil.getSessionFactory().getCurrentSession();
        if (session.isOpen())
            session.close();
    }

    public static Configuration createConfig() {
        Configuration config = HibernateUtil.getConfiguration();
//        return config.setProperty("hibernate.connection.driver_class", "org.hsqldb.jdbcDriver").
//                setProperty("hibernate.connection.url", "jdbc:hsqldb:mem:jetwickdb").
//                setProperty("hibernate.connection.username", "sa").
//                setProperty("hibernate.connection.password", "").
//                setProperty("hibernate.default_schema", TEST_SCHEMA).
//                setProperty("hibernate.dialect", "org.hibernate.dialect.HSQLDialect").
//                setProperty("hibernate.current_session_context_class", "thread").//managed
//                setProperty("hibernate.show_sql", "true").
//                setProperty("hibernate.format_sql", "true");

        return config.setProperty("hibernate.connection.driver_class", "org.h2.Driver").
                //setProperty("hibernate.connection.url", "jdbc:h2:mem:jetwickdb").
                setProperty("hibernate.connection.url", "jdbc:h2:mem:test").
                setProperty("hibernate.connection.username", "sa").
                setProperty("hibernate.connection.password", "").
                setProperty("hibernate.default_schema", TEST_SCHEMA).
                setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect").
                setProperty("hibernate.current_session_context_class", "thread") //managed
                //                setProperty("hibernate.show_sql", "true").
                //                setProperty("hibernate.format_sql", "true")
                ;
    }
    private WorkManager manager;

    @Override
    public void setUp() throws Exception {
        cleanup();
        super.setUp();
        manager = getInstance(WorkManager.class);
        manager.beginWork();
    }

    @Override
    public void tearDown() throws Exception {
        manager.endWork();
    }

    @Override
    public Module createModule() {
        return new DefaultModule() {

            @Override
            public void installDbModule() {
                install(new HibModule(createConfig()));
            }
        };
    }

    protected void cleanup() throws Exception {
        HibernateUtil.recreateSchemaFromLiquibase();
//        HibernateUtil.recreateSchemaFromMapping();
    }

    public void commitAndReopenDB() throws Exception {
        manager.endWork();
        // super.setUp();
        manager.beginWork();
    }
}
