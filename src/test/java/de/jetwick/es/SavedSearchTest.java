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

import de.jetwick.util.Helper;
import java.util.Date;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class SavedSearchTest {

    public SavedSearchTest() {
    }

    @Test
    public void testQuery() {
        assertEquals("java, user:\"peter\"", new SavedSearch(1,
                new TweetQuery("java").addFilterQuery("user", "\"peter\"")).getName());
        assertEquals("user:\"peter\"", new SavedSearch(1,
                new TweetQuery().addFilterQuery("user", "\"peter\"")).getName());
        assertEquals("java, user:\"peter rich\"", new SavedSearch(1,
                new TweetQuery("java").addFilterQuery("user", "\"peter rich\"")).getName());
        assertEquals("java termin, user:\"peter test\"", new SavedSearch(1,
                new TweetQuery("java termin").addFilterQuery("user", "\"peter test\"")).getName());
        assertEquals("java, -user:peter", new SavedSearch(1,
                new TweetQuery("java").addFilterQuery("-user", "peter")).getName());
    }

    @Test
    public void testSave() {
        TweetQuery q1 = new TweetQuery("java");
        q1.addFilterQuery("user", "peter");
        assertTrue(new SavedSearch(1, q1).toString().contains("q=java&fq=user%3Apeter"));

        assertEquals(q1.getFilterQueries(), JetwickQuery.parseQuery("fq=user%3Apeter").getFilterQueries());

        q1 = new TweetQuery("java");
        q1.addFilterQuery("-user", "peter");
        assertTrue(new SavedSearch(1, q1).toString().contains("q=java&fq=-user%3Apeter"));
        assertEquals(q1.getFilterQueries(), JetwickQuery.parseQuery("fq=-user%3Apeter").getFilterQueries());
    }

    @Test
    public void testGetQueryWithoutDateFilter() {
        assertEquals(0, new SavedSearch(1,
                new TweetQuery().addFilterQuery("dt", "[1 TO 2]")).getCleanQuery().getFilterQueries().size());
        assertEquals(1, new SavedSearch(1,
                new TweetQuery().addFilterQuery("dt", "[1 TO 2]").
                addFilterQuery("xy", "ab")).getCleanQuery().getFilterQueries().size());
    }

    @Test
    public void testAddFacet() {
        assertEndsWith("*:*", new SavedSearch(1, new TweetQuery()).calcFacetQuery());
        assertEndsWith("*:*", new SavedSearch(1, new TweetQuery("")).calcFacetQuery());
        assertEndsWith("peter", new SavedSearch(1, new TweetQuery("peter ")).calcFacetQuery());
        assertEndsWith("peter pan", new SavedSearch(1, new TweetQuery("peter pan")).calcFacetQuery());

        assertEndsWith("(peter pan) AND test:x",
                new SavedSearch(1, new TweetQuery("peter pan").addFilterQuery("test", "x")).calcFacetQuery());
        assertEndsWith("(peter pan) AND test:x AND test2:y",
                new SavedSearch(1, new TweetQuery("peter pan").addFilterQuery("test", "x").
                addFilterQuery("test2", "y")).calcFacetQuery());

        assertEquals("solr  lucene", new SavedSearch(1,
                new TweetQuery("solr  lucene")).calcFacetQuery());

        assertEquals("solr OR lucene", new SavedSearch(1,
                new TweetQuery("solr OR lucene")).calcFacetQuery());
    }

    @Test
    public void testLastQueryDate() {
        TweetQuery q = new TweetQuery("wicket");
        SavedSearch ss = new SavedSearch(1, q);
        assertEndsWith("wicket", ss.calcFacetQuery());

        Date date = ss.getLastQueryDate();
        assertNull(date);
        ss.getQuery();
        date = ss.getLastQueryDate();
        assertNotNull(date);
        assertEndsWith("wicket AND dt:["
                + Helper.toLocalDateTime(date) + " TO *]", ss.calcFacetQuery());
    }

    public void assertEndsWith(String start, String str2) {
        assertTrue("expected end:" + start + " but was:" + str2, str2.endsWith(start));
    }
}
