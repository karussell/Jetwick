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
package de.jetwick.tw;

import org.junit.AfterClass;
import de.jetwick.JetwickTestClass;
import de.jetwick.data.JTag;
import de.jetwick.es.ElasticTagSearchTest;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetProducerViaStreamIntegrationTestClass extends JetwickTestClass {

    private ElasticTagSearchTest tagSearchTester = new ElasticTagSearchTest();
    private TweetProducerViaStream twProd;
    
    public TweetProducerViaStreamIntegrationTestClass() {
    }
    
    @BeforeClass
    public static void beforeClass() {
        ElasticTagSearchTest.beforeClass();
    }

    @AfterClass
    public static void afterClass() {
        ElasticTagSearchTest.afterClass();
    }
    
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        tagSearchTester.setUp();
        twProd = getInstance(TweetProducerViaStream.class);
        twProd.setTagSearch(tagSearchTester.getSearch());
        twProd.setTwitterSearch(getInstance(TwitterSearch.class));
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        tagSearchTester.tearDown();
    }

    @Test
    public void testUpdateAndInitTag() {
        tagSearchTester.getSearch().queueObject(new JTag("java").setTweetsPerSec(0.5));
        tagSearchTester.getSearch().queueObject(new JTag("google").setTweetsPerSec(0.5));
        tagSearchTester.getSearch().forceCleanTagQueueAndRefresh();
        
        twProd.setNewStreamInterval(30 * 1000);
        twProd.run();        
        
        assertTrue(tagSearchTester.getSearch().findByTerm("test").getQueryInterval() < 10 * JTag.DEFAULT_Q_I);
    }
}
