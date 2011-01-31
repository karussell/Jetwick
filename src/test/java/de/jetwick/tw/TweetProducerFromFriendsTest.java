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
package de.jetwick.tw;

import de.jetwick.es.ElasticUserSearch;
import de.jetwick.solr.SolrTweet;
import java.util.Collection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import de.jetwick.solr.SolrUser;
import de.jetwick.util.AnyExecutor;
import org.junit.Before;
import de.jetwick.es.ElasticUserSearchTest;
import org.junit.Test;
import static org.junit.Assert.*;
import twitter4j.TwitterException;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetProducerFromFriendsTest {

    ElasticUserSearchTest tester = new ElasticUserSearchTest();

    @BeforeClass
    public static void beforeClass() throws Exception {
        ElasticUserSearchTest.beforeClass();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        ElasticUserSearchTest.afterClass();
    }

    @Before
    public void setUp() throws Exception {
        tester.setUp();
    }

    public void tearDown() throws Exception {
        tester.tearDown();
    }

    private ElasticUserSearch getUserSearch() {
        return tester.getUserSearch();
    }

    @Test
    public void testRun() {
        getUserSearch().update(new SolrUser("test"), true, true);

        final TwitterSearch mockedTwitter = new TwitterSearch() {

            @Override
            public void getFriends(String userName, AnyExecutor<SolrUser> executor) {
                executor.execute(new SolrUser("friend1OfTest"));
                executor.execute(new SolrUser("friend2OfTest"));
            }

            @Override
            public long getHomeTimeline(Collection<SolrTweet> result, int tweets, long lastId) throws TwitterException {
                result.add(new SolrTweet(1L, "test tweet", new SolrUser("timetabling")));
                return 2L;
            }
        };

        TweetProducerFromFriends producer = new TweetProducerFromFriends() {

            @Override
            protected TwitterSearch createTwitter4J(String twitterToken, String twitterTokenSecret) {
                return mockedTwitter;
            }

            @Override
            protected boolean isValidUser(SolrUser u) {
                return true;
            }            
        };
        producer.setUserSearch(getUserSearch());
        producer.run(1);

        assertEquals(1, producer.getQueue().size());
        assertEquals("test tweet", producer.getQueue().poll().getTweets().iterator().next().getText());
        
        assertEquals(2, getUserSearch().findByScreenName("test").getFriends().size());
        assertTrue(getUserSearch().findByScreenName("test").getFriends().contains("friend1oftest"));
    }
}