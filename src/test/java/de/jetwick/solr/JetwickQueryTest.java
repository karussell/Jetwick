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
package de.jetwick.solr;

import java.io.IOException;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.xcontent.ExistsFilterBuilder;
import org.elasticsearch.index.query.xcontent.RangeFilterBuilder;
import org.elasticsearch.index.query.xcontent.TermFilterBuilder;
import org.elasticsearch.index.query.xcontent.TermsFilterBuilder;
import org.elasticsearch.index.query.xcontent.XContentFilterBuilder;
import org.junit.Test;
import static org.junit.Assert.*;

import static org.elasticsearch.common.xcontent.XContentFactory.*;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class JetwickQueryTest {

    public JetwickQueryTest() {
    }

    @Test
    public void testSimilarQuery() {
        SimilarQuery q = new SimilarQuery(
                new SolrTweet(1L, "Test test jAva http://blabli", new SolrUser("tmp")), false);

        assertTrue(q.calcTerms().contains("test"));
        assertTrue(q.calcTerms().contains("java"));
        assertFalse("query mustn't contain links or parts of links", q.calcTerms().contains("http"));
        q = new SimilarQuery(new SolrTweet(1L, "RT @user: test", new SolrUser("tmp")), false);
        assertFalse("query mustn't contain user", q.calcTerms().contains("user"));
    }

    @Test
    public void testParse() {
        JetwickQuery q = new TweetQuery("test").addFilterQuery("test", "xy").
                setSort("blie", "desc").addFacetField("coolField");
        TweetQuery newQ = TweetQuery.parseQuery(q.toString());
        assertEquals(q.toString(), newQ.toString());
        assertEquals(q, newQ);
    }

    @Test
    public void testFilterQuery2Builder() throws IOException {
        // how to test???

        XContentFilterBuilder builder = new TweetQuery().filterQuery2Builder("field", "[1 TO 2]");
        assertEquals(1, 1);
        builder = new TweetQuery().filterQuery2Builder("field", "[1 TO Infinity]");
        assertTrue(builder instanceof RangeFilterBuilder);
        assertEquals(c("{'range':{'field':{'from':1,'to':null,'include_lower':true,'include_upper':true}}}"), toString(builder));

        builder = new TweetQuery().filterQuery2Builder("field", "[-Infinity TO Infinity]");
        assertTrue(builder instanceof ExistsFilterBuilder);

        builder = new TweetQuery().filterQuery2Builder("field", "[-Infinity TO 2]");
        assertTrue(builder instanceof RangeFilterBuilder);
        assertEquals(c("{'range':{'field':{'from':null,'to':2,'include_lower':true,'include_upper':true}}}"), toString(builder));

        builder = new TweetQuery().filterQuery2Builder("field", "test");
        assertTrue(builder instanceof TermFilterBuilder);
        assertEquals(c("{'term':{'field':'test'}}"), toString(builder));

        builder = new TweetQuery().filterQuery2Builder("field", "\"test\"");
        assertTrue(builder instanceof TermFilterBuilder);
        assertEquals(c("{'term':{'field':'test'}}"), toString(builder));

        builder = new TweetQuery().filterQuery2Builder("field", "1 OR 2");
        assertTrue(builder instanceof TermsFilterBuilder);
        assertEquals(c("{'terms':{'field':[1,2]}}"), toString(builder));
    }

    public static String c(String str) {
        return str.replaceAll("'", "\"");
    }

    public static String toString(ToXContent content) throws IOException {
        XContentBuilder json = jsonBuilder();
        content.toXContent(json, null); //ToXContent.EMPTY_PARAMS        
        return json.string();
    }
}
