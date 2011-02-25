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

package de.jetwick.es;

import java.io.File;
//import org.apache.solr.core.SolrConfig;
//import org.apache.solr.util.TestHarness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * code borrowed from org.apache.solr.util.AbstractSolrTestCase (solr 1.4.1)
 */
public abstract class MyAbstractSolrTestCase {

//    protected SolrConfig solrConfig;
//    /**
//     * Harness initialized by initTestHarness.
//     *
//     * <p>
//     * For use in test methods as needed.
//     * </p>
//     */
//    protected TestHarness h;
//    /**
//     * The directory used to story the index managed by the TestHarness h
//     */
//    private File dataDir;
//    public Logger log = LoggerFactory.getLogger(MyAbstractSolrTestCase.class);
//
//    public abstract String getSolrHome();
//
//    /**
//     * Subclasses must define this method to return the name of the
//     * schema.xml they wish to use.
//     */
//    public String getSchemaFile() {
//        return getSolrHome() + "/conf/schema.xml";
//    }
//
//    /**
//     * Subclasses must define this method to return the name of the
//     * solrconfig.xml they wish to use.
//     */
//    public String getSolrConfigFile() {
//        return getSolrHome() + "/conf/solrconfig.xml";
//    }
//
//    protected boolean isSkippingDataDir() {
//        return false;
//    }
//
//    /**
//     * Initializes things your test might need
//     *
//     * <ul>
//     * <li>Creates a dataDir in the "java.io.tmpdir"</li>
//     * <li>initializes the TestHarness h using this data directory, and getSchemaPath()</li>     
//     * </ul>
//     */
//    public void setUp() throws Exception {
//        //problematic static method SolrResourceLoader.locateSolrHome:
//        // solrConfig.getResourceLoader().getInstanceDir();
//        System.setProperty("solr.solr.home", getSolrHome());
//
////        log.info("####SETUP_START " + getSolrHome());
//        dataDir = new File(System.getProperty("java.io.tmpdir")
//                + System.getProperty("file.separator")
//                + getClass().getName() + "-" + System.currentTimeMillis());
//        dataDir.mkdirs();
//
//        String configFile = getSolrConfigFile();
//        if (configFile == null)
//            throw new NullPointerException("Config file is necessary!");
//
//        solrConfig = TestHarness.createConfig(getSolrConfigFile());
//        h = new TestHarness(dataDir.getAbsolutePath(), solrConfig, getSchemaFile());
////        log.info("####SETUP_END " + getSolrHome());
//    }
//
//    /**
//     * Shuts down the test harness, and makes the best attempt possible
//     * to delete dataDir
//     */
//    public void tearDown() throws Exception {
////        log.info("####TEARDOWN_START " + getSolrHome());
//        if (h != null)
//            h.close();
//
//        if (isSkippingDataDir())
//            log.error("DataDir will not be removed: " + dataDir.getAbsolutePath());
//        else if (!recurseDelete(dataDir))
//            log.error("Best effort to remove " + dataDir.getAbsolutePath() + " FAILED!");
//    }
//
//    public static boolean recurseDelete(File f) {
//        if (f.isDirectory()) {
//            for (File sub : f.listFiles()) {
//                if (!recurseDelete(sub))
//                    return false;
//            }
//        }
//        return f.delete();
//    }
}
