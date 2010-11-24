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
import de.jetwick.rmi.RMIServer;
import de.jetwick.util.Helper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetCollector {

    // twClient.getTrend() ...  20 tweets per min
    // RT                  ... 100 tweets per sec (as of 9.5.2010)
    public static List<String> DEFAULT_ST = Arrays.asList("timetabling", "RT");//, "java");
//    , "algorithm", "solr",
//            "lucene", "netbeans", "db4o", "java", "javascript", "javafx", "dzone",
//            "oracle", "open source", "google", "obama", "RT",
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
        int maxTime = 6000;

        if (args.length == 0) {
            logger.info("You can specify the maximal time in seconds to collect "
                    + "the tweets via: -maxTime=100 (-1 means forever)");
        } else {
            Map<String, String> map = Helper.parseArguments(args);
            String maxTimeStr = map.get("maxTime");
            if (maxTimeStr != null)
                maxTime = Integer.parseInt(maxTimeStr);
        }
        logger.info("max collect time:" + maxTime);

        Runnable runOnExit = new Runnable() {

            @Override
            public void run() {
                logger.info("Finished via Shutdown hook!\n\nNow listing current thread stack traces");

                for (Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                    for (StackTraceElement el : entry.getValue()) {
                        logger.info(entry.getKey().getName() + " " + el);
                    }
                }
            }
        };

        Runtime.getRuntime().addShutdownHook(new Thread(runOnExit));

        Module module = new DefaultModule();
        Injector injector = Guice.createInjector(module);
        TwitterSearch tws = injector.getInstance(TwitterSearch.class);
        tws.init();
        Configuration cfg = injector.getInstance(Configuration.class);

        // add at least the default tags      
        WorkManager manager = injector.getInstance(WorkManager.class);
        manager.beginWork();
        TagDao tagDao = injector.getInstance(TagDao.class);
        tagDao.addAll(DEFAULT_ST);
        manager.endWork();

        TweetProducer twProducer = injector.getInstance(TweetProducer.class);
        twProducer.setMaxTime(maxTime);
        int tweetsPerBatch = cfg.getTweetsPerBatch();
        twProducer.setMaxFill((int) (2 * tweetsPerBatch));
        twProducer.setTwitterSearch(tws);
        twProducer.setUncaughtExceptionHandler(excHandler);
        if (cfg.isTweetResolveUrl()) {
            twProducer.setResolveUrls(true);
            twProducer.setResolveThreads(cfg.getTweetResolveUrlThreads());
            twProducer.setResolveTimeout(cfg.getTweetResolveUrlTimeout());
            twProducer.setUrlTitleCleaner(new UrlTitleCleaner(cfg.getUrlTitleAvoidList()));
        }
        twProducer.start();

        TweetConsumer twConsumer = injector.getInstance(TweetConsumer.class);
        twConsumer.setTweets(twProducer.getTweets());
        twConsumer.setTweetProducer(twProducer);
        twConsumer.setUncaughtExceptionHandler(excHandler);
        twConsumer.setTweetBatchSize(tweetsPerBatch);
        twConsumer.setOptimizeInterval(cfg.getTweetSearchOptimizeInterval());
        twConsumer.setOptimizeToSegmentsAfterUpdate(cfg.getTweetSearchCommitOptimizeSegments());
        twConsumer.setRemoveDays(cfg.getSolrRemoveDays());
        twConsumer.start();

//        TweetUpdater twUpdater = injector.getInstance(TweetUpdater.class);
//        twUpdater.setUsersPerUpdate(10);
//        twUpdater.setTweetProducer(twProducer);
//        twUpdater.setTwitterSearch(tws);
//        twUpdater.setUncaughtExceptionHandler(excHandler);
//        twUpdater.start();

        RMIServer rmiServer = injector.getInstance(RMIServer.class);
        rmiServer.setTweets(twProducer.getTweets());
        Thread rmiServerThread = rmiServer.createThread();
        rmiServerThread.start();

        twProducer.join();
        twConsumer.join();
//        twUpdater.join();
        rmiServerThread.interrupt();

        // use CyclicBarrier + FutureTask + Executors
    }
}

