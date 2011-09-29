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

import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
import de.jetwick.util.StopWatch;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
        int counter = 0;
        Set<JUser> users = new LinkedHashSet<JUser>();
        // TODO count users with token!
        long counts = userSearch.countAll();
        int ROWS = 100;
        // paging
        for (int i = 0; i < counts / ROWS + 1; i++) {
            try {
                userSearch.searchLastLoggedIn(users, i * ROWS, ROWS);
            } catch (Exception ex) {
                logger.error("Couldn't search user index: " + ex.getMessage());
            }
        }

        logger.info("Found:" + users.size() + " users in user index");
        List<JUser> scheduleForDelete = new ArrayList();
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
//                    logger.info("name:\t" + authUser.getScreenName());
//                    logger.info("token:\t" + authUser.getTwitterToken());
//                    logger.info("token_secret:\t" + authUser.getTwitterTokenSecret());                    
//                    logger.info("consumer_key:\t" + twSearch.getConsumerKey());
//                    logger.info("consumer_secret:\t" + twSearch.getConsumerSecret());                    

                    ue.setTwitterSearch(createTwitter4J(authUser.getTwitterToken(),
                            authUser.getTwitterTokenSecret()));
                } catch (Exception ex2) {
                    ex = ex2;
                }
                if (ue.getTwitterSearch() == null) {
                    String str = getErrorMsg(ex);
                    if (str.contains("401:Authentication credentials")) {
                        authUser.setActive(false);
                        scheduleForDelete.add(authUser);
                    }

                    logger.error("Skipping user:" + authUser.getScreenName()
                            + " token: " + authUser.getTwitterToken()
                            + " active: " + authUser.isActive()
                            + " error: " + str);
                    continue;
                }
            }

            // getRateLimit is too slow -> use cached most of the times
            int rl = 0;
            if (counter++ % 20 == 0)
                rl = ue.getTwitterSearch().getRateLimit();
            else
                rl = ue.getTwitterSearch().getRateLimitFromCache();

            if (rl < TwitterSearch.LIMIT) {
                logger.info("No API points left for user:" + ue.getUser() + " " + ue.getTwitterSearch().getSecondsUntilReset());
                continue;
            }

            int friends = -1;
            // regularly check if friends of authenticated user were changed
            try {
//                logger.info("Grabbing friends of " + ue.getTwitterSearch().getScreenName() + " (" + ue.getUser().getScreenName() + ")");
                friends = new FriendSearchHelper(userSearch, ue.getTwitterSearch()).updateFriendsOf(authUser).size();
            } catch (Exception ex) {
                logger.error("Problem when getting friends from " + authUser.getScreenName()
                        + " Error:" + getErrorMsg(ex));
                continue;
            }

            // regularly feed tweets of friends from authenticated user           
            try {
                StopWatch watch = new StopWatch("friends").start();
                Set<JTweet> tweets = new LinkedHashSet<JTweet>();

                // reduce maximal tweets per search when user has too many friends
                int maxTweets = 99;
                if (friends > 500)
                    maxTweets = 25;

                ue.setLastId(ue.getTwitterSearch().getHomeTimeline(tweets, maxTweets, ue.getLastId()));
                if (tweets.size() > 0) {
                    for (JTweet tw : tweets) {
                        if (tw.getFromUser().getScreenName().equalsIgnoreCase(authUser.getScreenName()))
                            tw.makePersistent();

                        // set to protected as we want to store only the article url (not the tweet!)
                        if (tw.getFromUser().isProtected())
                            tw.setProtected(true);

                        resultTweets.put(tw.setFeedSource("friendsOf:" + authUser.getScreenName()));
                    }
                    logger.info("Pushed " + tweets.size() + " friend tweets of " + authUser.getScreenName()
                            + " into queue. Last date " + new Date(ue.getLastId()) + ". " + watch.stop());
                }
            } catch (Exception ex) {
                logger.error("Exception while retrieving friend tweets of "
                        + authUser.getScreenName() + " Error:" + getErrorMsg(ex));
            }

            // do not hit twitter too much            
            myWait(2);
        } //  next user

        userSearch.update(scheduleForDelete);
        myWait(10);
        return true;
    } // next cycle

    String getErrorMsg(Throwable e) {
        if (e == null || e.getMessage() == null)
            return "<no error message>";

        return e.getMessage().replaceAll("\\n", " ");
    }

    protected TwitterSearch createTwitter4J(String twitterToken, String twitterTokenSecret) {
        return new TwitterSearch().setConsumer(twSearch.getConsumerKey(), twSearch.getConsumerSecret()).
                initTwitter4JInstance(twitterToken, twitterTokenSecret, true);
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
