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

package de.jetwick.ui;

import com.google.inject.Provider;
import de.jetwick.config.Configuration;
import de.jetwick.data.YUser;
import de.jetwick.rmi.RMIClient;
import de.jetwick.solr.SolrUser;
import de.jetwick.tw.Credits;
import de.jetwick.tw.Twitter4JTweet;
import de.jetwick.tw.TwitterSearch;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.wicket.util.tester.FormTester;
import org.junit.Before;
import org.junit.Test;
import twitter4j.Tweet;

import static org.junit.Assert.*;
import twitter4j.TwitterException;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class HomePageTest extends WicketPagesTestClass {

    String uString;
    String qString;
    Collection<? extends Tweet> tweets;
    List<? extends Tweet> returnUserTweets;
    List<? extends Tweet> returnSearchTweets;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        reset();
    }

    public void reset() {
        uString = "";
        qString = "";
        tweets = null;
        returnUserTweets = Arrays.asList(new Twitter4JTweet(3L, "java test2", "peter2"), new Twitter4JTweet(4L, "java pest2", "peter2"));
        returnSearchTweets = Arrays.asList(new Twitter4JTweet(1L, "java test", "peter"), new Twitter4JTweet(2L, "java pest", "peter"));
    }

    @Test
    public void testSelectAndRemove() {
        tester.startPage(HomePage.class);
        tester.assertNoErrorMessage();

        FormTester formTester = tester.newFormTester("searchbox:searchform");
        formTester.setValue("textField", "java");
        formTester.submit();
        tester.assertNoErrorMessage();

        tester.clickLink("searchbox:searchform:homelink");
        tester.assertNoErrorMessage();

        tester.startPage(new HomePage(new SolrQuery("timetabling"), 0, false));
        tester.assertNoErrorMessage();
    }

    @Test
    public void testQueueWhenNoResults() throws InterruptedException {
        HomePage page = getInstance(HomePage.class);
        page.setRMIClient(createAssertRMIClient());
        page.setTwitterSearch(createAssertTwitter());

        Thread t = page.queueTweets(null, "java", null);
        t.start();
        t.join();

        // perform normal searchAndGetUsers
        assertNotNull(tweets);
        assertEquals("#java", qString);
        assertEquals("", uString);
    }

    @Test
    public void testQueueWhenUserSearch() throws InterruptedException {
        HomePage page = getInstance(HomePage.class);
        page.setRMIClient(createAssertRMIClient());
        page.setTwitterSearch(createAssertTwitter());

        Thread t = page.queueTweets(null, null, "java");
        t.start();
        t.join();

        assertNotNull(tweets);
        assertEquals("", qString);
        assertEquals("#java", uString);
    }

    @Test
    public void testNoNullPointerExcForInstantSearch() throws InterruptedException {
        HomePage page = getInstance(HomePage.class);
        page.setRMIClient(createAssertRMIClient());
        page.setTwitterSearch(createAssertTwitter());

        // query and user are null and hits == 0 => no background thread is created
        page.init(new SolrQuery(), 0, false);
        assertNull(page.getBackgroundThread());

        page.doSearch(new SolrQuery(), 0, false, true);
        assertNull(page.getBackgroundThread());
        assertNull(tweets);
        assertEquals("", uString);
        assertEquals("", qString);
    }

    @Test
    public void testWhithNoSolrSearch() throws InterruptedException {
        HomePage page = getInstance(HomePage.class);
        page.setRMIClient(createAssertRMIClient());
        page.setTwitterSearch(createAssertTwitter());

        // normal searchAndGetUsers fails but set twitterfallback = false
        page.init(new SolrQuery("java"), 0, true);
        page.getBackgroundThread().join();
        assertNotNull(tweets);
        assertEquals("", uString);
        assertEquals("#java", qString);

        // do not trigger background searchAndGetUsers for the same query
        reset();
        page.doSearch(new SolrQuery("java"), 0, true);
        assertFalse(page.getBackgroundThread().isAlive());
        assertNull(tweets);

        // if only user searchAndGetUsers then set twitterFallback = true
        reset();
        page.doSearch(new SolrQuery().addFilterQuery("user:test"), 0, true);
        assertEquals("#test", uString);
        assertEquals("", qString);
        assertNull(tweets);
        assertTrue(page.getBackgroundThread().isAlive());
        page.getBackgroundThread().join();
        assertNotNull(tweets);

        // if searchAndGetUsers AND user searchAndGetUsers then set twitterFallback = false but trigger backgr. thread
        reset();
        page.doSearch(new SolrQuery("java").addFilterQuery("user:test"), 0, true);
        assertEquals("", uString);
        assertEquals("", qString);
        assertTrue(page.getBackgroundThread().isAlive());
        page.getBackgroundThread().join();
        assertEquals("#test", uString);
        assertEquals("", qString);
    }

    TwitterSearch createAssertTwitter() {
        return new TwitterSearch(new Credits()) {

            @Override
            public int getRateLimit() {
                return 100;
            }

            @Override
            public boolean init() {
                return true;
            }

            @Override
            public YUser getUser() throws TwitterException {
                return new YUser("testUser");
            }

            @Override
            public Collection<? extends Tweet> searchTweets(String queryStr, int tweets) throws TwitterException {
                qString = "#" + queryStr;
                return returnSearchTweets;
            }

            @Override
            public Collection<? extends Tweet> searchAndGetUsers(String queryStr, Collection<SolrUser> result, int rows, int maxPage) throws TwitterException {
                qString = "#" + queryStr;
                return returnSearchTweets;
            }

            @Override
            public List<? extends Tweet> getTweets(String userScreenName, Collection<SolrUser> result, int tweets) throws TwitterException {
                uString = "#" + userScreenName;
                return returnUserTweets;
            }
        };
    }

    Provider<RMIClient> createAssertRMIClient() {
        return new Provider<RMIClient>() {

            @Override
            public RMIClient get() {
                return new RMIClient(new Configuration()) {

                    @Override
                    public RMIClient init() {
                        return this;
                    }

                    @Override
                    public int send(Collection<? extends Tweet> tweets) throws RemoteException {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ex) {
                        }
                        HomePageTest.this.tweets = tweets;
                        return 2;
                    }
                };
            }
        };
    }
}
