/*
 * Copyright 2011 Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetwick.tw;

import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
import de.jetwick.tw.queue.TweetPackageList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetProducerFromFriends extends TweetProducerOnline {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    Map<String, TwitterSearch> userMap = new LinkedHashMap<String, TwitterSearch>();

    public void run(int count) {
        for (int i = 0; i < count; i++) {
            innerRun();
        }
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            innerRun();
        }
    }

    public void innerRun() {
        Set<SolrUser> users = new LinkedHashSet<SolrUser>();
        long counts = userSearch.countAll();
        int ROWS = 100;
        for (int i = 0; i < counts / ROWS + 1; i++) {
            SolrQuery q = new SolrQuery().setStart(i * ROWS).setRows((i + 1) * ROWS);
            try {
                userSearch.search(users, q);
            } catch (SolrServerException ex) {
                logger.error("Couldn't search user index", ex);
            }
        }

        for (SolrUser u : users) {
            TwitterSearch ts = userMap.get(u.getScreenName());
            if (ts == null) {
                ts = createTwitter4J(u.getTwitterToken(), u.getTwitterTokenSecret());
                userMap.put(u.getScreenName(), ts);
            }

            try {
                new FriendSearchHelper(userSearch, ts).getFriendsOf(u);
            } catch (Exception ex) {
                logger.error("Exception when getting friends from " + u.getScreenName(), ex);
            }

            try {
                Set<SolrTweet> tweets = new LinkedHashSet<SolrTweet>();
                ts.getHomeTimeline(tweets, 200, 0);
                tweetPackages.add(new TweetPackageList("friendsOf:" + u.getScreenName()).init(MyTweetGrabber.idCounter.addAndGet(1), tweets));
            } catch (Exception ex) {
                logger.error("Exception while retrieving from twitter friend tweets of " + u.getScreenName(), ex);
            }
        }
    }

    protected TwitterSearch createTwitter4J(String twitterToken, String twitterTokenSecret) {
        return new TwitterSearch().setTwitter4JInstance(twitterToken, twitterTokenSecret);
    }
}
