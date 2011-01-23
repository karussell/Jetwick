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

import de.jetwick.es.TweetESQuery;
import org.apache.solr.client.solrj.SolrQuery;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class TweetQueryTest {

    public TweetQueryTest() {
    }

    @Test
    public void testSimilarQuery() {
        // TODO ES
//        new SearchRequestBuilder();
//        SolrQuery q = new TweetESQuery().createSimilarQuery(
//                new SolrTweet(1L, "Test test jAva http://blabli", new SolrUser("tmp")));
//        
//        assertTrue(q.getQuery().contains("test"));
//        assertTrue(q.getQuery().contains("java"));
//        assertFalse("query mustn't contain links or parts of links", q.getQuery().contains("http"));
//        q = new TweetQuery().createSimilarQuery(new SolrTweet(1L, "RT @user: test", new SolrUser("tmp")));
//        assertFalse("query mustn't contain user", q.getQuery().contains("user"));
    }
}
