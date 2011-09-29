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
package de.jetwick.util;

import com.google.inject.Inject;
import de.jetwick.tw.*;
import de.jetwick.data.JTweet;
import de.jetwick.data.UrlEntry;
import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.snacktory.HtmlFetcher;
import de.jetwick.snacktory.JResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.elasticsearch.common.collect.MapMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class takes the urls from article index and resolves them. 
 * Additionally and more importantly it stores the text and title into article index.
 * 
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class GenericUrlResolver extends MyThread implements AnyExecutor<JTweet> {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private int resolveThreads = 5;
    private int resolveTimeout = 500;
    private ExecutorService service;
    private long testWait = -1;
    protected BlockingQueue<JTweet> resolverQueue;
    @Inject
    private ElasticTweetSearch tweetSearch;
    private UrlTitleCleaner urlTitleCleaner = new UrlTitleCleaner();
    @Inject
    private HtmlFetcher fetcher;
    private final Map<String, JTweet> unresolvedCache;
    private final Map<String, Object> tooOldMap;
    private static final Object OBJECT = new Object();
    private AtomicInteger counter = new AtomicInteger(0);
    private AtomicInteger emptyTitleCounter = new AtomicInteger(0);
    private AtomicLong start = new AtomicLong(System.nanoTime());

//    public GenericUrlResolver() {
//        this(600);
//    }
    public GenericUrlResolver(int queueSize) {
        super("generic-url-resolver");
        unresolvedCache = createGenericCache(5000, 24 * 60);
        tooOldMap = createGenericCache(500, 24 * 60);
        resolverQueue = new LinkedBlockingQueue<JTweet>(queueSize);
    }

    public static <K, V> Map<K, V> createGenericCache(int count, int minutes) {
        // do NOT use .softKeys() otherwise we will get == comparison which
        // is bad for 'new Long'        
        return new MapMaker().concurrencyLevel(20).
                maximumSize(count).expireAfterWrite(minutes, TimeUnit.MINUTES).makeMap();
    }

    public GenericUrlResolver setHtmlFetcher(HtmlFetcher fetcher) {
        this.fetcher = fetcher;
        return this;
    }

    public GenericUrlResolver setTest(long testWait) {
        this.testWait = testWait;
        return this;
    }

    public void setResolveTimeout(int resolveTimeout) {
        this.resolveTimeout = resolveTimeout;
    }

    public int getResolveTimeout() {
        return resolveTimeout;
    }

    public GenericUrlResolver setResolveThreads(int resolveThreads) {
        this.resolveThreads = resolveThreads;
        return this;
    }

    public ExecutorService getService() {
        if (service == null)
            service = Executors.newFixedThreadPool(resolveThreads);

        return service;
    }

    public BlockingQueue<JTweet> getInputQueue() {
        return resolverQueue;
    }

    JTweet findUrlInCache(String url) {
        return unresolvedCache.get(url);
    }

    int getUnresolvedSize() {
        return unresolvedCache.size();
    }

    @Override
    public void run() {
        Collection<Callable<Object>> workerCollection = new ArrayList<Callable<Object>>(resolveThreads);
        for (int i = 0; i < resolveThreads; i++) {
            final int tmp = i;
            workerCollection.add(new Callable() {

                @Override
                public Object call() throws Exception {
                    try {
                        while (true) {
                            if (!executeResolve(tmp))
                                break;
                        }
                        logger.info(getName() + " stopped");
                    } catch (Throwable ex) {
                        logger.error("url resolver " + tmp + "died", ex);
                    }
                    return null;
                }
            });
        }
        try {
            if (testWait > 0)
                getService().invokeAll(workerCollection, testWait, TimeUnit.MILLISECONDS);
            else
                getService().invokeAll(workerCollection);

            logger.warn("FINISHED " + getName() + " testWait:" + testWait);
        } catch (InterruptedException ex) {
            logger.info(getName() + " was interrupted:" + ex.getMessage());
        }
    }

    public void queueObject(JTweet tw) {
        // if tweet is persistent we need to queue it
        boolean directlyQueueIt = false;
        String url = tw.getUrl();
        if (tweetSearch.tooOld(tw.getCreatedAt())) {            
            tooOldMap.put(url, OBJECT);
            unresolvedCache.remove(url);
            directlyQueueIt = true;
        } else {
            if (Helper.isEmpty(url))
                tweetSearch.queueObject(tw);
            else if (tooOldMap.containsKey(url)) {
                logger.warn("(2) Skipped too old tweet: " + url);
                directlyQueueIt = true;
            } else {
                putObject(tw);
            }
        }
        if (!directlyQueueIt && tw.isPersistent())
            tweetSearch.queueObject(tw);
    }

    void putObject(JTweet tw) {
        if (isTweetInIndex(tw)) {
            // no need to queue again to aindex as we queue if article already exists on every resolve
            unresolvedCache.remove(tw.getUrl());
            canRemoveOrigUrl(tw);
            tweetSearch.queueObject(tw);
        } else {
            if (canRemoveOrigUrl(tw)) {
                tweetSearch.queueObject(tw);
                return;
            }

            String url = tw.getUrl();
            boolean alreadyExistent = false;
            for (int i = 0; i < 2; i++) {
                JTweet old = unresolvedCache.put(url, tw);
                if (old != null) {
                    if (tw.getTwitterId() == old.getTwitterId())
                        tw.updateFrom(old);

                    tweetSearch.queueObject(tw);
                    alreadyExistent = true;
                    break;
                }

                String tmp = getFirstOrigUrl(tw);
                if (Helper.isEmpty(tmp) || tmp.equals(url))
                    break;
                url = tmp;
                // try again for original url
            }

            if (!alreadyExistent)
                try {                    
                    resolverQueue.put(tw);                    
                } catch (InterruptedException ex) {
                    logger.error("Couldn't put article:" + tw.getUrl(), ex);
                }
        }
    }

    private String getFirstOrigUrl(JTweet tw) {
        if (tw.getUrlEntries().size() > 0)
            return tw.getUrlEntries().iterator().next().getOriginalUrl(tw);

        return null;
    }

    public boolean executeResolve(final int thread) {
        JTweet tweet = null;
        try {
            tweet = resolverQueue.take();
        } catch (Exception ex) {
            if (thread == 0)
                logger.warn("url resolver " + thread + " died " + ex.getMessage());
            return false;
        }

        String origUrl = tweet.getUrl();
        String url = origUrl;
        try {
            boolean doFetch = true;
            String resUrl = fetcher.getResolvedUrl(url, resolveTimeout);
            if (!Helper.isEmpty(resUrl) && resUrl.length() > url.length()) {
                url = resUrl;
                // check if resolved url already exists
                if (exists(resUrl)) {
                    unresolvedCache.remove(resUrl);
                    doFetch = false;
                }
            }
            if (doFetch) {
                JResult res = fetcher.fetchAndExtract(url, resolveTimeout, false);

                // set resolved url
                if (tweet.getUrlEntries().size() > 0) {
                    UrlEntry ue = tweet.getUrlEntries().iterator().next();
                    ue.setResolvedUrl(res.getUrl());
                    ue.setResolvedTitle(res.getTitle());
                    ue.setResolvedSnippet(res.getText());
                    ue.setResolvedDomain(Helper.extractDomain(url));
                }

                if (urlTitleCleaner.contains(res.getTitle()))
                    tweet.setQuality(20);

                if (res.getTitle().isEmpty())
                    emptyTitleCounter.addAndGet(1);
                counter.addAndGet(1);
                if (thread < 3) {
                    float secs = (System.nanoTime() - start.get()) / 1e+9f;
                    logger.info(thread + "| " + counter.get() / secs + " entries/sec"//, secs:" + secs
                            + ", feeded:" + counter
                            + ", resolverQueue.size:" + resolverQueue.size()
                            + ", unresolved.size:" + unresolvedCache.size()
                            + ", tooOld.size:" + tooOldMap.size()
                            + ", empty titles:" + emptyTitleCounter);
                }
            }

        } catch (Exception ex) {
            //logger.info("Error while resolveAndFetch url:" + art.getUrl() + " Error:" + Helper.getMsg(ex));
            tweet.setQuality(Math.round(tweet.getQuality() * 0.8f));
        } finally {
            // always feed the article even if there was an error            
            tweetSearch.queueObject(tweet);

            // real time get ensures that we have at least the url in aindex (not so for origURL!)
            unresolvedCache.remove(tweet.getUrl());

            // DISABLED for now as 
//            if (!checkAgainQueue.offer(art))
//                logger.error("checkAgainQueue full. Skipped:" + art.getUrl());
        }
        return true;
    }

    boolean isTweetInIndex(JTweet tw) {
        JTweet existing = tweetSearch.findByTwitterId(tw.getTwitterId());
        if (existing != null)
            return true;

        return exists(tw.getUrl());
    }

    boolean canRemoveOrigUrl(JTweet tw) {
        boolean remove = false;
        for (UrlEntry as : tw.getUrlEntries()) {
            String oUrl = as.getOriginalUrl(tw);
            // is original url already in index?
            if (oUrl != null && exists(oUrl)) {
                unresolvedCache.remove(oUrl);
                remove = true;
            }
        }
        return remove;
    }

    boolean exists(String url) {
        return !tweetSearch.findByUrl(url).isEmpty();
    }

    @Override
    public JTweet execute(JTweet tweet) {
        queueObject(tweet);
        return tweet;
    }
}
