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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import de.jetwick.config.Configuration;
import de.jetwick.config.DefaultModule;
import de.jetwick.es.ElasticTagSearch;
import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.rmi.RMIServer;
import de.jetwick.util.GenericUrlResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetCollector {

    // twClient.getTrend() ...  20 tweets per min
    // RT                  ... 100 tweets per sec (as of 9.5.2010)
//    public static List<String> DEFAULT_ST = Arrays.asList("RT",
//            "java", "algorithm", "solr",
//            "lucene", "netbeans", "db4o", "java", "javascript", "javafx", "dzone",
//            "oracle", "open source", "google", "obama",
//            "wicket", "wikileaks", "world cup", "news");
    private static Logger logger = LoggerFactory.getLogger(TweetCollector.class);
    private static Thread.UncaughtExceptionHandler excHandler = new Thread.UncaughtExceptionHandler() {

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            logger.error("Thread '" + t.getName() + "' was aborted!", e);
            throw new UnsupportedOperationException("Not supported yet.");
        }
    };

    public static void main(String[] args) throws InterruptedException {
        Runnable runOnExit = new Runnable() {

            @Override
            public void run() {
                logger.info("Finished via Shutdown hook!");
            }
        };

        Runtime.getRuntime().addShutdownHook(new Thread(runOnExit));

        Module module = new DefaultModule();
        Injector injector = Guice.createInjector(module);
        TwitterSearch tws = injector.getInstance(TwitterSearch.class);
        ElasticTweetSearch tweetSearch = injector.getInstance(ElasticTweetSearch.class);

        // WAIT UNTIL AVAILABLE
        tweetSearch.waitUntilAvailable(10000);
        
        ElasticUserSearch userSearch = injector.getInstance(ElasticUserSearch.class);
        ElasticTagSearch tagSearch = injector.getInstance(ElasticTagSearch.class);
        Configuration cfg = injector.getInstance(Configuration.class);

        // 1. every producer has a separate queue (with a different capacity) to feed TweetConsumer:
        //      TProd1 -- queue1 --\
        //      TProd2 -- queue2 ---> TweetConsumer
        //      ...
        
        // 2. TweetConsumer polls N elements from every queue and feeds the results
        //    into the resolver - see GenericUrlResolver.
        
        // 4. Via ElasticTweetSearch:s commit listener the URL:s of tweets will be        
        //    resolved - 
        //    For every URL an article is created and feeded into the article index

        TweetConsumer twConsumer = injector.getInstance(TweetConsumer.class);
        twConsumer.setUncaughtExceptionHandler(excHandler);

        GenericUrlResolver resolver = injector.getInstance(GenericUrlResolver.class);        
        resolver.start();
        int queueCapacity = cfg.getUrlResolverInputQueueSize();
        // feeding consumer via twitter search (or offline fake)
        TweetProducer twProducer = injector.getInstance(TweetProducer.class);                
        twProducer.setTwitterSearch(tws);
        twProducer.setUserSearch(userSearch);
        twProducer.setTagSearch(tagSearch);
        twProducer.setQueue(twConsumer.register("producer-search", queueCapacity, 100));

        // feeding consumer via twitter keyword stream (gets keywords from tagindex)
        TweetProducerViaStream producerViaStream = injector.getInstance(TweetProducerViaStream.class);
        producerViaStream.setQueue(twConsumer.register("producer-stream", queueCapacity, 120));
        producerViaStream.setTwitterSearch(tws);
        producerViaStream.setTagSearch(tagSearch);
        producerViaStream.setUncaughtExceptionHandler(excHandler);
        producerViaStream.setTweetsPerSecLimit(cfg.getTweetsPerSecLimit());

        // feeding consumer from tweets of friends (of registered users)
        TweetProducerViaUsers producerFromFriends = injector.getInstance(TweetProducerViaUsers.class);
        producerFromFriends.setQueue(twConsumer.register("producer-friends", queueCapacity, 100));
        producerFromFriends.setTwitterSearch(tws);
        producerFromFriends.setUserSearch(userSearch);
        producerFromFriends.setUncaughtExceptionHandler(excHandler);

        // feeding consumer from UI        
        RMIServer rmiServer = injector.getInstance(RMIServer.class);
        rmiServer.setQueue(twConsumer.register("producer-rmi", queueCapacity, 20));
        Thread rmiServerThread = rmiServer.createThread();
  
        // configure tweet index to call UrlResolver after feeding of a tweet        
        tweetSearch.setRemoveOlderThanDays(cfg.getTweetSearchRemoveDays());
        tweetSearch.setBatchSize(cfg.getTweetSearchBatch());                

        Thread twProducerThread = new Thread(twProducer, "tweet-producer");
        twProducerThread.setUncaughtExceptionHandler(excHandler);
        twProducerThread.start();

        rmiServerThread.start();
        twConsumer.start();
        producerFromFriends.start();
        if (cfg.isStreamEnabled())
            producerViaStream.start();

        // ## JOIN
        twProducerThread.join();

        if (cfg.isStreamEnabled())
            producerViaStream.interrupt();

        producerFromFriends.interrupt();
        twConsumer.interrupt();
        rmiServerThread.interrupt();        
    }
}
