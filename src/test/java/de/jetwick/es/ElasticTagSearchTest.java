/*
 *  Copyright 2010 Peter Karich jetwick_@_pannous_._info
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetwick.es;

import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;
import de.jetwick.data.JTag;
import org.junit.Before;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class ElasticTagSearchTest extends AbstractElasticSearchTester {

//    private Logger logger = LoggerFactory.getLogger(getClass());
    private static ElasticTagSearch tagSearch;

    public ElasticTagSearch getSearch() {
        return tagSearch;
    }

    @Before
    public void setUp() throws Exception {
        tagSearch = new ElasticTagSearch(getClient());
        super.setUp(tagSearch);
    }


    @Test
    public void testUpdateWithInc() {
        // index shouldn't be empty for the first query in queueTag!
        tagSearch.store(new JTag("tmp"), true);
        
        JTag tag = new JTag("java");
        assertEquals(0, tag.getRequestCount());
        tagSearch.queueTag(tag);
        assertTrue(tagSearch.forceCleanTagQueueAndRefresh());
        assertEquals(1, tagSearch.findByName("java").getRequestCount());
        assertEquals(2, tagSearch.countAll());

        tag = new JTag("java");
        assertEquals(0, tag.getRequestCount());        
        tagSearch.queueTag(tag);
        assertTrue(tagSearch.forceCleanTagQueueAndRefresh());
        assertEquals(2, tagSearch.countAll());
        assertEquals(2, tagSearch.findByName("java").getRequestCount());

        tag = new JTag("java", "peter");
        assertEquals(0, tag.getRequestCount());        
        tagSearch.queueTag(tag);
        assertTrue(tagSearch.forceCleanTagQueueAndRefresh());
        assertEquals(1, tagSearch.findByNameAndUser("java", "peter").getRequestCount());
        assertEquals(2, tagSearch.findByName("java").getRequestCount());
        assertEquals(3, tagSearch.countAll());
    }

    @Test
    public void testSave() {
        tagSearch.store(new JTag("Test"));
        tagSearch.store(new JTag("#Test"));
        tagSearch.store(new JTag("algorithm -google"), true);
        assertEquals("test", tagSearch.findByName("tesT").getTerm());
        assertEquals("#test", tagSearch.findByName("#test").getTerm());
        assertEquals("algorithm -google", tagSearch.findByName("algorithm -google").getTerm());
        
        assertEquals(3, tagSearch.findSorted(0, 1000).size());
    }    
    
    @Test
    public void testAddAll() throws IOException {
        tagSearch.addAll(Arrays.asList("test", "pest"), true, false);
        assertEquals(2, tagSearch.findSorted(0, 100).size());
        assertEquals("test", tagSearch.findByName("tesT").getTerm());
        assertEquals("pest", tagSearch.findByName("Pest").getTerm());
    }
}
