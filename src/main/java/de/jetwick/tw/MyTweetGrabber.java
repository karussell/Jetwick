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
import com.google.inject.Provider;
import de.jetwick.rmi.RMIClient;
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
import de.jetwick.tw.queue.QueueThread;
import de.jetwick.util.MaxBoundSet;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterException;

public class MyTweetGrabber implements Serializable {

    private static final long serialVersionUID = 1L;
    private final Logger logger = LoggerFactory.getLogger(MyTweetGrabber.class);
    private String userName;
    private String queryStr;
    private TwitterSearch tweetSearch;
    @Inject
    private Provider<RMIClient> rmiClient;
    private int tweetCount;
    private Collection<JTweet> tweets;
    @Inject
    private Provider<MaxBoundSet> lastSearches;

    public MyTweetGrabber() {
    }

    public MyTweetGrabber init(Collection<JTweet> tweets, String query, String userName) {
        this.tweets = tweets;
        this.userName = userName;
        this.queryStr = query;
        return this;
    }

    public void setUserName(String userName) {
        this.userName = userName.toLowerCase();
    }

    public String getUserName() {
        return userName;
    }

    public MyTweetGrabber setTwitterSearch(TwitterSearch ts) {
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
        return new QueueThread() {

            @Override
            public void run() {
                if (!tweetSearch.isInitialized())
                    return;

                int rl = tweetSearch.getRateLimit();
                if (rl <= TwitterSearch.LIMIT) {
                    doAbort(new RuntimeException("Couldn't process query (TwitterSearch+Index)."
                            + " Rate limit is smaller than " + TwitterSearch.LIMIT + ":" + rl));
                    return;
                }

                String feedSource = "";
                if (tweets == null) {
                    if (userName != null && !userName.isEmpty()) {
                        // TODO exlude friendSearch
                        try {
                            if (!isSearchDoneInLastMinutes("user:" + userName)) {
//                                logger.info("lastsearches hashcode:" + lastSearches.hashCode());
                                tweets = new LinkedBlockingQueue<JTweet>();
                                feedSource = "grab user:" + userName;
                                tweets.addAll(tweetSearch.getTweets(new JUser(userName), new ArrayList<JUser>(), tweetCount));
                                logger.info("add " + tweets.size() + " tweets from user search: " + userName);
                            }
                        } catch (TwitterException ex) {
                            doAbort(ex);
                            logger.warn("Couldn't update user: " + userName + " " + ex.getLocalizedMessage());
                        }
                    } else if (queryStr != null && !queryStr.isEmpty()) {
                        try {
                            if (!isSearchDoneInLastMinutes(queryStr)) {
//                                logger.info("lastsearches hashcode:" + lastSearches.hashCode());
                                tweets = new LinkedBlockingQueue<JTweet>();
                                feedSource = "grab query:" + queryStr;
                                tweetSearch.search(queryStr, tweets, tweetCount, 0);
                                logger.info("added " + tweets.size() + " tweets via twitter search: " + queryStr);
                            }
                        } catch (TwitterException ex) {
                            doAbort(ex);
                            logger.warn("Couldn't query twitter: " + queryStr + " " + ex.getLocalizedMessage());
                        }
                    }
                } else
                    feedSource = "filledTweets:" + tweets.size();

                try {
                    if (tweets != null && tweets.size() > 0 && !feedSource.isEmpty()) {
                        for (JTweet tw : tweets) {
                            rmiClient.get().init().send(tw.setFeedSource(feedSource));
                        }
                    }
                } catch (Exception ex) {
                    logger.warn("Error while sending tweets to queue server" + ex.getMessage());
                }
            }
        };
    }

    public boolean isSearchDoneInLastMinutes(String string) {
        return !lastSearches.get().add(string.toLowerCase());
    }

    public QueueThread queueArchiving() {
        return new QueueThread() {

            @Override
            public void run() {
                try {
                    JUser user = new JUser(userName);
                    int maxTweets = tweetCount;
                    tweetCount = 0;
                    int rows = 100;
                    setProgress(0);
                    logger.info("start archiving!");
                    for (int start = 0; start < maxTweets && !isCanceled(); start += rows) {
                        Collection<JTweet> tmp = tweetSearch.getTweets(user, start, rows);
                        if (tmp.isEmpty())
                            continue;
                        try {
                            tweetCount += tmp.size();
                            for (JTweet tw : tmp) {
                                tw.makePersistent();
                            }

                            for (JTweet tw : tmp) {
                                rmiClient.get().init().send(tw.setFeedSource("archiving user:" + userName));
                            }
                            logger.info("queue tweets " + tweetCount + " to index queue");
                            setProgress((int) (tweetCount * 100.0 / maxTweets));
                        } catch (Exception ex) {
                            logger.warn("Error for tweets [" + start + "," + (start + 100)
                                    + "] sending to index queue:", ex);
                        }
                    }
                    logger.info("grabbed tweets for: " + userName);
                    doFinish();
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

    public MyTweetGrabber setMyBoundSet(final MaxBoundSet<String> boundSet) {
        lastSearches = new Provider<MaxBoundSet>() {

            @Override
            public MaxBoundSet get() {
                return boundSet;
            }
        };
        return this;
    }
}
