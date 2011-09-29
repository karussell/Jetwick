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
import de.jetwick.data.JTweet;
import java.util.Collection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import de.jetwick.data.JUser;
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
public class TweetProducerViaUsersTest {

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
        return tester.getSearch();
    }

    @Test
    public void testRun() {
        getUserSearch().update(new JUser("test").setTwitterToken("xy"), true, true);

        final TwitterSearch mockedTwitter = new TwitterSearch() {

            @Override
            public int getRateLimit() {
                return 200;
            }

            @Override
            public void getFriends(String userName, AnyExecutor<JUser> executor) {
                executor.execute(new JUser("friend1oftest"));
                executor.execute(new JUser("friend2oftest"));
            }

            @Override
            public long getHomeTimeline(Collection<JTweet> result, int tweets, long lastId) throws TwitterException {
                result.add(new JTweet(1L, "test tweet", new JUser("timetabling")));
                result.add(new JTweet(2L, "cool, this tweet will auto persist", new JUser("test")));
                return 2L;
            }
        };

        TweetProducerViaUsers tweetProducer = new TweetProducerViaUsers() {

            @Override
            protected TwitterSearch createTwitter4J(String twitterToken, String twitterTokenSecret) {
                return mockedTwitter;
            }

            @Override
            protected boolean isValidUser(JUser u) {
                return true;
            }

            @Override
            protected synchronized boolean myWait(float seconds) {
                return true;
            }            
        };
        tweetProducer.setUserSearch(getUserSearch());
        tweetProducer.run(1);
        
        assertEquals(2, tweetProducer.getQueue().size());
        JTweet tw = tweetProducer.getQueue().poll();
        assertEquals("test tweet", tw.getText());
        assertFalse(tw.isPersistent());
        tw = tweetProducer.getQueue().poll();
        
        getUserSearch().refresh();
        assertEquals(2, getUserSearch().findByScreenName("test").getFriends().size());
        assertTrue(getUserSearch().findByScreenName("test").getFriends().contains("friend1oftest"));
    }
}