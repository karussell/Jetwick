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

import java.util.Date;
import com.google.inject.Module;
import de.jetwick.config.DefaultModule;
import de.jetwick.data.JTag;
import de.jetwick.snacktory.JResult;
import java.util.LinkedHashSet;
import java.util.Set;
import de.jetwick.JetwickTestClass;
import de.jetwick.es.ElasticTagSearchTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.es.ElasticTweetSearchTest;
import de.jetwick.es.ElasticUserSearchTest;
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
import de.jetwick.data.UrlEntry;
import de.jetwick.es.TweetQuery;
import de.jetwick.snacktory.HtmlFetcher;
import de.jetwick.util.GenericUrlResolver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetCollectorIntegrationTestClass extends JetwickTestClass {

    private ElasticUserSearchTest userSearchTester = new ElasticUserSearchTest();
    private ElasticTweetSearchTest tweetSearchTester = new ElasticTweetSearchTest();
    private ElasticTagSearchTest tagSearchTester = new ElasticTagSearchTest();
    private GenericUrlResolver urlResolver = new GenericUrlResolver(100).setResolveThreads(2);

    @BeforeClass
    public static void beforeClass() {
        ElasticTweetSearchTest.beforeClass();
    }

    @AfterClass
    public static void afterClass() {
        ElasticTweetSearchTest.afterClass();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        tagSearchTester.setUp();
        userSearchTester.setUp();
        tweetSearchTester.setUp();        
        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        tagSearchTester.tearDown();
        userSearchTester.tearDown();
        tweetSearchTester.tearDown();
    }

    @Test
    public void testUrlResolving() throws Exception {
        final Map<Thread, Throwable> exceptionMap = new HashMap<Thread, Throwable>();
        Thread.UncaughtExceptionHandler excHandler = createExceptionMapHandler(exceptionMap);

        // fill DB with one default tag
        tagSearchTester.getSearch().bulkUpdate(Arrays.asList(new JTag("java")), tagSearchTester.getSearch().getIndexName(), true);

        ElasticTweetSearch tweetSearch = tweetSearchTester.getSearch();
        ElasticUserSearch userSearch = userSearchTester.getSearch();
        TwitterSearch tws = new TwitterSearch() {

            @Override
            public boolean isInitialized() {
                return true;
            }

            @Override
            public long search(String q, Collection<JTweet> result, int tweets, long lastMillis) {
                JUser u = new JUser("timetabling");
                JTweet tw1 = new JTweet(1L, "... Egypt. http://apne.ws/dERa4A - XY #tEst", u);
                result.add(tw1);
                return lastMillis;
            }

            @Override
            public List<JTweet> getTweets(JUser user, Collection<JUser> users, int twPerPage) {
                return Collections.EMPTY_LIST;
            }
        };

        TweetConsumer tweetConsumer = getInstance(TweetConsumer.class);
        tweetConsumer.setUncaughtExceptionHandler(excHandler);
        tweetConsumer.start();

        TweetProducer tweetProducer = getInstance(TweetProducer.class);
        tweetProducer.setTwitterSearch(tws);
        tweetProducer.setUserSearch(userSearch);
        tweetProducer.setTagSearch(tagSearchTester.getSearch());
        tweetProducer.setQueue(tweetConsumer.register("tweet-producer", Integer.MAX_VALUE, 100));

        Thread tweetProducerThread = new Thread(tweetProducer);
        tweetProducerThread.setUncaughtExceptionHandler(excHandler);
        tweetProducerThread.start();
        // wait so let consumer consume
        Thread.sleep(500);

        tweetConsumer.interrupt();
        tweetProducerThread.interrupt();
        checkExceptions(exceptionMap);

        tweetSearch.forceEmptyQueueAndRefresh();
        List<JTweet> res = tweetSearch.searchTweets(new TweetQuery().addFilterQuery(ElasticTweetSearch.USER, "timetabling"));
        assertEquals(1, res.size());
        assertEquals(1, res.get(0).getUrlEntries().size());
        // no resolving for now
        assertTrue(res.get(0).getUrlEntries().iterator().next().getResolvedUrl().equals("http://apne.ws/dERa4A"));
    }

    @Test
    public void testProduceTweets() throws InterruptedException, Exception {
        final Map<Thread, Throwable> exceptionMap = new HashMap<Thread, Throwable>();
        Thread.UncaughtExceptionHandler excHandler = createExceptionMapHandler(exceptionMap);

        // fill DB with one default tag
        tagSearchTester.getSearch().bulkUpdate(Arrays.asList(new JTag("java")), tagSearchTester.getSearch().getIndexName(), true);

        ElasticUserSearch userSearch = userSearchTester.getSearch();
        ElasticTweetSearch tweetSearch = tweetSearchTester.getSearch();

        // already existing tweets must not harm
        tweetSearch.store(new JTweet(3L, "duplication tweet", new JUser("tmp")), true);
        TwitterSearch tws = new TwitterSearch() {

            @Override
            public boolean isInitialized() {
                return true;
            }

            @Override
            public long search(String q, Collection<JTweet> result, int tweets, long lastMillis) {
                JUser u = new JUser("timetabling");
                JTweet tw1 = new JTweet(1L, "test", u);
                result.add(tw1);

                tw1 = new JTweet(2L, "java test", u);
                result.add(tw1);

                // this tweet will be ignored and so it won't be indexed!
                tw1 = new JTweet(3L, "duplicate tweet", new JUser("anotheruser"));
                result.add(tw1);

                tw1 = new JTweet(4L, "reference a user: @timetabling", new JUser("user3"));
                result.add(tw1);

                assertEquals(4, result.size());
                return lastMillis;
            }

            @Override
            public List<JTweet> getTweets(JUser user, Collection<JUser> users, int twPerPage) {
                return Collections.EMPTY_LIST;
            }
        };

        TweetConsumer tweetConsumer = getInstance(TweetConsumer.class);
        tweetConsumer.setUncaughtExceptionHandler(excHandler);
        tweetConsumer.start();

        TweetProducer tweetProducer = getInstance(TweetProducer.class);
        tweetProducer.setTwitterSearch(tws);
        tweetProducer.setUserSearch(userSearch);
        tweetProducer.setTagSearch(tagSearchTester.getSearch());
        tweetProducer.setQueue(tweetConsumer.register("tweet-producer", Integer.MAX_VALUE, 100));

        Thread tweetProducerThread = new Thread(tweetProducer);
        tweetProducerThread.setUncaughtExceptionHandler(excHandler);
        tweetProducerThread.start();
        Thread.sleep(500);

        tweetConsumer.interrupt();
        checkExceptions(exceptionMap);
        tweetSearch.forceEmptyQueueAndRefresh();

        Set<JUser> users = new LinkedHashSet<JUser>();
        tweetSearch.query(users, new TweetQuery().addFilterQuery(ElasticTweetSearch.USER, "timetabling"));
        assertEquals(2, users.iterator().next().getOwnTweets().size());

        List<JUser> res = new ArrayList<JUser>();
        tweetSearch.query(res, new TweetQuery("java"));
        assertEquals(1, res.size());

        Collection<JTweet> coll = tweetSearch.searchTweets(new TweetQuery("duplicate"));
        assertEquals(1, coll.size());
        assertEquals("duplication tweet", coll.iterator().next().getText());

        coll = tweetSearch.searchTweets(new TweetQuery("duplication"));
        assertEquals(1, coll.size());
        assertEquals("duplication tweet", coll.iterator().next().getText());
    }

    @Test
    public void testArticleContains2Sources() throws InterruptedException, Exception {
        final Map<Thread, Throwable> exceptionMap = new HashMap<Thread, Throwable>();
        Thread.UncaughtExceptionHandler excHandler = createExceptionMapHandler(exceptionMap);

        // fill DB with one default tag
        tagSearchTester.getSearch().bulkUpdate(Arrays.asList(new JTag("java")), tagSearchTester.getSearch().getIndexName(), true);

        TwitterSearch tws = new TwitterSearch() {

            @Override
            public boolean isInitialized() {
                return true;
            }

            @Override
            public long search(String q, Collection<JTweet> result, int tweets, long lastMillis) {
                // make retweet older otherwise no retweet detection!
                Date dt = new Date();
                JTweet tw1 = new JTweet(10L, "A new #browser performance test: Rendering the #linux kernel impact graph on #github: http://t.co/0NCINwv", new JUser("jbandi")).setCreatedAt(dt);
                result.add(tw1);
                JTweet tw2 = new JTweet(11L, "RT @jbandi: A new #browser performance test: Rendering the #linux kernel impact graph on #github: http://t.co/0NCINwv", new JUser("adietisheim")).setCreatedAt(new Date(dt.getTime() + 1));
                result.add(tw2);
                return lastMillis;
            }

            @Override
            public List<JTweet> getTweets(JUser user, Collection<JUser> users, int twPerPage) {
                return Collections.EMPTY_LIST;
            }
        };

        ElasticTweetSearch tweetSearch = getInstance(ElasticTweetSearch.class);
        GenericUrlResolver resolver = getInstance(GenericUrlResolver.class);
        tweetSearch.addListener(resolver);
        
        TweetConsumer tweetConsumer = getInstance(TweetConsumer.class);
        tweetConsumer.setUncaughtExceptionHandler(excHandler);
        tweetConsumer.start();

        TweetProducer tweetProducer = getInstance(TweetProducer.class);
        tweetProducer.setTwitterSearch(tws);
        tweetProducer.setUserSearch(getInstance(ElasticUserSearch.class));
        tweetProducer.setTagSearch(tagSearchTester.getSearch());
        tweetProducer.setQueue(tweetConsumer.register("tweet-producer", Integer.MAX_VALUE, 100));

        Thread tweetProducerThread = new Thread(tweetProducer);
        tweetProducerThread.setUncaughtExceptionHandler(excHandler);
        tweetProducerThread.start();
        // let tweetconsumer do its work
        Thread.sleep(500);
                
        tweetSearch.forceEmptyQueueAndRefresh();
        List<JTweet> tweets = tweetSearch.searchTweets(new TweetQuery().setSort(ElasticTweetSearch.RT_COUNT, "desc"));
        assertEquals(2, tweets.size());
        assertEquals(1, tweets.get(0).getRetweetCount());
        assertEquals(0, tweets.get(1).getRetweetCount());        
    }

    @Override
    public Module createModule() {
        return new DefaultModule() {

            @Override
            public void installSearchModule() {
                bind(ElasticUserSearch.class).toInstance(userSearchTester.getSearch());
                bind(ElasticTweetSearch.class).toInstance(tweetSearchTester.getSearch());
            }

            @Override
            public GenericUrlResolver createGenericUrlResolver() {
                return urlResolver;
            }

            @Override
            public HtmlFetcher createHtmlFetcher() {
                return new HtmlFetcher() {

                    @Override
                    public JResult fetchAndExtract(String url, int timeout, boolean resolve) throws Exception {
                        return UrlEntry.createSimpleResult(url);
                    }

                    @Override
                    public String getResolvedUrl(String urlAsString, int timeout) {
                        // TODO NOW resolved url can be different!!
                        return urlAsString;
                    }
                };
            }
        };
    }
}
