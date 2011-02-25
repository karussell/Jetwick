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
package de.jetwick.bot;

import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.solr.JetwickQuery;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
import de.jetwick.tw.TwitterSearch;
import java.util.ArrayList;
import java.util.List;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import twitter4j.Status;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class JetwotTest {

    public JetwotTest() {
    }   
    private List<SolrTweet> todoTweets = new ArrayList<SolrTweet>();
    private List<Long> retweeted = new ArrayList<Long>();
    private Jetwot bot;

    @Before
    public void setUp() throws Exception {
        reset();
    }

    public void reset() {
        retweeted.clear();
        todoTweets.clear();
        bot = new Jetwot() {

            @Override
            public void init() {
                tw4j = new TwitterSearch() {

                    @Override
                    public Status doRetweet(long twitterId) {
                        retweeted.add(twitterId);
                        return null;
                    }
                };

                tweetSearch = new ElasticTweetSearch() {

                    @Override
                    public SearchResponse search(JetwickQuery query) {
                        return null;
                    }

                    @Override
                    public List<SolrTweet> collectTweets(SearchResponse rsp) {
                        return todoTweets;
                    }
                };
            }
        };

    }

    @Test
    public void testMain() {
        todoTweets.add(new SolrTweet(1L, "test too short", new SolrUser("test")).setRt(2));
        todoTweets.add(new SolrTweet(2L, "test this is not too short bercasu we addded a lot of unknown noise words", new SolrUser("test")).setRt(2));

        bot.start(1, 0);

        assertEquals(1, retweeted.size());
        assertTrue(retweeted.contains(2L));
    }

    @Test
    public void testAvoidSimilarRetweets() {
        todoTweets.add(new SolrTweet(1L, "Dear kids, There is NO Santa Claus. Those presents are from your parents. \"With love, WikiLeaks\"", new SolrUser("ihackinjosh")).setRt(5));
        todoTweets.add(new SolrTweet(2L, "Dear Kids, There is no Santa. Those presents are from your parents. Sincerely, Wikileaks. http://lil.as/1Nu (via @sapnabhavnani)", new SolrUser("dearblankplease")).setRt(4));

        bot.start(2, 0);

        assertEquals(1, retweeted.size());
        assertTrue(retweeted.contains(1L));
    }
}
