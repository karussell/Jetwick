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

import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
import de.jetwick.tw.Credits;
import de.jetwick.tw.TwitterSearch;
import de.jetwick.tw.queue.TweetPackage;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.wicket.util.tester.FormTester;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import twitter4j.TwitterException;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class HomePageTest extends WicketPagesTestClass {

    String uString;
    String qString;
    BlockingQueue<SolrTweet> tweets;
    List<SolrTweet> returnUserTweets;
    List<SolrTweet> returnSearchTweets;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        reset();
    }

    public void reset() {
        uString = "";
        qString = "";
        tweets = new LinkedBlockingQueue<SolrTweet>();
        SolrUser u = new SolrUser("peter");
        SolrUser u2 = new SolrUser("peter2");
        returnUserTweets = Arrays.asList(new SolrTweet(3L, "java test2", u2), new SolrTweet(4L, "java pest2", u2));
        returnSearchTweets = Arrays.asList(new SolrTweet(1L, "java test", u), new SolrTweet(2L, "java pest", u));
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

        TweetPackage pkg = page.queueTweets(null, "java", null);
        pkg.retrieveTweets(tweets);

        // perform normal searchAndGetUsers        
        assertEquals("#java", qString);
        assertEquals("", uString);
    }

    @Test
    public void testQueueWhenUserSearch() throws InterruptedException {
        HomePage page = getInstance(HomePage.class);

        TweetPackage p = page.queueTweets(null, null, "java");
        p.retrieveTweets(tweets);

        assertEquals("", qString);
        assertEquals("#java", uString);
    }

    @Test
    public void testNoNullPointerExcForInstantSearch() throws InterruptedException {
        HomePage page = getInstance(HomePage.class);

        // query and user are null and hits == 0 => no background thread is created
        page.init(new SolrQuery(), 0, false);
        assertNull(page.getTweetPackage());

        page.doSearch(new SolrQuery(), 0, false, true);
        assertNull(page.getTweetPackage());
        assertEquals("", uString);
        assertEquals("", qString);
    }

    @Test
    public void testWhithNoSolrSearch() throws InterruptedException {
        HomePage page = getInstance(HomePage.class);

        // normal searchAndGetUsers fails but set twitterfallback = false
        page.init(new SolrQuery("java"), 0, true);
        tweets = new LinkedBlockingQueue<SolrTweet>();
        page.getTweetPackage().retrieveTweets(tweets);
        assertEquals("", uString);
        assertEquals("#java", qString);

        // do not trigger background searchAndGetUsers for the same query
        page.doSearch(new SolrQuery("java"), 0, true);
        assertNull(page.getTweetPackage());

        // if only user searchAndGetUsers then set twitterFallback = true
        reset();
        page.doSearch(new SolrQuery().addFilterQuery("user:test"), 0, true);
        assertEquals("#test", uString);
        assertEquals("", qString);
        page.getTweetPackage().retrieveTweets(tweets);

        // if searchAndGetUsers AND user searchAndGetUsers then set twitterFallback = false but trigger backgr. thread
        reset();
        page.doSearch(new SolrQuery("java").addFilterQuery("user:test"), 0, true);
        assertEquals("", uString);
        assertEquals("", qString);
        page.getTweetPackage().retrieveTweets(tweets);
        assertEquals("#test", uString);
        assertEquals("", qString);
    }

    @Override
    protected TwitterSearch createTwitterSearch() {
        return new TwitterSearch() {

            @Override
            public int getRateLimit() {
                return 100;
            }

            @Override
            public boolean init() {
                return true;
            }

            @Override
            public SolrUser getUser() throws TwitterException {
                return new SolrUser("testUser");
            }

            @Override
            public long search(String term, Collection<SolrTweet> result, int tweets, long lastId) throws TwitterException {
                qString = "#" + term;
                result.addAll(returnSearchTweets);
                return lastId;
            }

            @Override
            public Collection<SolrTweet> searchAndGetUsers(String queryStr, Collection<SolrUser> result, int rows, int maxPage) throws TwitterException {
                qString = "#" + queryStr;
                return returnSearchTweets;
            }

            @Override
            public List<SolrTweet> getTweets(SolrUser user, Collection<SolrUser> result, int tweets) throws TwitterException {
                uString = "#" + user.getScreenName();
                return returnUserTweets;
            }
        }.setCredits(new Credits());
    }
}
