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

import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
import de.jetwick.tw.queue.TweetPackageList;
import de.jetwick.util.StopWatch;
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
    private Map<String, UserEntry> userMap = new LinkedHashMap<String, UserEntry>();

    public TweetProducerFromFriends() {
        setName("friend-tweet-producer");
    }

    public void run(int count) {
        for (int i = 0; i < count; i++) {
            innerRun();
        }
    }

    @Override
    public void run() {
        logger.info("Started " + getName());
        while (!isInterrupted()) {
            if (!innerRun())
                break;
        }
        logger.info("Finished " + getName());
    }

    public boolean innerRun() {
        Set<SolrUser> users = new LinkedHashSet<SolrUser>();
        long counts = userSearch.countAll();
        int ROWS = 100;
        // paging
        for (int i = 0; i < counts / ROWS + 1; i++) {
            // prefer last logged in users
            SolrQuery q = new SolrQuery().setStart(i * ROWS).setRows(ROWS).
                    setSortField(ElasticUserSearch.CREATED_AT, SolrQuery.ORDER.desc);
            try {
                userSearch.search(users, q);
            } catch (SolrServerException ex) {
                logger.error("Couldn't search user index", ex);
            }
        }
        
        logger.info("Found:" + users.size() + " users in user index");
        for (SolrUser u : users) {
            if (!isValidUser(u))
                continue;

            UserEntry ue = userMap.get(u.getScreenName());
            if (ue == null) {
                ue = new UserEntry(u);
                userMap.put(u.getScreenName(), ue);
            }

            if (ue.getTwitterSearch() == null) {
                try {
                    ue.setTwitterSearch(createTwitter4J(u.getTwitterToken(),
                            u.getTwitterTokenSecret()));
                } catch (Exception ex) {
                    logger.error("Skipping user:" + u.getScreenName() + " token:"
                            + u.getTwitterToken() + " Error:" + getErrorMsg(ex));
                    continue;
                }
            }

//            try {
//                logger.info("Now User:" + ue.getTwitterSearch().getScreenName());
//            } catch (Exception ex) {
//                logger.error("Error when getting user", ex);
//                continue;
//            }

            if (ue.getTwitterSearch().getRateLimit() < TwitterSearch.LIMIT) {
                logger.info("No API points left for user:" + ue.getUser() + " " + ue.getTwitterSearch().getSecondsUntilReset());
                continue;
            }

            // regularly check if friends of user u were changed
            try {
                logger.info("Grabbing friends of " + ue.getTwitterSearch().getScreenName() + " (" + ue.getUser().getScreenName() + ")");
                new FriendSearchHelper(userSearch, ue.getTwitterSearch()).updateFriendsOf(u).size();
            } catch (Exception ex) {
                logger.error("Exception when getting friends from " + u.getScreenName()
                        + " Error:" + getErrorMsg(ex));
            }

            // regularly feed tweets of friends from user u            
            try {
                StopWatch watch = new StopWatch("friends").start();
                Set<SolrTweet> tweets = new LinkedHashSet<SolrTweet>();
                ue.setLastId(ue.getTwitterSearch().getHomeTimeline(tweets, 99, ue.getLastId()));
                if (tweets.size() > 0) {
                    tweetPackages.add(new TweetPackageList("friendsOf:" + u.getScreenName()).init(MyTweetGrabber.idCounter.addAndGet(1), tweets));
                    logger.info("Pushed " + tweets.size() + " friend tweets of " + u.getScreenName()
                            + " into queue. Last id " + ue.getLastId() + ". " + watch.stop());
                }
            } catch (Exception ex) {
                logger.error("Exception while retrieving friend tweets of "
                        + u.getScreenName() + " Error:" + getErrorMsg(ex));
            }

            // do not hit twitter too much
            myWait(2);
        }

        myWait(10);
        return true;
    }

    String getErrorMsg(Throwable e) {
        return e.getMessage().replaceAll("\\n", " ");
    }

    protected TwitterSearch createTwitter4J(String twitterToken, String twitterTokenSecret) {
        return new TwitterSearch().setConsumer(twSearch.getConsumerKey(), twSearch.getConsumerSecret()).
                initTwitter4JInstance(twitterToken, twitterTokenSecret);
    }

    protected boolean isValidUser(SolrUser u) {
        if (u.getTwitterToken() == null || u.getTwitterTokenSecret() == null) {
            logger.warn("Skipped user:" + u.getScreenName() + " - no token or secret!");
            return false;
        }
        return true;
    }

    private static class UserEntry {

        private SolrUser u;
        private TwitterSearch ts;
        private long lastId = 0L;

        public UserEntry(SolrUser u) {
            this.u = u;
        }

        public long getLastId() {
            return lastId;
        }

        public void setLastId(long lastId) {
            this.lastId = lastId;
        }

        public void setTwitterSearch(TwitterSearch ts) {
            this.ts = ts;
        }

        public TwitterSearch getTwitterSearch() {
            return ts;
        }

        public SolrUser getUser() {
            return u;
        }
    }
}
