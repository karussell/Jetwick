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

import com.google.inject.Provider;
import de.jetwick.rmi.RMIClient;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
import de.jetwick.tw.queue.QueueThread;
import de.jetwick.tw.queue.TweetPackageList;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterException;

public class MyTweetGrabber implements Serializable {

    public static AtomicInteger idCounter = new AtomicInteger(0);
    private static final Logger logger = LoggerFactory.getLogger(MyTweetGrabber.class);
    private String userName;
    private String queryStr;
    private TwitterSearch tweetSearch;
    private Provider<RMIClient> rmiClient;
    private int tweetCount;
    private Collection<SolrTweet> tweets;

    public MyTweetGrabber() {
    }

    public MyTweetGrabber init(String userName) {
        init(null, userName, null);
        return this;
    }

    public MyTweetGrabber init(Collection<SolrTweet> tweets, String userName, String query) {
        this.tweets = tweets;
        this.userName = userName;
        this.queryStr = query;
        return this;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserName() {
        return userName;
    }

    public MyTweetGrabber setTweetSearch(TwitterSearch ts) {
        this.tweetSearch = ts;
        return this;
    }

    public MyTweetGrabber setRmiClient(Provider<RMIClient> rmiClient) {
        this.rmiClient = rmiClient;
        return this;
    }

    public MyTweetGrabber setTweetsCount(int count) {
        tweetCount = count;
        return this;
    }

    public QueueThread queueTweetPackage() {
        int rl = tweetSearch.getRateLimit();
        if (rl <= TwitterSearch.LIMIT)
            return new QueueThread().doAbort(
                    new RuntimeException("Couldn't process query (TwitterSearch+Index)."
                    + " Rate limit is smaller than " + TwitterSearch.LIMIT + ":" + rl));

        return new QueueThread() {

            @Override
            public void run() {
                if (tweets == null) {
                    tweets = new LinkedBlockingQueue<SolrTweet>();
                    if (userName != null && !userName.isEmpty()) {
                        try {
                            tweets.addAll(tweetSearch.getTweets(new SolrUser(userName), new ArrayList<SolrUser>(), tweetCount));
                            logger.info("add tweets from user search: " + userName);
                        } catch (TwitterException ex) {
                            doAbort(ex);
                            logger.warn("Couldn't update user: " + userName + " " + ex.getLocalizedMessage());
                        }
                    } else if (queryStr != null && !queryStr.isEmpty()) {
                        try {
                            tweetSearch.search(queryStr, tweets, tweetCount, 0);
                            logger.info("added tweets via twitter search: " + queryStr);
                        } catch (TwitterException ex) {
                            doAbort(ex);
                            logger.warn("Couldn't query twitter: " + queryStr + " " + ex.getLocalizedMessage());
                        }
                    }
                }

                try {
                    if (tweets != null && tweets.size() > 0)
                        rmiClient.get().init().send(new TweetPackageList().init(idCounter.addAndGet(1), tweets));
                } catch (Exception ex) {
                    logger.warn("Error while sending " + tweets.size()
                            + " tweets to queue server", ex);
                }
            }
        };
    }

    public QueueThread queueArchiving() {
        return new QueueThread() {

            @Override
            public void run() {
                try {
                    SolrUser user = new SolrUser(userName);
                    int maxTweets = tweetCount;
                    tweetCount = 0;
                    int rows = 100;
                    setProgress(0);
                    logger.info("start archiving!");
                    for (int start = 0; start < maxTweets && !isCanceled(); start += rows) {
                        Collection<SolrTweet> tmp = tweetSearch.getTweets(user, start, rows);
                        if (tmp.size() == 0)
                            continue;
                        try {
                            tweetCount += tmp.size();
                            for (SolrTweet tw : tmp) {
                                tw.setUpdatedAt(new Date());
                            }

                            TweetPackageList pkg = new TweetPackageList().init(idCounter.addAndGet(1), tmp);
                            rmiClient.get().init().send(pkg);
                            logger.info("queue tweets " + tweetCount + " to index queue");
                            setProgress((int) (tweetCount * 100.0 / maxTweets));
                        } catch (Exception ex) {
                            logger.warn("Error for tweets [" + start + "," + (start + 100)
                                    + "] sending to index queue:", ex);
                        }
                    }
                    logger.info("grabbed tweets for: " + userName);
//                     doRetrieved();
                } catch (TwitterException ex) {
                    doAbort(ex);
                    logger.warn("Couldn't get all tweets for user: " + userName + " " + ex.getLocalizedMessage());
                } catch (Exception ex) {
                    doAbort(ex);
                    logger.error("Couldn't init rmi server? " + ex.getLocalizedMessage());
                }
            }
        };
    }

    public int getTweetCount() {
        return tweetCount;
    }
}
