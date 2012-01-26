/*
 * Copyright 2011 Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetwick.es;

import java.io.File;
import org.elasticsearch.client.Client;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public abstract class AbstractElasticSearchTester {

    private static ElasticNode node = new ElasticNode();    
//    private Logger logger = LoggerFactory.getLogger(getClass());

    @BeforeClass
    public static void beforeClass() {
        File file = new File("/tmp/es");
        file.delete();
        file.mkdir();
        node.start(file.getAbsolutePath(), "es/config", true);        
//        node.waitForYellow();
//        node.waitForOneActiveShard();
    }

    @AfterClass
    public static void afterClass() {
        node.stop();
    }

    public Client getClient() {
        return node.client();
    }

    public void setUp(AbstractElasticSearch search) {
        if(!node.isStarted())
            throw new IllegalStateException("You'll need to call beforeClass and afterClass before using this tester!");
                      
        search.saveCreateIndex(search.getIndexName(), false);

        // start with a fresh index:
        search.deleteAll();  
        
        // optimize to delete remaining deleted articles which can disturb scoring!
        search.optimize();
    }

    public void tearDown() throws Exception {
    }
}
