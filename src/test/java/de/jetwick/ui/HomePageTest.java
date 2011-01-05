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

import de.jetwick.config.Configuration;
import de.jetwick.rmi.RMIClient;
import de.jetwick.solr.JetwickQuery;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrTweetSearch;
import de.jetwick.solr.SolrUser;
import de.jetwick.tw.TwitterSearch;
import de.jetwick.tw.queue.QueueThread;
import de.jetwick.tw.queue.TweetPackage;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.wicket.PageParameters;
import org.apache.wicket.util.tester.FormTester;
import org.junit.Before;
import org.junit.Test;
import twitter4j.TwitterException;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class HomePageTest extends WicketPagesTestClass {

    private String uString;
    private String qString;
    private List<SolrTweet> returnUserTweets;
    private List<SolrTweet> returnSearchTweets;
    private TweetPackage sentTweets;
    private SolrTweetSearch ownSolrTweetSearch;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        reset();
    }

    public void reset() {
        sentTweets = null;
        uString = "";
        qString = "";
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
    }

    @Test
    public void testNormalSearch() throws Exception {
        ownSolrTweetSearch = mock(SolrTweetSearch.class);
        setUp();
        SolrQuery query = new SolrQuery("timetabling");
        tester.startPage(new HomePage(query, 0, false));
        tester.assertNoErrorMessage();
        verify(ownSolrTweetSearch).search(new LinkedHashSet<SolrUser>(), query);
    }

    @Test
    public void testQueueWhenNoResults() throws InterruptedException {
        HomePage page = getInstance(HomePage.class);

        QueueThread pkg = page.queueTweets(null, "java", null);
        Thread t = new Thread(pkg);
        t.start();
        t.join();
        assertNotNull(sentTweets);

        // perform normal searchAndGetUsers        
        assertEquals("#java", qString);
        assertEquals("", uString);
    }

    @Test
    public void testQueueWhenUserSearch() throws InterruptedException {
        HomePage page = getInstance(HomePage.class);

        QueueThread p = page.queueTweets(null, null, "java");
        p.run();
        assertNotNull(sentTweets);

        assertEquals("", qString);
        assertEquals("#java", uString);
    }

    @Test
    public void testAvoidDuplicateSearchEnqueueing() throws InterruptedException {
        HomePage page = getInstance(HomePage.class);

        QueueThread p = page.queueTweets(null, null, "java");
        p.run();
        assertNotNull(sentTweets);
        assertEquals("", qString);
        assertEquals("#java", uString);

        reset();
        p = page.queueTweets(null, null, "Java");
        p.run();
        assertNull(sentTweets);
        assertEquals("", qString);
        assertEquals("", uString);

//        reset();
//        page = getInstance(HomePage.class);
//        p = page.queueTweets(null, null, "Java");
//        p.run();
//        assertNull(sentTweets);
//        assertEquals("", qString);
//        assertEquals("", uString);
    }

    @Test
    public void testNoNullPointerExcForInstantSearch() throws InterruptedException {
        HomePage page = getInstance(HomePage.class);

        // query and user are null and hits == 0 => no background thread is created
        page.init(new SolrQuery(), 0, false);
        assertNull(page.getQueueThread());

        page.doSearch(new SolrQuery(), 0, false, true);
        assertNull(page.getQueueThread());
        assertEquals("", uString);
        assertEquals("", qString);
    }

    @Test
    public void testWithDate() throws InterruptedException {
        HomePage page = getInstance(HomePage.class);
        PageParameters pp = new PageParameters();
        pp.put("until", "2011-02-01");
        SolrQuery q = page.createQuery(pp);
        assertEquals("dt:[2011-02-01T00:00:00Z TO *]", JetwickQuery.getFirstFilterQuery(q, "dt"));

        pp = new PageParameters();
        pp.put("until", "2011-02-01T00:00:00Z");
        q = page.createQuery(pp);
        assertEquals("dt:[2011-02-01T00:00:00Z TO *]", JetwickQuery.getFirstFilterQuery(q, "dt"));
    }

    @Test
    public void testWhithNoSolrSearch() throws InterruptedException {
        HomePage page = getInstance(HomePage.class);

        // normal query fails but set twitterfallback = false
        page.init(new SolrQuery("java"), 0, true);
        page.getQueueThread().run();
        assertNotNull(sentTweets);
        assertEquals("", uString);
        assertEquals("#java", qString);

        // do not trigger background search for the same query
        page.doSearch(new SolrQuery("java"), 0, true);
        assertNull(page.getQueueThread());

        // if only user search then set twitterFallback = true
        reset();
        page.doSearch(new SolrQuery().addFilterQuery("user:test"), 0, true);
        assertEquals("#test", uString);
        assertEquals("", qString);
        page.getQueueThread().run();

        // if 'normal query' AND 'user search' then set twitterFallback = false but trigger backgr. thread
        reset();
        page.doSearch(new SolrQuery("java").addFilterQuery("user:test"), 0, true);
        page.getQueueThread().join();
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
            public int getRateLimitFromCache() {
                return 100;
            }

            @Override
            public TwitterSearch setTwitter4JInstance(String token, String tokenSec) {
                return this;
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
        };
    }

    @Override
    protected RMIClient createRMIClient() {
        return new RMIClient(new Configuration()) {

            @Override
            public RMIClient init() {
                return this;
            }

            @Override
            public void send(TweetPackage tweets) throws RemoteException {
                sentTweets = tweets;
            }
        };
    }

    @Override
    protected SolrTweetSearch createSolrTweetSearch() {
        if (ownSolrTweetSearch == null)
            return super.createSolrTweetSearch();

        return ownSolrTweetSearch;
    }
}
