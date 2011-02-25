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

package de.jetwick;

import de.jetwick.es.UserIndexResponseOrderTestClass;
import de.jetwick.tw.TweetCollectorIntegrationTestClass;
import de.jetwick.tw.TwitterSearchIntegrationTestClass;
import de.jetwick.util.MiscIntegrationTestClass;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Put test here if they requires network access or an external db server.
 * They usually take a lot longer then normal unit tests.
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
@RunWith(Suite.class)
@SuiteClasses(value = {TwitterSearchIntegrationTestClass.class,
    TweetCollectorIntegrationTestClass.class,
    UserIndexResponseOrderTestClass.class,
    MiscIntegrationTestClass.class})
public class AllIntegrationTesting {

    @BeforeClass
    public static void beforeClass() {
//        server = new Db4oServer();
//        server.start(false);
    }

    @AfterClass
    public static void afterClass() {
//        server.close();
    }
}
