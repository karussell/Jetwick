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
import de.jetwick.config.Configuration;
import de.jetwick.data.UrlEntry;
import de.jetwick.data.YTag;
import de.jetwick.data.YUser;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
import de.jetwick.tw.cmd.TermCreateCommand;
import de.jetwick.util.AnyExecutor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.Before;

import org.junit.Test;
import twitter4j.Status;
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
        Credits c = new Configuration().getTwitterSearchCredits();
        twitterSearch.setTwitter4JInstance(c.getToken(), c.getTokenSecret());
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
    public void testNormalAccountAgainstSpam() throws TwitterException {
        List<SolrTweet> list = new ArrayList<SolrTweet>();
        list = twitterSearch.getTweets(new SolrUser("berniecezee2573"), 100);
        for (SolrTweet tw : list) {
            for (UrlEntry entry : new FakeUrlExtractor().setText(tw.getText()).run().getUrlEntries()) {
                tw.addUrlEntry(entry);
            }
            SolrTweet tw2 = new TermCreateCommand().execute(tw);
            System.out.println(tw2.getQuality() + " " + tw2.getQualDebug() + " " + tw2.getText());
        }
    }

    @Test
    public void testGetTweetWithGeolocation() throws TwitterException {
        Status st = twitterSearch.getTweet(18845491030L);
        assertNotNull(st.getGeoLocation());
//        System.out.println("geo:" + st.getGeoLocation());
    }

    @Test
    public void getFollowers() throws TwitterException {
        final Collection<SolrUser> coll = new ArrayList<SolrUser>();
        twitterSearch.getFriends("wiedumir", new AnyExecutor<SolrUser>() {

            @Override
            public SolrUser execute(SolrUser u) {
                coll.add(u);
                return u;
            }
        });
        
        assertTrue(coll.size() > 5);
    }

    @Test
    public void getHomeTimeline() throws TwitterException {
        // damn twitter uncertainties
        int size = twitterSearch.getHomeTimeline(30).size();
//        System.out.println("get 30 homeline tweets:" + size);
        assertTrue(size >= 25);

        BlockingQueue<SolrTweet> coll = new LinkedBlockingQueue<SolrTweet>();
        twitterSearch.getHomeTimeline(coll, 10, 0);
        for (SolrTweet tw : coll) {
            assertNotNull(tw.getFromUser().getProfileImageUrl());
        }
    }

    @Test
    public void statsOfAUser() throws TwitterException {
        TwitterSearch st = twitterSearch;
        List<SolrTweet> tweets = st.getTweets(new SolrUser("timetabling"), 20);
        List<SolrTweet> newTweets = new ArrayList();
        for (SolrTweet tw : tweets) {
            assertNotNull(tw.getFromUser());
            assertNotNull(tw.getFromUser().getProfileImageUrl());
            newTweets.add(tw);
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
        Set<SolrTweet> resList = new LinkedHashSet<SolrTweet>();
        YTag tag = new YTag("java");
        st.search(tag.getTerm(), resList, 200, tag.getLastId());
        for (SolrTweet tw : resList) {
            assertNotNull(tw.getFromUser().getProfileImageUrl());
        }
        assertTrue(resList.size() > 190);

        Set<Long> ids = new LinkedHashSet<Long>();
        for (SolrTweet tw : resList) {
            ids.add(tw.getTwitterId());
        }

//        System.out.println("size:" + ids.size());
        assertTrue(ids.size() > 190);

        List<SolrTweet> other = new ArrayList<SolrTweet>();
        for (SolrTweet tw : resList) {
            if (!ids.remove(tw.getTwitterId()))
                other.add(tw);
        }

//        System.out.println("size:" + other.size());
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
