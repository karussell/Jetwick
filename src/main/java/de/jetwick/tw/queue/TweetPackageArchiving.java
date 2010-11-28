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
package de.jetwick.tw.queue;

import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
import de.jetwick.tw.Credits;
import de.jetwick.tw.TwitterSearch;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterException;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class TweetPackageArchiving extends AbstractTweetPackage {

    private static final Logger logger = LoggerFactory.getLogger(TweetPackageArchiving.class);
    private String userName;
    private Credits credits;

    public TweetPackageArchiving init(int id, String user, int maxTweets, Credits credits) {
        super.init(id, maxTweets);
        this.userName = user;
        this.credits = credits;
        return this;
    }

    @Override
    public TweetPackage retrieveTweets(BlockingQueue<SolrTweet> result) {
        try {
            logger.info("archivize tweets for: " + userName);
            SolrUser user = new SolrUser(userName);
            int tweetCount = 0;
            int rows = 100;
            setProgress(0);
            TwitterSearch tweetSearch = getTwitterSearch(credits);
            for (int start = 0; start < getMaxTweets() && !isCanceled(); start += rows) {
                Collection<SolrTweet> tmp = tweetSearch.getTweets(user, start, rows);
                if (tmp.size() == 0)
                    continue;
                try {
                    tweetCount += tmp.size();
                    for (SolrTweet tw : tmp) {
                        tw.setUpdatedAt(new Date());
                        result.add(tw);
                    }
                    logger.info("queue tweets " + tweetCount + " to index queue");
                    setProgress((int) (tweetCount * 100.0 / getMaxTweets()));
                } catch (Exception ex) {
                    logger.warn("Error for tweets [" + start + "," + (start + 100)
                            + "] sending to index queue:" + ex.toString());
                }
            }
            doFinish();
        } catch (TwitterException ex) {
            doAbort(ex);
            logger.warn("Couldn't get all tweets for user: " + userName + " " + ex.getLocalizedMessage());
        } catch (Exception ex) {
            doAbort(ex);
            logger.error("Couldn't init rmi server? " + ex.getLocalizedMessage());
        }
        return this;
    }

    public String getUserName() {
        return userName;
    }
}
