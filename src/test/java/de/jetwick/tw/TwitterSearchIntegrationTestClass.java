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
import com.google.inject.Module;
import de.jetwick.JetwickTestClass;
import de.jetwick.config.Configuration;
import de.jetwick.config.DefaultModule;
import de.jetwick.data.UrlEntry;
import de.jetwick.data.JTag;
import de.jetwick.es.ElasticTweetSearchTest;
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
import de.jetwick.es.TweetQuery;
import de.jetwick.tw.cmd.TermCreateCommand;
import de.jetwick.util.AnyExecutor;
import de.jetwick.util.GenericUrlResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

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
    private static ElasticTweetSearchTest twSearchTester = new ElasticTweetSearchTest();

    @BeforeClass
    public static void beforeClass() {
        twSearchTester.beforeClass();
    }

    @AfterClass
    public static void afterClass() {
        twSearchTester.afterClass();
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        twSearchTester.setUp();
        Credits c = new Configuration().getTwitterSearchCredits();
        twitterSearch.initTwitter4JInstance(c.getToken(), c.getTokenSecret(), true);
    }

    @Override
    public Object getInjectObject() {
        return this;
    }

    @Test
    public void testUserUpdate() {
        TwitterSearch st = twitterSearch;
        List<JUser> users = new ArrayList<JUser>();
        JUser user1 = new JUser("pannous");
        users.add(user1);
        JUser user2 = new JUser("timetabling");
        users.add(user2);

        st.updateUserInfo(users);
        assertNotNull(user2.getDescription());
        assertTrue(user2.getDescription().trim().length() > 0);
    }

    @Test
    public void testNormalAccountAgainstSpam() throws TwitterException {
        List<JTweet> list = new ArrayList<JTweet>();
        list = twitterSearch.getTweets(new JUser("berniecezee2573"), 100);
        for (JTweet tw : list) {
            for (UrlEntry entry : new FakeUrlExtractor().setText(tw.getText()).run().getUrlEntries()) {
                tw.addUrlEntry(entry);
            }
            JTweet tw2 = new TermCreateCommand().execute(tw);
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
    public void testGetFriends() throws TwitterException {
        final Collection<JUser> coll = new ArrayList<JUser>();
        twitterSearch.getFriends("wiedumir", new AnyExecutor<JUser>() {

            @Override
            public JUser execute(JUser u) {
                coll.add(u);
                return u;
            }
        });

        System.out.println("follower:" + coll.size());
        assertTrue(coll.size() > 5);
    }

    @Test
    public void getHomeTimeline() throws TwitterException {
        // damn twitter uncertainties
        int size = twitterSearch.getHomeTimeline(30).size();
//        System.out.println("get 30 homeline tweets:" + size);
        assertTrue(size >= 25);

        BlockingQueue<JTweet> coll = new LinkedBlockingQueue<JTweet>();
        twitterSearch.getHomeTimeline(coll, 10, 0);
        for (JTweet tw : coll) {
            assertNotNull(tw.getFromUser().getProfileImageUrl());
        }
    }

    @Test
    public void statsOfAUser() throws TwitterException {
        TwitterSearch st = twitterSearch;
        List<JTweet> tweets = st.getTweets(new JUser("timetabling"), 20);
        List<JTweet> newTweets = new ArrayList();
        for (JTweet tw : tweets) {
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
        Set<JTweet> resList = new LinkedHashSet<JTweet>();
        JTag tag = new JTag("java");
        st.search(tag.getTerm(), resList, 200, tag.getMaxCreateTime());
        for (JTweet tw : resList) {
            assertNotNull(tw.getFromUser().getProfileImageUrl());
        }
        assertTrue(resList.size() > 190);

        Set<Long> ids = new LinkedHashSet<Long>();
        for (JTweet tw : resList) {
            ids.add(tw.getTwitterId());
        }

//        System.out.println("size:" + ids.size());
        assertTrue(ids.size() > 190);

        List<JTweet> other = new ArrayList<JTweet>();
        for (JTweet tw : resList) {
            if (!ids.remove(tw.getTwitterId()))
                other.add(tw);
        }

//        System.out.println("size:" + other.size());
        assertTrue(other.size() < 10);

        resList.clear();
        // searchAndGetUsers with the saved sinceId
        st.search(tag.getTerm(), resList, 200, tag.getMaxCreateTime());
        assertTrue(resList.size() > 0);
    }

    @Test
    public void testTrend() {
        TwitterSearch st = twitterSearch;
        assertTrue(st.getTrends().size() > 0);
    }

    @Test
    public void testFriendSearch() {
//        FriendSearchHelper helper = new FriendSearchHelper() {
//
//            @Override
//            public void updateUser(SolrUser user) {                
//            }
//
//            @Override
//            public SolrUser getUser(String screenName) {
//                return new SolrUser(screenName);
//            }
//            
//        };
//        helper.setTwitter4j(twitterSearch);
//        Collection<String> f = helper.getFriendsOf("ibood");
//        assertTrue(f.size() > 10);         
//        System.out.println("Friends:" + f.size() + " " + f);
        Collection<String> f = new ArrayList<String>();
        for (int i = 0; i < 50000; i++) {
            f.add("user" + i);
        }

        TweetQuery q = new TweetQuery("").createFriendsQuery(f);

        // create tweet to map some indirectly mapped (not defined) fields like dt
        twSearchTester.getSearch().store(new JTweet(1L, "test", new JUser("user")), true);

        // should not throw an exception
        twSearchTester.getSearch().query(q);
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
}
