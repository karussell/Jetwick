/*
 *  Copyright 2010 Peter Karich jetwick_@_pannous_._info
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetwick.tw;

import de.jetwick.data.UrlEntry;
import de.jetwick.solr.SolrTweet;
import de.jetwick.tw.queue.AbstractTweetPackage;
import de.jetwick.tw.queue.TweetPackage;
import de.jetwick.util.Helper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class TweetUrlResolver extends MyThread {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private int resolveThreads = 5;
    private int resolveTimeout = 500;
    private ExecutorService service;
    private boolean test = true;
    private BlockingQueue<TweetPackage> packages;
    private BlockingQueue<TweetPackage> resultPackages = new LinkedBlockingQueue<TweetPackage>();
    private int maxFill = 1000;

    public TweetUrlResolver() {
        super("tweet-urlresolver");
    }

    public TweetUrlResolver setTest(boolean test) {
        this.test = test;
        return this;
    }

    public void setResolveTimeout(int resolveTimeout) {
        this.resolveTimeout = resolveTimeout;
    }

    public void setResolveThreads(int resolveThreads) {
        this.resolveThreads = resolveThreads;
    }

    public ExecutorService getService() {
        if (service == null)
            service = Executors.newFixedThreadPool(resolveThreads);

        return service;
    }

    public UrlExtractor createExtractor() {
        return new UrlExtractor().setResolveTimeout(resolveTimeout);
    }

    public TweetUrlResolver setReadingQueue(BlockingQueue<TweetPackage> tweetPackages) {
        packages = tweetPackages;
        return this;
    }
    private AtomicLong allTweets = new AtomicLong(0);

    @Override
    public void run() {
        logger.info("url resolve threads:" + resolveThreads);
        Collection<Callable<Object>> workerCollection = new ArrayList<Callable<Object>>(resolveThreads);
        for (int i = 0; i < resolveThreads; i++) {
            final int tmp = i;
            workerCollection.add(new Callable() {

                @Override
                public Object call() throws Exception {
                    while (true) {
                        TweetPackage pkg = packages.poll();
                        if (pkg == null) {
                            if (!myWait(1))
                                return null;

                            continue;
                        }

                        // log not too often
                        if (tmp == 0 || tmp == 1)
                            logger.info("sentTweets:" + allTweets.get());

                        for (SolrTweet tw : pkg.getTweets()) {
                            allTweets.addAndGet(1);
                            UrlExtractor extractor = createExtractor();
                            for (UrlEntry ue : extractor.setText(tw.getText()).run().getUrlEntries()) {
                                if (Helper.trimNL(Helper.trimAll(ue.getResolvedTitle())).isEmpty())
                                    continue;

                                tw.addUrlEntry(ue);
                            }
                        }
                        resultPackages.add(pkg);
                        int count = 0;
                        while (true) {
                            count = AbstractTweetPackage.calcNumberOfTweets(resultPackages);
                            if (count < maxFill)
                                break;

                            // log not too often
                            if (tmp == 0 || tmp == 1)
                                logger.info("... WAITING! " + count + " are too many tweets from url resolving!");
                            if (!myWait(20))
                                return null;
                        }
                    } // while
                } // call
            });
        }
        try {
            if (test)
                getService().invokeAll(workerCollection, 100, TimeUnit.MILLISECONDS);
            else
                getService().invokeAll(workerCollection);
        } catch (InterruptedException ex) {
            logger.info(getName() + " was interrupted:" + ex.getMessage());
        }
    }

    public BlockingQueue<TweetPackage> getResultQueue() {
        return resultPackages;
    }
    /**
     * Resolve the detected urls for the specified tweets.
     * @param tweets where the tweets come from
     * @param outTweets where the new tweets (with the new text) will be saved
     * @param threadCount how many threads to use
     */
//    public void resolveUrls(final BlockingQueue<SolrTweet> tweets,
//            final BlockingQueue<SolrTweet> outTweets, int threadCount) {
//        int maxThreads = Math.min(threadCount, tweets.size() / 5 + 1);
//        Collection<Callable<Object>> coll = new ArrayList<Callable<Object>>(maxThreads);
//        for (int i = 0; i < maxThreads; i++) {
//            coll.add(new Callable() {
//
//                @Override
//                public Object call() throws Exception {
//                    while (true) {
//                        SolrTweet tmpTw = tweets.poll();
//                        if (tmpTw == null)
//                            break;
//
//                        UrlExtractor extractor = createExtractor();
//                        for (UrlEntry ue : extractor.setText(tmpTw.getText()).run().getUrlEntries()) {
//                            if (Helper.trimNL(Helper.trimAll(ue.getResolvedTitle())).isEmpty())
//                                continue;
//
//                            tmpTw.addUrlEntry(ue);
//                        }
//                        outTweets.add(tmpTw);
//                    }
//                    return null;
//                }
//            });
//        }
//        // this will block the current thread
//        //getService().invokeAll(coll, 2L, TimeUnit.MINUTES);
//
//        startResolve(coll);
//        try {
//            finishResolve();
//        } catch (InterruptedException ex) {
//            Thread.currentThread().interrupt();
//        }
//    }
//
//    long remainingNanos;
//    long lastTime;
//
//    public void startResolve(Collection<Callable<Object>> callables) {
//        lastTime = System.nanoTime();
//        remainingNanos = TimeUnit.MINUTES.toNanos(2);
//        futures.clear();
//        for (Callable<Object> t : callables) {
//            futures.add(new FutureTask<Object>(t));
//        }
//        // Interleave time checks and calls to execute in case
//        // executor doesn't have any/much parallelism.
//        Iterator<Future<Object>> it = futures.iterator();
//        while (it.hasNext()) {
//            getService().execute((Runnable) (it.next()));
//            long now = System.nanoTime();
//            remainingNanos -= now - lastTime;
//            lastTime = now;
//            if (remainingNanos <= 0)
//                return;
//        }
//    }
//
//    public void finishResolve() throws InterruptedException {
//        boolean done = false;
//        try {
//            for (Future<Object> f : futures) {
//                if (!f.isDone()) {
//                    if (remainingNanos <= 0)
//                        return;
//                    try {
//                        f.get(remainingNanos, TimeUnit.NANOSECONDS);
//                    } catch (CancellationException ignore) {
//                    } catch (ExecutionException ignore) {
//                    } catch (TimeoutException toe) {
//                        return;
//                    }
//                    long now = System.nanoTime();
//                    remainingNanos -= now - lastTime;
//                    lastTime = now;
//                }
//            }
//            done = true;
//            return;
//        } finally {
//            if (!done)
//                for (Future<Object> f : futures) {
//                    f.cancel(true);
//                }
//
//            futures.clear();
//        }
//    }
}
