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

import com.google.inject.Module;
import de.jetwick.config.DefaultModule;
import de.jetwick.es.ElasticTagSearch;
import org.junit.AfterClass;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.JetwickTestClass;
import de.jetwick.data.JTag;
import de.jetwick.es.ElasticTagSearchTest;
import de.jetwick.util.GenericUrlResolver;
import java.util.Arrays;
import java.util.Collection;
import java.util.Queue;
import java.util.Stack;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetProducerViaSearchTest extends JetwickTestClass {

    private ElasticTagSearchTest tagSearchTester = new ElasticTagSearchTest();
    private TweetProducerViaSearch twProd;

    public TweetProducerViaSearchTest() {
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
    public Module createModule() {
        return new DefaultModule() {

            @Override
            public void installSearchModule() {
                // avoid that we need to set up (user/tweet) search
            }

            @Override
            public GenericUrlResolver createGenericUrlResolver() {
                return null;
            }
        };
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        tagSearchTester.setUp();
        twProd = getInstance(TweetProducerViaSearch.class);
        twProd.setTagSearch(tagSearchTester.getSearch());
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        tagSearchTester.tearDown();
    }

    @Test
    public void testUpdateAndInitTag() {
        // make sure that updateTag is in a transaction                
        twProd.updateTag(new JTag("test"), 6);
        tagSearchTester.getSearch().forceCleanTagQueueAndRefresh();
        assertTrue(tagSearchTester.getSearch().findByTerm("test").getQueryInterval() < 10 * JTag.DEFAULT_Q_I);
    }

    @Test
    public void testInitTagsNoException() {
        twProd.updateTag(new JTag("test"), 6);
        ElasticUserSearch uSearch = mock(ElasticUserSearch.class);
        ElasticTagSearch tagSearch = mock(ElasticTagSearch.class);
        when(uSearch.getQueryTerms()).thenReturn(Arrays.asList("test OR pest"));
        when(tagSearch.findSorted(0, 1000)).thenReturn(Arrays.asList(new JTag("solr OR lucene")));
        twProd.setUserSearch(uSearch);
        twProd.setTagSearch(tagSearch);
        Collection<JTag> tags = twProd.initTags();

        assertEquals(3, tags.size());
        String str = "";
        for (JTag tag : tags) {
            str += tag.getTerm();
            twProd.updateTag(tag, 5);
        }
        assertTrue(str.contains("test"));
        assertTrue(str.contains("pest"));
        assertTrue(str.contains("solr"));
        assertTrue(str.contains("lucene"));

        twProd.updateTag(new JTag("anotherone"), 6);
    }

    @Test
    public void testFIFO() {
        Queue q = new LinkedBlockingDeque();
        q.add("test");
        q.add("pest");
        assertEquals("test", q.poll());

        q = new LinkedBlockingQueue();
        q.add("test");
        q.add("pest");
        assertEquals("test", q.poll());

        Stack v = new Stack();
        v.add("test");
        v.add("pest");
        assertEquals("pest", v.pop());
    }
}
