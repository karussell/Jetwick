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

import java.util.Date;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;
import de.jetwick.data.JTag;
import de.jetwick.util.MyDate;
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
        // index shouldn't be empty for the first query in queueObject!
        tagSearch.store(new JTag("tmp"), true);

        JTag tag = new JTag("java");
        assertEquals("java", tag.getTerm());
        assertEquals(0, tag.getRequestCount());
        tagSearch.queueObject(tag, true);
        assertTrue(tagSearch.forceCleanTagQueueAndRefresh());
        assertEquals(1, tagSearch.findByTerm("java").getRequestCount());
        assertEquals(2, tagSearch.countAll());

        tag = new JTag("java");
        assertEquals(0, tag.getRequestCount());
        tagSearch.queueObject(tag, true);
        assertTrue(tagSearch.forceCleanTagQueueAndRefresh());
        assertEquals(2, tagSearch.countAll());
        assertEquals(2, tagSearch.findByTerm("java").getRequestCount());

        tag = new JTag("java", "peter");
        assertEquals("java", tag.getTerm());
        assertEquals("peter", tag.getUser());
        assertEquals(0, tag.getRequestCount());
        tagSearch.queueObject(tag, true);
        assertTrue(tagSearch.forceCleanTagQueueAndRefresh());
        assertEquals(1, tagSearch.findByTermAndUser("java", "peter").getRequestCount());
        assertEquals(2, tagSearch.findByTerm("java").getRequestCount());
        assertEquals(3, tagSearch.countAll());

        tag = tagSearch.findByTermAndUser("java", "peter");
        assertEquals("java", tag.getTerm());
        assertEquals("peter", tag.getUser());

        tag = tagSearch.findByTermAndUser("java", "peter");
        tagSearch.queueObject(tag);
        assertTrue(tagSearch.forceCleanTagQueueAndRefresh());
        tag = tagSearch.findByTermAndUser("java", "peter");
        assertEquals("java", tag.getTerm());
        assertEquals("peter", tag.getUser());

        tagSearch.store(tag, true);
        tag = tagSearch.findByTermAndUser("java", "peter");
        assertEquals("java", tag.getTerm());
        assertEquals("peter", tag.getUser());
    }

    @Test
    public void testSave() {
        tagSearch.store(new JTag("Test"), false);
        tagSearch.store(new JTag("#Test"), false);
        tagSearch.store(new JTag("algorithm -google"), true);
        assertEquals("test", tagSearch.findByTerm("tesT").getTerm());
        assertEquals("#test", tagSearch.findByTerm("#test").getTerm());
        assertEquals("algorithm -google", tagSearch.findByTerm("algorithm -google").getTerm());

        assertEquals(3, tagSearch.findSorted(0, 1000).size());
    }

    @Test
    public void testAddAll() throws IOException {
        tagSearch.addAll(Arrays.asList("test", "pest"), true, false);
        assertEquals(2, tagSearch.findSorted(0, 100).size());
        assertEquals("test", tagSearch.findByTerm("tesT").getTerm());
        assertEquals("pest", tagSearch.findByTerm("Pest").getTerm());
    }

    @Test
    public void testFindLowFrequent() {
        JTag tag = new JTag("java").setTweetsPerSec(0.6);
        JTag tag2 = new JTag("test").setTweetsPerSec(0.5);
        tagSearch.queueObject(tag);
        tagSearch.queueObject(tag2);
        assertTrue(tagSearch.forceCleanTagQueueAndRefresh());
        assertEquals(1, tagSearch.findLowFrequent(0, 10, 0.5).size());
        assertEquals(2, tagSearch.findLowFrequent(0, 10, 1).size());
    }

    @Test
    public void testSplitOROperator() {
        JTag tag = new JTag("java OR java OR people");
        tagSearch.queueObject(tag);
        assertTrue(tagSearch.forceCleanTagQueueAndRefresh());
        assertEquals(2, tagSearch.findAll(0, 10).size());
    }

    @Test
    public void testDeleteUntil() {
        JTag tag = new JTag("java OR java OR people").setLastRequest(new MyDate().minusHours(30).toDate());
        tagSearch.queueObject(tag, false);
        assertTrue(tagSearch.forceCleanTagQueueAndRefresh());
        tag = new JTag("people").setLastRequest(new Date());
        tagSearch.queueObject(tag, false);
        assertTrue(tagSearch.forceCleanTagQueueAndRefresh());
        assertEquals(2, tagSearch.findAll(0, 10).size());

//        for (JTag tmp : tagSearch.findAll(0, 100)) {
//            System.out.println(tmp + " " + tmp.getLastRequest());
//        }
        tagSearch.deleteOlderThan(24);
        assertTrue(tagSearch.forceCleanTagQueueAndRefresh());
        assertEquals(1, tagSearch.findAll(0, 10).size());
    }
}
