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

import de.jetwick.es.ElasticUserSearch;
import de.jetwick.es.JetwickQuery;
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
import de.jetwick.es.UserQuery;
import de.jetwick.tw.queue.AbstractTweetPackage;
import de.jetwick.tw.queue.TweetPackageList;
import de.jetwick.util.StopWatch;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetProducerViaUsers extends TweetProducerViaSearch {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private Map<String, UserEntry> userMap = new LinkedHashMap<String, UserEntry>();

    public TweetProducerViaUsers() {
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
        Set<JUser> users = new LinkedHashSet<JUser>();
        long counts = userSearch.countAll();
        int ROWS = 100;
        // paging
        for (int i = 0; i < counts / ROWS + 1; i++) {
            // prefer last logged in users
            JetwickQuery q = new UserQuery().setFrom(i * ROWS).setSize(ROWS).
                    setSort(ElasticUserSearch.CREATED_AT, "desc");
            try {
                userSearch.search(users, q);
            } catch (Exception ex) {
                logger.error("Couldn't search user index", ex);
            }
        }
        
        // do not add more tweets to the pipe if consumer cannot process it
        int count = 0;
        while (true) {
            count = AbstractTweetPackage.calcNumberOfTweets(tweetPackages);
            if (count < maxFill)
                break;

            logger.info("... WAITING! " + count + " are too many tweets from friend-tweet search!");
            if (!myWait(20))
                return false;
        }

        logger.info("Found:" + users.size() + " users in user index");
        for (JUser authUser : users) {
            if (!isValidUser(authUser))
                continue;

            UserEntry ue = userMap.get(authUser.getScreenName());
            if (ue == null) {
                ue = new UserEntry(authUser);
                userMap.put(authUser.getScreenName(), ue);
            }

            if (ue.getTwitterSearch() == null) {
                Exception ex = null;
                try {
                    ue.setTwitterSearch(createTwitter4J(authUser.getTwitterToken(),
                            authUser.getTwitterTokenSecret()));
                } catch (Exception ex2) {
                    ex = ex2;
                }
                if (ue.getTwitterSearch() == null) {
                    logger.error("Skipping user:" + authUser.getScreenName() + " token:"
                            + authUser.getTwitterToken() + " Error:" + getErrorMsg(ex));
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

            // regularly check if friends of authenticated user were changed
            try {
                logger.info("Grabbing friends of " + ue.getTwitterSearch().getScreenName() + " (" + ue.getUser().getScreenName() + ")");
                new FriendSearchHelper(userSearch, ue.getTwitterSearch()).updateFriendsOf(authUser).size();
            } catch (Exception ex) {
                logger.error("Exception when getting friends from " + authUser.getScreenName()
                        + " Error:" + getErrorMsg(ex));
            }

            // regularly feed tweets of friends from authenticated user           
            try {
                StopWatch watch = new StopWatch("friends").start();
                Set<JTweet> tweets = new LinkedHashSet<JTweet>();
                ue.setLastId(ue.getTwitterSearch().getHomeTimeline(tweets, 99, ue.getLastId()));
                if (tweets.size() > 0) {
                    for (JTweet tw : tweets) {
                        if (tw.getFromUser().getScreenName().equalsIgnoreCase(authUser.getScreenName()))
                            tw.makePersistent();
                    }
                    tweetPackages.add(new TweetPackageList("friendsOf:" + authUser.getScreenName()).init(tweets));
                    logger.info("Pushed " + tweets.size() + " friend tweets of " + authUser.getScreenName()
                            + " into queue. Last id " + ue.getLastId() + ". " + watch.stop());
                }
            } catch (Exception ex) {
                logger.error("Exception while retrieving friend tweets of "
                        + authUser.getScreenName() + " Error:" + getErrorMsg(ex));
            }

            // do not hit twitter too much
            myWait(2);
        }

        myWait(10);
        return true;
    }

    String getErrorMsg(Throwable e) {
        if (e == null || e.getMessage() == null)
            return "<no error message>";

        return e.getMessage().replaceAll("\\n", " ");
    }

    protected TwitterSearch createTwitter4J(String twitterToken, String twitterTokenSecret) {
        return new TwitterSearch().setConsumer(twSearch.getConsumerKey(), twSearch.getConsumerSecret()).
                initTwitter4JInstance(twitterToken, twitterTokenSecret);
    }

    protected boolean isValidUser(JUser u) {
        if (u.getTwitterToken() == null || u.getTwitterTokenSecret() == null) {
            logger.warn("Skipped user:" + u.getScreenName() + " - no token or secret!");
            return false;
        }
        return true;
    }

    private static class UserEntry {

        private JUser u;
        private TwitterSearch ts;
        private long lastId = 0L;

        public UserEntry(JUser u) {
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

        public JUser getUser() {
            return u;
        }
    }
}
