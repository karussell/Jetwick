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
package de.jetwick.solr;

import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public abstract class AbstractMCTestCase {

    protected static String solrWebapp = "/solr";
    protected static int port = 8983;
    private static SolrTestInstance testInstance;

//    @BeforeClass
//    public static void beforeClass() throws Exception {
//        System.setProperty(SolrTestInstance.SOLR_PROP, "/home/peterk/tmp");
//        testInstance = new SolrTestInstance();
//        testInstance.start();
//    }
//
//    @AfterClass
//    public static void afterClass() {
//        testInstance.stop();
//    }

    public void setUp() throws Exception {
        // remove newly created cores or close and clear cores or use cores.setPersistent(false);
        // at the moment we set persistent=false within solr.cml
    }

    public void tearDown() throws Exception {
    }
}
