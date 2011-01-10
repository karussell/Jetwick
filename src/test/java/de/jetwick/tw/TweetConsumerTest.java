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

import de.jetwick.es.ElasticTweetSearchTest;
import de.jetwick.JetwickTestClass;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrTweetSearchTest;
import de.jetwick.solr.SolrUser;
import de.jetwick.tw.queue.TweetPackage;
import de.jetwick.tw.queue.TweetPackageList;
import de.jetwick.util.MyDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetConsumerTest extends JetwickTestClass {

    private ElasticTweetSearchTest tester = new ElasticTweetSearchTest();
    private TweetConsumer tweetConsumer;

    @Override
    @Before
    public void setUp() throws Exception {
        // TODO call beforeClass instead !!
        tester.setUp();
        super.setUp();
        tweetConsumer = getInstance(TweetConsumer.class);
        tweetConsumer.setTweetSearch(tester.getTweetSearch());
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        tester.tearDown();
    }

    @Test
    public void testAddAll() {
        tweetConsumer.setRemoveDays(1);
//        dbHelper.setRemoveDays(1);
//        tweetConsumer.setDbHelper(dbHelper);
        LinkedBlockingQueue<TweetPackage> queue = new LinkedBlockingQueue<TweetPackage>();
        SolrTweet tw = createTweet(1L, "@daniel fancy!", "timetabling");
        tw.setCreatedAt(new Date());
        queue.add(new TweetPackageList("").init(0, Arrays.asList(tw)));
        Collection<SolrTweet> res = tweetConsumer.updateTweets(queue, 100);
        assertEquals(1, res.size());
        // 0 removeDays < tw.createdAt
//        assertEquals(0, res.getDeletedTweets().size());
    }

    @Test
    public void testAddSomeMore() {
//        dbHelper.setRemoveDays(1);        
//        tweetConsumer.setDbHelper(dbHelper);
        tweetConsumer.setRemoveDays(1);

        BlockingQueue<TweetPackage> queue = new LinkedBlockingQueue<TweetPackage>();
        SolrTweet tw = createTweet(4L, "OldTweet", "userB");
        tw.setCreatedAt(new MyDate().minusDays(2).toDate());
        SolrTweet tw2 = createTweet(5L, "RT @userB: text", "timetabling");
        tw2.setCreatedAt(new Date());
        SolrTweet tw3 = createTweet(6L, "Bla bli", "userB");
        tw3.setCreatedAt(new Date());
        queue.add(new TweetPackageList("").init(0, Arrays.asList(tw, tw2, tw3)));
        Collection<SolrTweet> res = tweetConsumer.updateTweets(queue, 100);
        assertEquals(2, res.size());
//        assertEquals(0, res.getDeletedTweets().size());
    }

    @Test
    public void testSolrData() {
        BlockingQueue<TweetPackage> queue = new LinkedBlockingQueue<TweetPackage>();
        SolrTweet tw = createTweet(5L, "text", "timetabling");
        tw.setCreatedAt(new Date());
        queue.add(new TweetPackageList("").init(0, Arrays.asList(tw)));
        Collection<SolrTweet> res = tweetConsumer.updateTweets(queue, 100);
        assertEquals(1, res.size());
//        assertEquals(0, res.getDeletedTweets().size());

        queue.clear();
        tw = createTweet(6L, "RT @timetabling: text", "userB");
        tw.setCreatedAt(new Date());
        queue.add(new TweetPackageList("").init(0, Arrays.asList(tw)));
        res = tweetConsumer.updateTweets(queue, 100);
        assertEquals(2, res.size());
//        assertEquals(0, res.getDeletedTweets().size());
    }

    SolrTweet createTweet(long id, String twText, String user) {
        return new SolrTweet(id, twText, new SolrUser(user)).setCreatedAt(new Date(id));
    }
}
