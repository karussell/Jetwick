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

import com.google.inject.Inject;
import de.jetwick.JetwickTestClass;
import de.jetwick.data.YTag;
import de.jetwick.data.YUser;
import de.jetwick.solr.SolrTweet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;

import org.junit.Test;
import twitter4j.Tweet;
import static org.junit.Assert.*;
import twitter4j.TwitterException;

/**
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TwitterSearchIntegrationTestClass extends JetwickTestClass {

    @Inject
    private TwitterSearch twitterSearch;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        twitterSearch.init();
    }

    @Override
    public Object getInjectObject() {
        return this;
    }

    @Test
    public void testUserUpdate() {
        TwitterSearch st = twitterSearch;
        List<YUser> users = new ArrayList<YUser>();
        YUser user1 = new YUser("pannous");
        users.add(user1);
        YUser user2 = new YUser("timetabling");
        users.add(user2);

        st.updateUserInfo(users);
        assertNotNull(user2.getDescription());
        assertTrue(user2.getDescription().trim().length() > 0);
    }

    @Test
    public void testGetTweetWithGeolocation() throws TwitterException {
//        Status st = twitterSearch.getTweet(18845491030L);
//        System.out.println("geo:" + st.getGeoLocation());
    }

    @Test
    public void getHomeTimeline() throws TwitterException {
        assertEquals(30, twitterSearch.getHomeTimeline(30).size());
    }

    @Test
    public void statsOfAUser() throws TwitterException {
        TwitterSearch st = twitterSearch;
        List<? extends Tweet> tweets = st.getTweets("timetabling", 20);
        List<SolrTweet> newTweets = new ArrayList();
        for (Tweet tw : tweets) {
            SolrTweet tw2 = new SolrTweet(tw);
            newTweets.add(tw2);
//            System.out.println(tw.getText() + " " + tw2.getInReplyTwitterId());
        }
//        TweetExtractor e = new TweetExtractor(newTweets).setTermMaxCount(100);
//        System.out.println(e.run().getTags());
//
//        for (Entry<String, Integer> lang : e.getLanguages().entrySet()) {
//            System.out.println(lang);
//        }
    }

    @Test
    public void testSearch() throws TwitterException {
        TwitterSearch st = twitterSearch;
        Set<Tweet> resList = new LinkedHashSet<Tweet>();
        YTag tag = new YTag("java");
        st.search(tag.getTerm(), resList, 200, tag.getLastId());
        assertTrue(resList.size() > 190);

        Set<Long> ids = new LinkedHashSet<Long>();
        for (Tweet tw : resList) {
            ids.add(tw.getId());
        }

        System.out.println("size:" + ids.size());
        assertTrue(ids.size() > 190);

        List<Tweet> other = new ArrayList<Tweet>();
        for (Tweet tw : resList) {
            if (!ids.remove(tw.getId()))
                other.add(tw);
        }

        System.out.println("size:" + other.size());
        assertTrue(other.size() < 10);

        resList.clear();
        // searchAndGetUsers with the saved sinceId
        st.search(tag.getTerm(), resList, 200, tag.getLastId());
        assertTrue(resList.size() > 0);
    }

    @Test
    public void testTrend() {
        TwitterSearch st = twitterSearch;
        assertTrue(st.getTrends().size() > 0);
    }
}
