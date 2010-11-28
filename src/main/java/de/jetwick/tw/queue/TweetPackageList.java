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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.concurrent.BlockingQueue;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class TweetPackageList extends AbstractTweetPackage {

    private Collection<SolrTweet> tweets;

    public TweetPackageList() {
    }

    public TweetPackageList init(int id, Collection<SolrTweet> tweets) {
        super.init(id, tweets == null ? 0 : tweets.size());
        if (tweets != null)
            this.tweets = new LinkedHashSet<SolrTweet>(tweets);

        return this;
    }

    @Override
    public TweetPackage retrieveTweets(BlockingQueue<SolrTweet> result) {
        if (tweets != null)
            result.addAll(tweets);
        return this;
    }
}
