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

import de.jetwick.JetwickTestClass;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrTweetSearchTest;
import de.jetwick.util.MyDate;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import twitter4j.Tweet;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetConsumerTest extends JetwickTestClass {

    private SolrTweetSearchTest tester = new SolrTweetSearchTest();    
    private TweetConsumer tweetConsumer;

    @Override
    @Before
    public void setUp() throws Exception {
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
        Queue<Tweet> queue = new LinkedBlockingQueue<Tweet>();
        Twitter4JTweet tw = new Twitter4JTweet(1L, "@daniel fancy!", "timetabling");
        queue.add(tw);
        Collection<SolrTweet> res = tweetConsumer.updateDbTweets(queue, 100);
        assertEquals(1, res.size());
        // 0 removeDays < tw.createdAt
//        assertEquals(0, res.getDeletedTweets().size());
    }

    @Test
    public void testAddSomeMore() {
//        dbHelper.setRemoveDays(1);        
//        tweetConsumer.setDbHelper(dbHelper);
        tweetConsumer.setRemoveDays(1);

        Queue<Tweet> queue = new LinkedBlockingQueue<Tweet>();
        Twitter4JTweet tw = new Twitter4JTweet(4L, "OldTweet", "userB");
        tw.setCreatedAt(new MyDate().minusDays(2).toDate());
        queue.add(tw);
        tw = new Twitter4JTweet(5L, "RT @userB: text", "timetabling");
        queue.add(tw);
        tw = new Twitter4JTweet(6L, "Bla bli", "userB");
        queue.add(tw);
        Collection<SolrTweet> res = tweetConsumer.updateDbTweets(queue, 100);
        assertEquals(2, res.size());
//        assertEquals(0, res.getDeletedTweets().size());
    }

    @Test
    public void testSolrData() {
        Queue<Tweet> queue = new LinkedBlockingQueue<Tweet>();
        Twitter4JTweet tw = new Twitter4JTweet(5L, "text", "timetabling");
        queue.add(tw);
        Collection<SolrTweet> res = tweetConsumer.updateDbTweets(queue, 100);
        assertEquals(1, res.size());
//        assertEquals(0, res.getDeletedTweets().size());

        queue.clear();
        tw = new Twitter4JTweet(6L, "RT @timetabling: text", "userB");
        queue.add(tw);
        res = tweetConsumer.updateDbTweets(queue, 100);
        assertEquals(2, res.size());
//        assertEquals(0, res.getDeletedTweets().size());
    }
}
