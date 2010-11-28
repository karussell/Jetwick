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
import com.wideplay.warp.persist.WorkManager;
import de.jetwick.config.Configuration;
import de.jetwick.data.UserDao;
import de.jetwick.data.YUser;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.Tweet;

/**
 * updates tweets and description from users (costs API calls)
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetUpdater extends AbstractTweetConsumer {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private TwitterSearch twSearch;
    @Inject
    private WorkManager manager;
    @Inject
    private UserDao uDao;
    private int firstRow = 0;
    private int usersPerUpdate = 30;
    private int waitForNextCall = 1 * 60;

    @Inject
    public TweetUpdater(Configuration cfg) {
        super("tweet-updater", cfg);
    }

    @Override
    public void run() {
        while (!isInterrupted() && producer.isAlive()) {
            manager.beginWork();
            Collection<SolrTweet> allTweets = null;
            try {
                allTweets = updateUsers();

                if (allTweets == null)
                    break;

                // after updating the tweets push to solr
                try {
                    tweetSearch.update(allTweets);
                    tweetSearch.commit();
                } catch (Exception ex) {
                    logger.error("solr indexing failed. tweets:" + allTweets.size(), ex);
                    try {
                        tweetSearch.rollback();
                    } catch (Exception ex2) {
                    }
                }
            } finally {
                manager.endWork();
            }
            if (!myWait(waitForNextCall))
                break;
        }
    }

    /**
     * Update info/tweets for users and update solr.     
     */
    public Collection<SolrTweet> updateUsers() {
        // TODO at the moment we ignore StaleObjectStateException!
        // problem is that if we are retring the session is closed for earlier objects
        // and we will get LazyInitException when indexing to solr (e.g. for getOwnTweets)!
        // i.ather words: probably for the objects in the list before the staleexception occured which do not have a session any longer

        int rl = twSearch.getRateLimit();
        if (rl <= 0) {
            logger.info("No API calls. Seconds until reset:" + twSearch.getSecondsUntilReset());
            return Collections.EMPTY_LIST;
        }

        List<YUser> todoUsers = getTodoUsers();
        if (todoUsers.size() == 0)
            return null;

        // get latest tweets for 30 users (30 * 12 = 360 API calls in 1h)
        Set<SolrTweet> newTweets = new LinkedHashSet<SolrTweet>();
        int noOfUsers = Math.min(rl, Math.min(todoUsers.size(), usersPerUpdate));
        List<YUser> selectedVIPUsers = todoUsers.subList(0, noOfUsers);
        int counter = 0;
        int exceptions = 0;
        for (YUser user : selectedVIPUsers) {
            counter++;
            String msg = "Couldn't update tweets of " + user.getScreenName()
                    + ". User " + counter + " of " + selectedVIPUsers.size() + " VIP users";
            try {
//                for (SolrTweet externTw : twSearch.getTweets(user.getScreenName())) {
//                    UpdateResult res = addTweet(externTw);
//                    newTweets.addAll(res.getUpdatedTweets());
//                }
                user.setUpdateAt(new Date());
                try {
                    uDao.save(user);
                    logger.info("Updated " + user.getScreenName() + " [" + counter + "]");
                } catch (Exception ex) {
                    // TODO
                    logger.error("Couldn't set updateAt " + user.getScreenName() + " [" + counter + "] because of "
                            + ex.getClass().getSimpleName() + " " + ex.getLocalizedMessage());
                }
//            } catch (TwitterException ex) {
//                logger.warn(msg, ex);
//                exceptions++;
//                if (ex.exceededRateLimitation()) {
//                    logger.warn("Skipping the rest of the users. Current user:" + counter
//                            + " rate limit exceeded?:" + ex.exceededRateLimitation()
//                            + " exceptions:" + exceptions);
//                    // TODO return ex.getRetryAfter();
//                    return Collections.EMPTY_LIST;
//                }
            } catch (Exception ex) {
                logger.error(msg, ex);
            }
        }
        logger.info("Updated " + (counter - exceptions) + " of " + uDao.countAllOutOfDate() + " out-of-date users.");

        // get description of some users
        // http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-users-lookup

        if (rl > 120) {
            // maximal 100 users could be processed in updateUserInfo
            noOfUsers = Math.min(rl, Math.min(todoUsers.size(), 100));
            List<YUser> selectedUsers = todoUsers.subList(0, noOfUsers);
            Collection<? extends Tweet> latestTweetsOfSelUsers = twSearch.updateUserInfo(selectedUsers);

            for (YUser u : selectedUsers) {
                try {
                    uDao.save(u);
                } catch (Exception ex) {
                    // TODO StaleObjectStateException, ConstraintViolationException, HibernateException: Found two representations of same collection: de.jetwick.data.YUser.receivedTweets
                    logger.error("Skipping user " + u.getScreenName() + " because of "
                            + ex.getClass().getSimpleName() + " " + ex.getLocalizedMessage());
                }
            }
            for (Tweet tw : latestTweetsOfSelUsers) {
                try {
//                    UpdateResult res = addTweet(tw);
//                    newTweets.addAll(res.getUpdatedTweets());
                } catch (Exception ex) {
                    // TODO org.hibernate.HibernateException: Found shared references to a collection: de.jetwick.data.YUser.ownTweets
                    logger.error("Skipping tweet " + tw.getId() + " because of "
                            + ex.getClass().getSimpleName() + " " + ex.getLocalizedMessage());
                }
            }
            logger.info("Updated info for " + selectedUsers.size() + " users");
        }

        return newTweets;
    }

    protected List<YUser> getTodoUsers() {
        int PAGE = 100;
        List<YUser> todoUsers = uDao.findAllOutOfDate(firstRow, firstRow + PAGE);
        logger.info("Users to update: " + uDao.countAllOutOfDate());
        firstRow += PAGE;
        if (todoUsers.size() == 0) {
            firstRow = 0;
            return Collections.EMPTY_LIST;
        }
        return todoUsers;
    }

    public void setUsersPerUpdate(int usersPerUpdate) {
        this.usersPerUpdate = usersPerUpdate;
    }

    public void setTwitterSearch(TwitterSearch twSearch) {
        this.twSearch = twSearch;
    }

    public void setWaitForNextCall(int waitForNextCall) {
        this.waitForNextCall = waitForNextCall;
    }
}
