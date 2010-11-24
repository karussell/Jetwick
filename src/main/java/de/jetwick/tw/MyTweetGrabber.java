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
import de.jetwick.solr.SolrUser;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.Tweet;
import twitter4j.TwitterException;

public class MyTweetGrabber implements Serializable {

    private static final Logger logger = LoggerFactory.getLogger(MyTweetGrabber.class);
    private Collection<? extends Tweet> tmpTweets;
    private String userName;
    private String queryStr;
    private TwitterSearch tweetSearch;
    private Provider<RMIClient> rmiClient;
    private int tweetCount;
    private Exception exception;
    private int progress;

    public MyTweetGrabber(String userName) {
        this(null, userName, null);
    }

    public MyTweetGrabber(Collection<? extends Tweet> tweets, String userName, String query) {
        this.tmpTweets = tweets;
        this.userName = userName;
        this.queryStr = query;
    }

    public int getTweetCount() {
        return tweetCount;
    }

    public int getProgress() {
        return progress;
    }

    public Exception getException() {
        return exception;
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

    public Thread createQueueThread() {
        return new Thread("grabber-queue") {

            @Override
            public void run() {
                int rl = tweetSearch.getRateLimit();
                if (rl <= TwitterSearch.LIMIT) {
                    logger.warn("Couldn't process query (TwitterSearch+Index). Rate limit is smaller than " + TwitterSearch.LIMIT + ":" + rl);
                    return;
                }
                if (tmpTweets == null) {
                    tmpTweets = new LinkedHashSet<Tweet>();
                    if (userName != null && !userName.isEmpty()) {
                        try {
                            logger.info("add tweets via user search: " + userName);
                            tmpTweets = tweetSearch.getTweets(userName, new ArrayList<SolrUser>(), tweetCount);
                        } catch (TwitterException ex) {
                            exception = ex;
                            logger.warn("Couldn't update user: " + userName + " " + ex.getLocalizedMessage());
                        }
                    } else if (queryStr != null && !queryStr.isEmpty()) {
                        try {
                            logger.info("add tweets via twitter search: " + queryStr);
                            tmpTweets = tweetSearch.searchTweets(queryStr, tweetCount, 1);
                        } catch (TwitterException ex) {
                            exception = ex;
                            logger.warn("Couldn't update user: " + queryStr + " " + ex.getLocalizedMessage());
                        }
                    }
                }
                try {
                    if (tmpTweets.size() > 0)
                        rmiClient.get().init().send(tmpTweets);
                } catch (Exception ex) {
                    logger.warn("Error while sending " + tmpTweets.size() + " tweets to queue server:" + ex.toString());
                }
            }
        };
    }

    public Thread createArchivingThread() {
        return new Thread("grabber-archiver") {

            @Override
            public void run() {
                try {
                    logger.info("archivize tweets for: " + userName);
                    RMIClient client = rmiClient.get().init();
                    int maxTweets = tweetCount;
                    tweetCount = 0;
                    int rows = 100;
                    progress = 0;
                    for (int start = 0; start < maxTweets && !isInterrupted(); start += rows) {
                        Collection<? extends Tweet> tmp = tweetSearch.getTweets(userName, start, rows);
                        if (tmp.size() == 0)
                            continue;
                        try {
                            tweetCount += tmp.size();
                            client.send(tmp);
                            logger.info("sent tweets " + tweetCount + " to index queue");
                            progress = (int) (tweetCount * 100.0 / maxTweets);
                        } catch (Exception ex) {
                            logger.warn("Error for tweets [" + start + "," + (start + 100)
                                    + "] sending to index queue:" + ex.toString());
                        }
                    }
                } catch (TwitterException ex) {
                    exception = ex;
                    logger.warn("Couldn't get all tweets for user: " + userName + " " + ex.getLocalizedMessage());                    
                } catch (Exception ex) {
                    exception = ex;
                    logger.error("Couldn't init rmi server? " + ex.getLocalizedMessage());                    
                }
                progress = 100;
            }
        };
    }

    public void setProgress(int i) {
        progress = i;
    }
}
