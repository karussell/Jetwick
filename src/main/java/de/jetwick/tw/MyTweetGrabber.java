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
import de.jetwick.solr.SolrTweet;
import de.jetwick.tw.queue.TweetPackage;
import de.jetwick.tw.queue.TweetPackageTwQuery;
import de.jetwick.tw.queue.TweetPackageUserQuery;
import de.jetwick.tw.queue.TweetPackageArchiving;
import de.jetwick.tw.queue.TweetPackageList;
import java.io.Serializable;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyTweetGrabber implements Serializable {

    public static AtomicInteger idCounter = new AtomicInteger(0);
    private static final Logger logger = LoggerFactory.getLogger(MyTweetGrabber.class);
    private TweetPackage twPackage;
    private String userName;
    private String queryStr;
    private TwitterSearch tweetSearch;
    private Provider<RMIClient> rmiClient;
    private int tweetCount;
    private Exception exception;
    private int progress;
    @Inject
    private TweetPackageList tweetPackageList;
    @Inject
    private TweetPackageUserQuery tweetPackageUser;
    @Inject
    private TweetPackageTwQuery tweetPackageQuery;
    @Inject
    private TweetPackageArchiving tweetPackageArchiving;

    public MyTweetGrabber() {
    }

    public MyTweetGrabber init(String userName) {
        init(null, userName, null);
        return this;
    }

    public MyTweetGrabber init(Collection<SolrTweet> tweets, String userName, String query) {
        if (tweets != null) {
            this.twPackage = tweetPackageList.init(idCounter.addAndGet(1), tweets);
        }
        this.userName = userName;
        this.queryStr = query;
        return this;
    }

//    public int getTweetCount() {
//        return tweetCount;
//    }
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

    public TweetPackage queueTweetPackage() {
        int rl = tweetSearch.getRateLimit();
        if (rl <= TwitterSearch.LIMIT) {
            logger.warn("Couldn't process query (TwitterSearch+Index). Rate limit is smaller than " + TwitterSearch.LIMIT + ":" + rl);
            return twPackage;
        }
        if (twPackage == null) {
            if (userName != null && !userName.isEmpty()) {
                twPackage = tweetPackageUser.init(idCounter.addAndGet(1),
                        userName, tweetSearch.getCredits(), tweetCount);
            } else if (queryStr != null && !queryStr.isEmpty()) {
                twPackage = tweetPackageQuery.init(idCounter.addAndGet(1),
                        queryStr, tweetSearch.getCredits(), tweetCount);
            }
        }
        try {
            if (twPackage != null)
                rmiClient.get().init().send(twPackage);
        } catch (Exception ex) {
            logger.warn("Error while sending " + twPackage.getMaxTweets()
                    + " tweets to queue server:" + ex.toString());
        }
        return twPackage;
    }

    public TweetPackageArchiving queueArchiving() {
        TweetPackageArchiving tmp = tweetPackageArchiving.init(idCounter.addAndGet(1), userName, tweetCount,
                tweetSearch.getCredits());
        try {
            rmiClient.get().init().send(tmp);
        } catch (Exception ex) {
            logger.warn("Error while sending " + tmp.getMaxTweets()
                    + " tweets to queue server:" + ex.toString());
        }
        return tmp;
    }
//    public void setProgress(int i) {
//        progress = i;
//    }
}
