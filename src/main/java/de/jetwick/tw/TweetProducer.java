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

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;
import com.wideplay.warp.persist.WorkManager;
import de.jetwick.data.TagDao;
import de.jetwick.data.YTag;
import de.jetwick.util.Helper;
import de.jetwick.util.StopWatch;
import de.jetwick.data.UrlEntry;
import java.util.Collection;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.hibernate.StaleObjectStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.Tweet;
import twitter4j.TwitterException;

/**
 * fills the tweets queue via twitter search (does not cost API calls)
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetProducer extends MyThread {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Lock lock = new ReentrantLock();
    private StopWatch swSearch = new StopWatch("search");
    private StopWatch swUrl = new StopWatch("url   ");
    private final Condition condition = lock.newCondition();
    @Inject
    private TagDao tagDao;
    private Queue<Tweet> tweets = new LinkedBlockingDeque<Tweet>();
    private PriorityQueue<YTag> tags = new PriorityQueue<YTag>();
    private TwitterSearch twSearch;
    private int maxTime = -1;
    @Inject
    private WorkManager manager;
    private int maxFill = 2000;
    private int resolveThreads = 50;
    private boolean resolveUrls = false;
    private boolean addTrends = true;
    private int resolveTimeout = 500;
    private AtomicLong time;
    private UrlTitleCleaner skipUrlTitleList = new UrlTitleCleaner();

    public TweetProducer() {
        super("tweet-producer");
    }

    public Queue<Tweet> getTweets() {
        return tweets;
    }

    public void setResolveUrls(boolean resolveUrls) {
        this.resolveUrls = resolveUrls;
    }

    public void setResolveThreads(int resolveThreads) {
        this.resolveThreads = resolveThreads;
    }

    public void setResolveTimeout(int resolveTimeout) {
        this.resolveTimeout = resolveTimeout;
    }

    public void setUrlTitleCleaner(UrlTitleCleaner skipUrlTitleList) {
        this.skipUrlTitleList = skipUrlTitleList;
    }

    public UrlExtractor createExtractor() {
        return new UrlExtractor().setResolveTimeout(resolveTimeout);
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        logger.info("max tweets to search:" + maxFill + ". skip " + skipUrlTitleList.size() + " url titles");

        // to resolve even urls which requires cookies
        Helper.enableCookieMgmt();
        // to get the same title as a normal browser -> see Helper resolve method
        Helper.enableUserAgentOverwrite();

        long findNewTagsTime = -1;
        manager.beginWork();
        try {
            MAIN:
            while (!isInterrupted()
                    && (maxTime < 0 || (System.currentTimeMillis() - start) < maxTime)) {

                if (tags.isEmpty()) {
                    initTags();
                    if (tags.size() == 0) {
                        logger.warn("No tags found in db! Exit");
                        break;
                    }

                    if (findNewTagsTime > 0 && System.currentTimeMillis() - findNewTagsTime < 2000) {
                        int sec = Math.max(2, (int) tags.peek().getWaitingSeconds() + 1);
                        logger.info("nothing to do (too many pausing tags). wait " + sec + " seconds ");
                        myWait(sec);
                    }

                    findNewTagsTime = System.currentTimeMillis();

//                    Collection<String> trends = twSearch.getTrends();
                    // sometimes add trends (not too often to avoid too much noise)
//                    if (addTrends && rand.nextInt(4) < 3) {
//                        logger.info("add trends:" + trends);
//                        for (String t : trends) {
//                            tags.add(new YTag(t).setTransient(true));
//                        }
//                    }
                }

                YTag tag = tags.poll();
                if (tag != null && tag.nextQuery()) {
                    // do not add more tweets to the pipe if consumer cannot process it                    
                    while (tweets.size() > maxFill) {
                        logger.info("... WAITING! " + tweets.size() + " are too many tweets in the pipe!");
                        if (!myWait(20))
                            break MAIN;
                    }

                    LinkedBlockingDeque<Tweet> tmp = new LinkedBlockingDeque<Tweet>();
                    int waitInSeconds = 1;
                    try {
                        int hits = 0;
                        swSearch.start();
                        hits = twSearch.search(tag, tmp, tag.getPages());
                        swSearch.stop();
                        logger.info(swSearch + " \tqueue= " + tweets.size() + " \t + "
                                + tmp.size() + " \t q=" + tag.getTerm() + " pages=" + tag.getPages());

                        if (resolveUrls) {
                            swUrl.start();
                            resolveUrls(tmp, tweets, resolveThreads);
                            swUrl.stop();
//                            logger.info(swUrl + " all:" + allUrls + "\t accepted:" + acceptedUrls + " expanded:" + expandedUrls);
                        } else
                            tweets.addAll(tmp);

                        if (!tag.isTransient()) {
                            // TODO save only if storing to solr was successful
                            updateTagInTA(tag, hits);
                        }
                    } catch (TwitterException ex) {
                        logger.warn("Couldn't finish search for tag " + tag, ex);
                        if (ex.exceededRateLimitation())
                            waitInSeconds = ex.getRetryAfter();
                    } catch (StaleObjectStateException ex) {
                        initTags();
                    }

                    if (!myWait(waitInSeconds))
                        break;
                }
//                else logger.info("ignored " + tag.getTerm() + " \t try again in:" + tag.getWaitingSeconds());
            }
        } finally {
            manager.endWork();
        }

        logger.info(getName() + " successfully finished");
    }

    public Condition getCondition() {
        return condition;
    }

    public Lock getLock() {
        return lock;
    }

    public void setMaxFill(int maxFill) {
        this.maxFill = maxFill;
    }

    public void setTwitterSearch(TwitterSearch tws) {
        this.twSearch = tws;
    }

    public void setMaxTime(int maxTimeInSeconds) {
        this.maxTime = maxTimeInSeconds * 1000;
    }

    @Transactional
    public void updateTagInTA(YTag tag, int hits) {
        tag.optimizeQueryFrequency(hits);
        tagDao.save(tag);
    }

    /**
     * Resolve the detected urls for the specified tweets.
     * @param tweets where the tweets come from
     * @param outTweets where the new tweets (with the new text) will be saved
     * @param threadCount how many threads to use
     */
    public void resolveUrls(final Queue<? extends Tweet> tweets, final Collection<Tweet> outTweets, int threadCount) {
//        time = new AtomicLong(0);
//        StopWatch sw = new StopWatch("realtime");
//        sw.start();
        int maxThreads = Math.min(threadCount, tweets.size() / 5 + 1);
        Thread[] threads = new Thread[maxThreads];
        for (int i = 0; i < maxThreads; i++) {
            threads[i] = new Thread("url-res-" + i) {

                @Override
                public void run() {
                    while (true) {
                        Tweet tmpTw = tweets.poll();
                        if (tmpTw == null)
                            break;

                        Twitter4JTweet tw = new Twitter4JTweet(tmpTw);
                        UrlExtractor extractor = createExtractor();
                        for (UrlEntry ue : extractor.setText(tw.getText()).run().getUrlEntries()) {
                            if (Helper.trimNL(Helper.trimAll(ue.getResolvedTitle())).isEmpty())
                                continue;

                            if (!skipUrlTitleList.contains(ue.getResolvedTitle()))
                                tw.addUrlEntry(ue);
                        }
//                        time.addAndGet(extractor.getTime());
                        outTweets.add(tw);
                    }
                }
            };
            threads[i].start();
        }

        boolean interruptAll = false;
        for (int i = 0; i < threads.length; i++) {
            if (interruptAll) {
                threads[i].interrupt();
                continue;
            }
            try {
                threads[i].join();
            } catch (InterruptedException ex) {
                interruptAll = true;
            }
        }
//        sw.stop();
//        System.out.println("get title time:" + time.get() / 1000f / outTweets.size() + " real time:" + sw.getTime() / 1000f / outTweets.size());
    }

    private void initTags() {
        tags = new PriorityQueue<YTag>(tagDao.findAllSorted());
        logger.info("Using " + tags.size() + " tags. first tag is: " + tags.peek());
    }
}
