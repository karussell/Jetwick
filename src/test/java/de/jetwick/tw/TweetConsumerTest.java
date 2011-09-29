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

import java.util.Random;
import de.jetwick.es.ElasticTweetSearch;
import com.google.inject.Module;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import de.jetwick.JetwickTestClass;
import de.jetwick.config.DefaultModule;
import de.jetwick.data.JTweet;
import de.jetwick.es.ElasticTweetSearchTest;
import de.jetwick.data.JUser;
import de.jetwick.data.UrlEntry;
import de.jetwick.util.GenericUrlResolver;
import de.jetwick.util.MyDate;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
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
    private GenericUrlResolver resolver;

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
        resolver = getInstance(GenericUrlResolver.class);
        tweetConsumer = getInstance(TweetConsumer.class);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        tester.tearDown();
    }

    @Test
    public void testAddAll() {
        tester.getSearch().setRemoveOlderThanDays(1);

        BlockingQueue<JTweet> queue = tweetConsumer.register("addAll", Integer.MAX_VALUE, 1);
        JTweet tw = createTweet(1L, "@daniel fancy!", "timetabling");
        tw.setCreatedAt(new Date());
        queue.add(tw.setFeedSource("addAll"));
        tweetConsumer.executeOneBatch();

        assertEquals(1, resolver.getInputQueue().size());
    }

    @Test
    public void testAddSomeMore() {
        tester.getSearch().setRemoveOlderThanDays(1);

        BlockingQueue<JTweet> queue = tweetConsumer.register("tweet-producer", Integer.MAX_VALUE, 3);
        JTweet tw = createTweetWithUrl(4L, "http://test.de text", "userB", "http://test.de");
        tw.setCreatedAt(new MyDate().minusDays(2).toDate());
        JTweet tw2 = createTweetWithUrl(5L, "RT @userB: http://test.de text", "timetabling", "http://test.de");
        tw2.setCreatedAt(new Date());
        JTweet tw3 = createTweet(6L, "Bla bli", "userB");
        tw3.setCreatedAt(new Date());
        queue.addAll(Arrays.asList(tw, tw2, tw3));
        tweetConsumer.executeOneBatch();

        assertEquals(2, resolver.getInputQueue().size());
        assertTrue(resolver.getInputQueue().contains(tw));
        assertFalse(resolver.getInputQueue().contains(tw2));
        assertTrue(resolver.getInputQueue().contains(tw3));
    }

    @Test
    public void testESData() {
        BlockingQueue<JTweet> queue = tweetConsumer.register("tweet-producer", Integer.MAX_VALUE, 1);
        String url = "http://irgendwas.de";
        JTweet tw = createTweetWithUrl(5L, url + " text", "timetabling", url);
        tw.setCreatedAt(new Date());
        queue.add(tw);
        tweetConsumer.executeOneBatch();
        assertEquals(1, resolver.getInputQueue().size());

        resolver.getInputQueue().clear();
        tw = createTweetWithUrl(6L, "RT @timetabling: " + url + " text", "userB", url);
        tw.setCreatedAt(new Date());
        queue.add(tw);
        tweetConsumer.executeOneBatch();
        assertEquals(0, resolver.getInputQueue().size());        
    }

    @Test
    public void testTweetCache() {
        BlockingQueue<JTweet> queue = tweetConsumer.register("tweet-producer", Integer.MAX_VALUE, 2);
        JTweet tw1 = createTweet(1L, "text1", "timetabling");
        tw1.setCreatedAt(new Date());
        JTweet tw3 = createTweet(3L, "text3", "timetabling");
        tw3.setCreatedAt(new Date());
        queue.addAll(Arrays.asList(tw1, tw1, tw3));

        tweetConsumer.initTweetCache();
        tweetConsumer.executeOneBatch();

        assertEquals(2, resolver.getInputQueue().size());
        assertTrue(resolver.getInputQueue().contains(tw1));
        assertTrue(resolver.getInputQueue().contains(tw3));
        resolver.getInputQueue().clear();

        queue = tweetConsumer.register("tweet-producer2", Integer.MAX_VALUE, 1);
        JTweet tw2 = createTweet(2L, "text2", "timetabling");
        tw2.setCreatedAt(new Date());
        queue.addAll(Arrays.asList(tw1, tw2, tw3));
        tweetConsumer.executeOneBatch();

        assertEquals(1, resolver.getInputQueue().size());
        assertFalse(resolver.getInputQueue().contains(tw1));
        assertTrue(resolver.getInputQueue().contains(tw2));
        assertFalse(resolver.getInputQueue().contains(tw3));
    }

    JTweet createTweet(long id, String twText, String user) {
        Random rand = new Random();
        double d = rand.nextDouble();
        String url = "http://test.de/" + d;
        return createTweetWithUrl(id, twText, user, url);
    }

    JTweet createTweetWithUrl(long id, String twText, String user, String url) {
        int index = twText.indexOf(url);
        if (index < 0) {
            twText = url + " " + twText;
            index = 0;
        }

        UrlEntry ue = new UrlEntry(index, index + url.length(), url);
        return new JTweet(id, twText, new JUser(user)).setCreatedAt(new Date(id)).addUrlEntry(ue);
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
}
