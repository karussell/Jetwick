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
import de.jetwick.config.Configuration;
import de.jetwick.data.UrlEntry;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrTweetSearch;
import de.jetwick.tw.queue.TweetPackage;
import de.jetwick.util.Helper;
import de.jetwick.util.MyDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class AbstractTweetConsumer extends MyThread {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected TweetProducer producer;
    @Inject
    protected SolrTweetSearch tweetSearch;
    //protected StringCleaner tweetCleaner;
    private int removeDays = 8;
    private int resolveThreads = 50;
    private boolean resolveUrls = false;
    private int resolveTimeout = 500;
    private UrlTitleCleaner skipUrlTitleList = new UrlTitleCleaner();

    public AbstractTweetConsumer(String name, Configuration cfg) {
        super(name);
        //tweetCleaner = new StringCleaner(cfg.getUserBlacklist());
        logger.info("skip " + skipUrlTitleList.size() + " url titles");
    }

    public Collection<SolrTweet> updateTweets(BlockingQueue<TweetPackage> tws, int batch) {
        Set<SolrTweet> tweetSet = new LinkedHashSet<SolrTweet>();
        Collection<TweetPackage> donePackages = new ArrayList<TweetPackage>();
        while (true) {
            TweetPackage tw = tws.poll();
            if (tw == null)
                break;

            BlockingQueue<SolrTweet> tmpTweets = new LinkedBlockingQueue<SolrTweet>();
            tw.retrieveTweets(tmpTweets);
            donePackages.add(tw);
            tweetSet.addAll(tmpTweets);
            if (tweetSet.size() > batch)
                break;
        }

        try {
            // BlockingQueue_s are thread safe
            BlockingQueue<SolrTweet> outTweets = new LinkedBlockingQueue<SolrTweet>();
            if (resolveUrls) {
                BlockingQueue<SolrTweet> blockQueue2 = new LinkedBlockingQueue<SolrTweet>();
                blockQueue2.addAll(tweetSet);
                resolveUrls(blockQueue2, outTweets, resolveThreads);
            } else
                outTweets.addAll(tweetSet);

//            return updateDbTweetsInTA(tmpTweets);
            Collection<SolrTweet> res = tweetSearch.update(outTweets, new MyDate().minusDays(removeDays).toDate());
            for (TweetPackage pkg : donePackages) {
                logger.info("indexed: " + pkg);
//                pkg.finish();
            }
            return res;
        } catch (Exception ex) {
            logger.error("Couldn't update " + tweetSet.size() + " tweets.", ex);
//            for (Tweet tw : tmpTweets) {
//                String str = "";
//                if (tw instanceof Twitter4JTweet)
//                    str = " irosi:" + ((Twitter4JTweet) tw).getInReplyToStatusId() + " ";
//
//                System.out.println(tw.getId() + " " + tw.getCreatedAt() + " " + tw.getFromUser() + " " + str + tw.getText());
//            }
            return Collections.EMPTY_LIST;
        }
    }

//    private StopWatch sw5 = new StopWatch(" 5");
//
//    @Transactional
//    private UpdateResult updateDbTweetsInTA(Collection<Tweet> tws) {
//        UpdateResult res = new UpdateResult();
//        for (Tweet tw : tws) {
//            UpdateResult tmp = dbHelper.update(tw);
//            res.addAll(tmp);
//        }
//
//        return res;
//    }
    public void setTweetProducer(TweetProducer produceTweetsThread) {
        this.producer = produceTweetsThread;
    }

    public void setTweetSearch(SolrTweetSearch tweetSearch) {
        this.tweetSearch = tweetSearch;
    }

    public void setRemoveDays(int solrRemoveDays) {
        removeDays = solrRemoveDays;
    }

    /**
     * Resolve the detected urls for the specified tweets.
     * @param tweets where the tweets come from
     * @param outTweets where the new tweets (with the new text) will be saved
     * @param threadCount how many threads to use
     */
    public void resolveUrls(final BlockingQueue<SolrTweet> tweets,
            final BlockingQueue<SolrTweet> outTweets, int threadCount) {
        int maxThreads = Math.min(threadCount, tweets.size() / 5 + 1);
        Thread[] threads = new Thread[maxThreads];
        for (int i = 0; i < maxThreads; i++) {
            threads[i] = new Thread("url-res-" + i) {

                @Override
                public void run() {
                    while (true) {
                        SolrTweet tmpTw = tweets.poll();
                        if (tmpTw == null)
                            break;

                        //Twitter4JTweet tw = new Twitter4JTweet(tmpTw);
                        UrlExtractor extractor = createExtractor();
                        for (UrlEntry ue : extractor.setText(tmpTw.getText()).run().getUrlEntries()) {
                            if (Helper.trimNL(Helper.trimAll(ue.getResolvedTitle())).isEmpty())
                                continue;

                            if (!skipUrlTitleList.contains(ue.getResolvedTitle()))
                                tmpTw.addUrlEntry(ue);
                        }
                        outTweets.add(tmpTw);
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

    public void setResolveUrls(boolean resolveUrls) {
        this.resolveUrls = resolveUrls;
    }

    public void setResolveThreads(int resolveThreads) {
        this.resolveThreads = resolveThreads;
    }   
}
