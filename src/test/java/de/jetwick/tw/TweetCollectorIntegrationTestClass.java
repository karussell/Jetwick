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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.es.ElasticTweetSearchTest;
import de.jetwick.es.ElasticUserSearchTest;
import com.google.inject.Inject;
import de.jetwick.config.Configuration;
import de.jetwick.data.TagDao;
import de.jetwick.hib.HibTestClass;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
import de.jetwick.solr.TweetQuery;
import de.jetwick.tw.queue.TweetPackage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetCollectorIntegrationTestClass extends HibTestClass {

    // TODO later: @Inject
    private ElasticUserSearchTest userSearchTester = new ElasticUserSearchTest();
    private ElasticTweetSearchTest tweetSearchTester = new ElasticTweetSearchTest();
    @Inject
    private TagDao tagDao;

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
        super.setUp();      
        userSearchTester.setUp();
        tweetSearchTester.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        userSearchTester.tearDown();
        tweetSearchTester.tearDown();
    }

    @Test
    public void testProduceTweets() throws InterruptedException, Exception {
        final Map<Thread, Throwable> exceptionMap = new HashMap<Thread, Throwable>();
        Thread.UncaughtExceptionHandler excHandler = createExceptionMapHandler(exceptionMap);

        // fill DB with default tags
        tagDao.addAll(Arrays.asList("java"));
        // commit not necessary
//        assertEquals(1, tagDao.findAll().size());
//        assertNull(userDao.findByName("timetabling"));

        // already existing tweets must not harm
//        tweetDao.save(new YTweet(3L, "duplicate tweet"));
        ElasticTweetSearch tweetSearch = tweetSearchTester.getTweetSearch();
        ElasticUserSearch userSearch = userSearchTester.getUserSearch();
        tweetSearch.update(Arrays.asList(new SolrTweet(3L, "duplication tweet", new SolrUser("tmp"))));
        tweetSearch.refresh();

        Credits cred = new Configuration().getTwitterSearchCredits();
        TwitterSearch tws = new TwitterSearch() {

            @Override
            public boolean isInitialized() {
                return true;
            }                        

            @Override
            public long search(String q, Collection<SolrTweet> result, int tweets, long sinceId) {
                SolrUser u = new SolrUser("timetabling");
                SolrTweet tw1 = new SolrTweet(1L, "test", u);
                result.add(tw1);

                tw1 = new SolrTweet(2L, "java test", u);
                result.add(tw1);

                // this tweet will be ignored and so it won't be indexed!
                tw1 = new SolrTweet(3L, "duplicate tweet", new SolrUser("anotheruser"));
                result.add(tw1);

                tw1 = new SolrTweet(4L, "reference a user: @timetabling", new SolrUser("user3"));
                result.add(tw1);

                assertEquals(4, result.size());
                return result.size();
            }

            @Override
            public List<SolrTweet> getTweets(SolrUser user, Collection<SolrUser> users, int twPerPage) {
                return Collections.EMPTY_LIST;
            }
        }.setConsumer(cred.getConsumerKey(), cred.getConsumerSecret());
        tws.setTwitter4JInstance(cred.getToken(), cred.getTokenSecret());

        TweetProducer tweetProducer = getInstance(TweetProducer.class);
        tweetProducer.setTwitterSearch(tws);
        tweetProducer.setUserSearch(userSearch);
        Thread tweetProducerThread = new Thread(tweetProducer);
        tweetProducerThread.setUncaughtExceptionHandler(excHandler);
        tweetProducerThread.start();

        tweetProducerThread.join(3000);

        TweetUrlResolver twUrlResolver = getInstance(TweetUrlResolver.class);
        twUrlResolver.setResolveThreads(1);
        twUrlResolver.setReadingQueue(tweetProducer.getQueue());
        twUrlResolver.setUncaughtExceptionHandler(excHandler);
        twUrlResolver.setTest(false);
        twUrlResolver.start();

        BlockingQueue<TweetPackage> queue2 = twUrlResolver.getResultQueue();
        TweetConsumer tweetConsumer = getInstance(TweetConsumer.class);
        tweetConsumer.setUncaughtExceptionHandler(excHandler);
        tweetConsumer.setReadingQueue(queue2);
        tweetConsumer.setTweetBatchSize(1);
        tweetConsumer.setTweetSearch(tweetSearch);
        tweetConsumer.start();

        twUrlResolver.join(1000);
        tweetConsumer.interrupt();
        checkExceptions(exceptionMap);

//        YUser u = userDao.findByName("timetabling");
//        assertEquals(2, u.getOwnTweets().size());
//
//        commitAndReopenDB();
//        u = userDao.findByName("timetabling");
//        assertEquals(2, u.getOwnTweets().size());

        List<SolrUser> res = new ArrayList<SolrUser>();
        tweetSearch.search(res, new TweetQuery("java"));
        assertEquals(1, res.size());

        res.clear();
        tweetSearch.search(res, new TweetQuery("duplicate"));
        assertEquals(0, res.size());        
        
        res.clear();
        tweetSearch.search(res, new TweetQuery("duplication"));
        assertEquals(1, res.size());
        assertEquals("tmp", res.get(0).getScreenName());
    }
}
