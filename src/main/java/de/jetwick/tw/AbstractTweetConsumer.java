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
import de.jetwick.util.Helper;
import de.jetwick.util.MyDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.Tweet;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class AbstractTweetConsumer extends MyThread {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected TweetProducer producer;
    @Inject
    protected SolrTweetSearch tweetSearch;
    protected StringCleaner tweetCleaner;
    private int removeDays = 8;
    private int resolveThreads = 50;
    private boolean resolveUrls = false;
    private int resolveTimeout = 500;
    private UrlTitleCleaner skipUrlTitleList = new UrlTitleCleaner();

    public AbstractTweetConsumer(String name, Configuration cfg) {
        super(name);
        tweetCleaner = new StringCleaner(cfg.getUserBlacklist());
        logger.info("skip " + skipUrlTitleList.size() + " url titles");
    }

    public Collection<SolrTweet> updateDbTweets(Queue<Tweet> tws, int batch) {
        BlockingQueue<Tweet> tmpTweets = new LinkedBlockingQueue<Tweet>(batch);
        for (int i = 0; i < batch; i++) {
            Tweet tw = tws.poll();
            if (tw == null)
                break;

            if (tweetCleaner.contains(tw.getFromUser()))
                continue;

            tmpTweets.add(tw);
        }

        BlockingQueue<Tweet> outTweets = new LinkedBlockingQueue<Tweet>(batch);
        if (resolveUrls) {
            resolveUrls(tmpTweets, outTweets, resolveThreads);
        } else
            outTweets.addAll(tmpTweets);

//        return updateDbTweetsInTA(tmpTweets);
        try {
            return tweetSearch.update(outTweets, new MyDate().minusDays(removeDays).toDate());
        } catch (Exception ex) {
            logger.error("Cannot update " + tmpTweets.size() + " tweets.", ex);
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
    public void resolveUrls(final BlockingQueue<? extends Tweet> tweets,
            final BlockingQueue<Tweet> outTweets, int threadCount) {
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
