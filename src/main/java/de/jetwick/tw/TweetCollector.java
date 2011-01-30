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
import com.wideplay.warp.persist.WorkManager;
import de.jetwick.config.Configuration;
import de.jetwick.config.DefaultModule;
import de.jetwick.data.TagDao;
import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.rmi.RMIServer;
import de.jetwick.tw.queue.TweetPackage;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetCollector {

    // twClient.getTrend() ...  20 tweets per min
    // RT                  ... 100 tweets per sec (as of 9.5.2010)
    public static List<String> DEFAULT_ST = Arrays.asList("##timetabling", "RT",
            "java", "algorithm", "solr",
            "lucene", "netbeans", "db4o", "java", "javascript", "javafx", "dzone",
            "oracle", "open source", "google", "obama",
            "wicket", "wikileaks", "world cup", "news");
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
        ElasticUserSearch userSearch = injector.getInstance(ElasticUserSearch.class);
        Configuration cfg = injector.getInstance(Configuration.class);

        // add at least the default tags      
        WorkManager manager = injector.getInstance(WorkManager.class);
        manager.beginWork();
        TagDao tagDao = injector.getInstance(TagDao.class);
        tagDao.addAll(DEFAULT_ST);
        manager.endWork();

        // producer -> queue1 -> resolve url (max threads) -> queue2 -> feed solr (min time, max tweets)
        //              /\
        //              ||
        //          rmi from UI

        // feeding queue1 from twitter4j searches
        TweetProducer twProducer = injector.getInstance(TweetProducer.class);
        int tweetsPerBatch = cfg.getTweetsPerBatch();
        twProducer.setMaxFill(2 * tweetsPerBatch);
        twProducer.setTwitterSearch(tws);
        twProducer.setUserSearch(userSearch);        

        BlockingQueue<TweetPackage> queue1 = twProducer.getQueue();
        // feeding queue1 from UI
        RMIServer rmiServer = injector.getInstance(RMIServer.class);
        rmiServer.setFeedingQueue(queue1);
        Thread rmiServerThread = rmiServer.createThread();

        // reading queue1 and send to queue2
        TweetUrlResolver twUrlResolver = injector.getInstance(TweetUrlResolver.class);
        twUrlResolver.setReadingQueue(queue1);
        twUrlResolver.setResolveThreads(cfg.getTweetResolveUrlThreads());
        twUrlResolver.setResolveTimeout(cfg.getTweetResolveUrlTimeout());
        twUrlResolver.setUncaughtExceptionHandler(excHandler);
        twUrlResolver.setTest(false);
        twUrlResolver.setMaxFill(2 * tweetsPerBatch);        
        twUrlResolver.setUrlCleaner(new UrlTitleCleaner(cfg.getUrlTitleAvoidList()));

        // reading queue2
        BlockingQueue<TweetPackage> queue2 = twUrlResolver.getResultQueue();
        TweetConsumer twConsumer = new TweetConsumer();
        twConsumer.setReadingQueue(queue2);
        twConsumer.setUncaughtExceptionHandler(excHandler);
        twConsumer.setTweetBatchSize(tweetsPerBatch);
        twConsumer.setOptimizeInterval(cfg.getTweetSearchOptimizeInterval());
        twConsumer.setOptimizeToSegmentsAfterUpdate(cfg.getTweetSearchCommitOptimizeSegments());
        twConsumer.setRemoveDays(cfg.getSolrRemoveDays());
        twConsumer.setTweetSearch(tweetSearch);

        rmiServerThread.start();
        twConsumer.start();
        twUrlResolver.start();
        Thread twProducerThread = new Thread(twProducer, "tweet-producer");
        twProducerThread.setUncaughtExceptionHandler(excHandler);
        twProducerThread.start();

        twProducerThread.join();
        twConsumer.interrupt();
        twUrlResolver.interrupt();
        rmiServerThread.interrupt();
    }
}

