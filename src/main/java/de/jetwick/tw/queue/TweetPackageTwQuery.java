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
import de.jetwick.tw.Credits;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterException;

/**
 * bundle tweets via twitter query
 * 
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class TweetPackageTwQuery extends AbstractTweetPackage {

    private static final Logger logger = LoggerFactory.getLogger(TweetPackageTwQuery.class);
    private Credits credits;
    private String query;

    public TweetPackageTwQuery() {
    }

    public TweetPackageTwQuery init(int id, String query, Credits credits, int maxTweets) {
        super.init(id, maxTweets);
        this.credits = credits;
        this.query = query;
        return this;
    }

    @Override
    public TweetPackageTwQuery retrieveTweets(BlockingQueue<SolrTweet> res) {
        try {
            logger.info("add tweets via twitter search: " + query);
            getTwitterSearch(credits).search(query, res, getMaxTweets(), 0);
        } catch (TwitterException ex) {
            doAbort(ex);
            logger.warn("Couldn't query twitter: " + query + " " + ex.getLocalizedMessage());
        }
        return this;
    }
}
