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

import com.google.inject.Guice;
import com.google.inject.Injector;
import de.jetwick.config.DefaultModule;
import org.hibernate.cfg.Configuration;

/**
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class Migrate {

    public static void main(String[] args) {
        start();
    }

    public static void start() {
        new DefaultModule().installDbPasswords();
        HibernateUtil.setConfiguration(createConfig());
        HibernateUtil.recreateSchemaFromMapping();
        System.out.println("\n##################################################################\n"
                + "DO NOT forget to fill in some more keywords other than the default!");
    }

    public static Configuration createConfig() {
        Configuration config = HibernateUtil.getConfiguration();
        return config.setProperty("hibernate.connection.driver_class", "org.h2.Driver").
                setProperty("hibernate.connection.url", "jdbc:h2:~/.jetwick/migrate").                
                setProperty("hibernate.default_schema", "PUBLIC").
                setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect").
                setProperty("hibernate.current_session_context_class", "thread").//vs. managed

                setProperty("hibernate.show_sql", "true").
                setProperty("hibernate.format_sql", "true").//

                setProperty("hibernate.hbm2ddl.auto", "create-drop");
//         return config.setProperty("hibernate.connection.driver_class", "org.hsqldb.jdbcDriver").
//                setProperty("hibernate.connection.url", "jdbc:hsqldb:${user.home}/.jetwick/migrate").
//                setProperty("hibernate.connection.username", user).
//                setProperty("hibernate.connection.password", pw).
//                setProperty("hibernate.default_schema", "PUBLIC").
//                setProperty("hibernate.dialect", "org.hibernate.dialect.HSQLDialect").
//                setProperty("hibernate.current_session_context_class", "thread").//managed
//                setProperty("hibernate.show_sql", "true").
//                setProperty("hibernate.format_sql", "true").
//                // the following is only necessary for file databases
//                setProperty("hibernate.hbm2ddl.auto", "create-drop");
    }
}
