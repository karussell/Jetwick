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

import de.jetwick.es.ElasticTweetSearch;
import com.google.inject.Module;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import de.jetwick.JetwickTestClass;
import de.jetwick.config.DefaultModule;
import de.jetwick.data.JTweet;
import de.jetwick.es.ElasticTweetSearchTest;
import de.jetwick.data.JUser;
import de.jetwick.tw.queue.TweetPackage;
import de.jetwick.util.MyDate;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetConsumerTest extends JetwickTestClass {

    private ElasticTweetSearchTest tester = new ElasticTweetSearchTest();
    private TweetConsumer tweetConsumer;

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
        tester.setUp();
        super.setUp();
        tweetConsumer = getInstance(TweetConsumer.class);
        tweetConsumer.setTweetSearch(tester.getSearch());
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        tester.tearDown();
    }

    @Override
    public Module createModule() {
        return new DefaultModule() {

            @Override
            public void installSearchModule() {
                bind(ElasticTweetSearch.class).toInstance(tester.getSearch());
            }            
        };
    }        

    @Test
    public void testAddAll() {        
        tweetConsumer.setRemoveDays(1);
        LinkedBlockingQueue<TweetPackage> queue = new LinkedBlockingQueue<TweetPackage>();
        JTweet tw = createTweet(1L, "@daniel fancy!", "timetabling");
        tw.setCreatedAt(new Date());        
        tweetConsumer.updateTweets(Arrays.asList(tw));
    }

    @Test
    public void testAddSomeMore() {
//        dbHelper.setRemoveDays(1);        
//        tweetConsumer.setDbHelper(dbHelper);
        tweetConsumer.setRemoveDays(1);

        BlockingQueue<TweetPackage> queue = new LinkedBlockingQueue<TweetPackage>();
        JTweet tw = createTweet(4L, "OldTweet", "userB");
        tw.setCreatedAt(new MyDate().minusDays(2).toDate());
        JTweet tw2 = createTweet(5L, "RT @userB: text", "timetabling");
        tw2.setCreatedAt(new Date());
        JTweet tw3 = createTweet(6L, "Bla bli", "userB");
        tw3.setCreatedAt(new Date());
        
        tweetConsumer.updateTweets(Arrays.asList(tw, tw2, tw3));        
    }

    @Test
    public void testSolrData() {
        JTweet tw = createTweet(5L, "text", "timetabling");
        tw.setCreatedAt(new Date());        
        tweetConsumer.updateTweets(Arrays.asList(tw));
        tw = createTweet(6L, "RT @timetabling: text", "userB");
        tw.setCreatedAt(new Date());        
        tweetConsumer.updateTweets(Arrays.asList(tw));        
    }

    JTweet createTweet(long id, String twText, String user) {
        return new JTweet(id, twText, new JUser(user)).setCreatedAt(new Date(id));
    }
}
