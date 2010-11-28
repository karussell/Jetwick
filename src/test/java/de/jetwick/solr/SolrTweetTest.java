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

package de.jetwick.solr;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SolrTweetTest {

    @Test
    public void testIsRetweet() {
        assertTrue(createTweet(1, "RT @testuser").isRetweet());
        assertFalse(createTweet(1, "asseRT").isRetweet());

        SolrUser usera = new SolrUser("usera");
        assertFalse(createTweet(2L, "rt @usera: bla text").isRetweetOf(createTweet(1, "text", usera)));
        assertTrue(createTweet(2L, "bla rt @usera: text").isRetweetOf(createTweet(1, "text", usera)));
        assertTrue(createTweet(2L, "rt @usera: text bla").isRetweetOf(createTweet(1, "text", usera)));
        assertTrue(createTweet(2L, "blap RT @usera text").isRetweetOf(createTweet(1, "text", usera)));
        // fails with regex
        assertTrue(createTweet(2L, "blap RT @usera: text (bla ...").isRetweetOf(createTweet(1, "text (bla ...", usera)));
        assertTrue(createTweet(2L, "blap RT @usera: text ? * test.html").isRetweetOf(createTweet(1, "text ? * test.html", usera)));
    }

    @Test
    public void testAutoReverse() {
        // replies <-> inReplyOf
        SolrTweet tw1 = createTweet(1L, "test1");
        SolrTweet tw2 = createTweet(2L, "test2");
        tw1.addReply(tw2);
        assertEquals(tw1, tw2.getInReplyOf());
        assertEquals(1L, tw2.getInReplyTwitterId());

        // fromUser <-> ownTweets
        SolrUser user = new SolrUser("peter");
        user.addOwnTweet(tw1);
        assertEquals(user, tw1.getFromUser());

        user = new SolrUser("peter");
        tw1 = createTweet(1L, "test1");
        tw1.setFromUser(user);
        assertEquals(tw1, user.getOwnTweets().iterator().next());
    }

    @Test
    public void testRemoveDuplicatesAndSort() {
        // assert sort id and non duplicate id
        List<SolrTweet> tweets = new ArrayList<SolrTweet>();
        tweets.add(createTweet(1L, "test1"));
        tweets.add(createTweet(5L, "test2"));
        tweets.add(createTweet(2L, "test3"));
        tweets.add(createTweet(1L, "test4"));
        SolrTweet.sortAndDeduplicate(tweets);
        assertEquals(3, tweets.size());
        assertEquals(5L, (long) tweets.get(0).getTwitterId());
        assertEquals(2L, (long) tweets.get(1).getTwitterId());
        assertEquals(1L, (long) tweets.get(2).getTwitterId());

        // assert non-duplicate text
        tweets.clear();
        tweets.add(createTweet(1L, "test1"));
        tweets.add(createTweet(2L, "test1"));
        SolrTweet.sortAndDeduplicate(tweets);
        assertEquals(1, tweets.size());
        assertEquals(2L, (long) tweets.get(0).getTwitterId());

        // assert non-duplicate ids and text
        tweets.clear();
        tweets.add(createTweet(1L, "test1"));
        tweets.add(createTweet(5L, "test2"));
        tweets.add(createTweet(3L, "test4"));
        tweets.add(createTweet(11L, "test5"));
        tweets.add(createTweet(10L, "test4"));
        SolrTweet.sortAndDeduplicate(tweets);
        // do not remove the text if there is a tweet in-between (here 5L)
        assertEquals(5, tweets.size());
        tweets.clear();
        tweets.add(createTweet(1L, "test1"));
        tweets.add(createTweet(5L, "test2"));
        tweets.add(createTweet(11L, "test3"));
        tweets.add(createTweet(9L, "test2"));
        tweets.add(createTweet(10L, "test2"));
        SolrTweet.sortAndDeduplicate(tweets);
        assertEquals(3, tweets.size());
        assertEquals(11L, (long) tweets.get(0).getTwitterId());
        assertEquals(10L, (long) tweets.get(1).getTwitterId());
        assertEquals(1L, (long) tweets.get(2).getTwitterId());
    }

    @Test
    public void testExtractText() {
        assertEquals("text", createTweet(1L, "RT @user: text").extractRTText());
        assertEquals("text", createTweet(1L, "RT @user text").extractRTText());
        assertEquals("", createTweet(1L, "RT text").extractRTText());
        assertEquals("", createTweet(1L, "@user text").extractRTText());

        assertEquals("text", createTweet(1L, "rt @user: text").extractRTText());
    }

    SolrTweet createTweet(long id, String text) {
        return new SolrTweet(id, text, new SolrUser("tmp"));
    }
    SolrTweet createTweet(long id, String text, SolrUser user) {
        return new SolrTweet(id, text, user);
    }
}
