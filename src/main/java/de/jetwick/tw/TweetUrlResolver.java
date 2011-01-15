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

import com.google.inject.Inject;
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
    @Inject
    // if no inject then use empty list:
    private UrlTitleCleaner urlCleaner = new UrlTitleCleaner();

    public TweetUrlResolver() {
        super("tweet-urlresolver");
    }

    public TweetUrlResolver setTest(boolean test) {
        this.test = test;
        return this;
    }

    public void setMaxFill(int maxFill) {
        this.maxFill = maxFill;
    }        

    public void setUrlCleaner(UrlTitleCleaner urlCleaner) {
        this.urlCleaner = urlCleaner;
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
        return new UrlExtractor().setCleaner(urlCleaner).setResolveTimeout(resolveTimeout);
    }

    public TweetUrlResolver setReadingQueue(BlockingQueue<TweetPackage> tweetPackages) {
        packages = tweetPackages;
        return this;
    }

    public BlockingQueue<TweetPackage> getResultQueue() {
        return resultPackages;
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
                    try {
                        while (true) {
                            TweetPackage pkg = packages.poll();
                            if (pkg == null) {
                                // log not too often
                                if (tmp == 0 || tmp == 1)
                                    logger.info("urlResolver: no tweet packages in queue");
                                
                                if (!myWait(10))
                                    return null;

                                continue;
                            }

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
                            // log not too often
                            if (tmp == 0 || tmp == 1)
                                logger.info("sentTweets:" + allTweets.get());

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
                    } catch (Exception ex) {
                        logger.error("one url resolver died", ex);
                    }
                    return null;
                } // call
            });
        }
        try {
            if (test)
                getService().invokeAll(workerCollection, 100, TimeUnit.MILLISECONDS);
            else
                getService().invokeAll(workerCollection);

            logger.info("FINISHED " + getName());
        } catch (InterruptedException ex) {
            logger.info(getName() + " was interrupted:" + ex.getMessage());
        }
    }   
}
