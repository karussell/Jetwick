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

import de.jetwick.util.AnyExecutor;
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.IDs;
import twitter4j.Paging;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.RateLimitStatus;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.Trend;
import twitter4j.Tweet;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;

/**
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TwitterSearch implements Serializable {

    private static final long serialVersionUID = 1L;
    public final static String COOKIE = "jetwick";
    /**
     * Do not use less than this limit of 20 api points for queueing searches of unloggedin users
     */
    public final static int LIMIT = 50;
    private Twitter twitter;
    protected Logger logger = LoggerFactory.getLogger(TwitterSearch.class);
    private String consumerKey;
    private String consumerSecret;
    private int rateLimit = -1;

    public TwitterSearch() {
    }

    public TwitterSearch setConsumer(String consumerKey, String consumerSecrect) {
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecrect;
        return this;
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    /**
     * Connect with twitter to get a new personalized twitter4j instance.
     * @throws RuntimeException if verification or connecting failed
     */
    public TwitterSearch initTwitter4JInstance(String token, String tokenSecret) {
        if (consumerKey == null)
            throw new NullPointerException("Please use init consumer settings!");

        twitter = createTwitter(token, tokenSecret);
        logger.info("create new TwitterSearch");
        return this;
    }

    /**
     * Set an already 'connected' twitter4j instance. No exception can be thrown.
     */
    public TwitterSearch setTwitter4JInstance(Twitter tw) {
        twitter = tw;
        return this;
    }

    public Twitter getTwitter4JInstance() {
        return twitter;
    }

    private void setupProperties() {
        // this issue should now be resolved:
        // http://groups.google.com/group/twitter4j/browse_thread/thread/6f6d5b35149e2faa
//        System.setProperty("twitter4j.http.useSSL", "false");

        // friends makes problems
        // http://groups.google.com/group/twitter4j/browse_thread/thread/f696de22d4554143
        // http://groups.google.com/group/twitter-development-talk/browse_thread/thread/cd76f954957f6fb0
        // http://groups.google.com/group/twitter-development-talk/browse_thread/thread/9e9bfec2f076e4f9
        //System.setProperty("twitter4j.http.useSSL", "true");

        // changing some properties to be applied on HttpURLConnection
        // default read timeout 120000 see twitter4j.internal.http.HttpClientImpl
        System.setProperty("twitter4j.http.readTimeout", "10000");

        // default connection time out 20000
        System.setProperty("twitter4j.http.connectionTimeout", "10000");
    }

    public Twitter createTwitter(String token, String tokenSecret) {
        setupProperties();
        AccessToken aToken = new AccessToken(token, tokenSecret);
        // get this from your application details side !
        Twitter t = new TwitterFactory().getInstance();
        t.setOAuthConsumer(consumerKey, consumerSecret);
        t.setOAuthAccessToken(aToken);
        try {
//            RequestToken requestToken = t.getOAuthRequestToken();
//            System.out.println("TW-URL:" + requestToken.getAuthorizationURL());

            t.verifyCredentials();
        } catch (TwitterException ex) {
            // rate limit only exceeded
            if (ex.getStatusCode() == 400)
                return t;

            throw new RuntimeException(ex);
        }

        return t;
    }
    private RequestToken tmpRequestToken;

    /**
     * @return the url where the user should be redirected to
     */
    public String oAuthLogin(String callbackUrl) throws Exception {
        twitter = new TwitterFactory().getInstance();
        twitter.setOAuthConsumer(consumerKey, consumerSecret);
        tmpRequestToken = twitter.getOAuthRequestToken(callbackUrl);
        return tmpRequestToken.getAuthenticationURL();
    }

    /**
     * grab oauth_verifier from request of callback site
     * @return screenname or null
     */
    public AccessToken oAuthOnCallBack(String oauth_verifierParameter) throws TwitterException {
        if (tmpRequestToken == null)
            throw new IllegalStateException("RequestToken is empty. Call testOAuth before!");

        AccessToken aToken = twitter.getOAuthAccessToken(tmpRequestToken, oauth_verifierParameter);
        twitter.verifyCredentials();
        tmpRequestToken = null;
        return aToken;
    }

    public String getScreenName() {
        try {
            return twitter.getScreenName();
        } catch (Exception ex) {
            return null;
        }
    }

    public JUser getUser() throws TwitterException {
        return getUser(twitter.getScreenName());
    }

    public JUser getUser(String screenName) throws TwitterException {
        JUser user = new JUser(screenName);
        updateUserInfo(Arrays.asList(user));
        return user;
    }

    public User getTwitterUser() throws TwitterException {
        ResponseList<User> list = twitter.lookupUsers(new String[]{twitter.getScreenName()});
        rateLimit--;
        if (list.size() == 0)
            return null;
        else if (list.size() == 1)
            return list.get(0);
        else
            throw new IllegalStateException("returned more than one user for screen name:" + twitter.getScreenName());
    }

    public int getSecondsUntilReset() {
        try {
            RateLimitStatus rls = twitter.getRateLimitStatus();
            rateLimit = rls.getRemainingHits();
            return rls.getSecondsUntilReset();
        } catch (TwitterException ex) {
            logger.error("Cannot determine rate limit", ex);
            return -1;
        }
    }

    /**
     * Check with this method otherwise you'll get TwitterException
     */
    public int getRateLimit() {
        try {
            rateLimit = twitter.getRateLimitStatus().getRemainingHits();
            return rateLimit;
        } catch (TwitterException ex) {
            logger.error("Cannot determine rate limit", ex);
            return -1;
        }
    }

    public int getRateLimitFromCache() {
        if (twitter == null)
            return -1;

        try {
            if (rateLimit < 0)
                rateLimit = twitter.getRateLimitStatus().getRemainingHits();
        } catch (TwitterException ex) {
            rateLimit = -1;
        }

        return rateLimit;
    }

    /**
     * forces correct rate limit for next getRateLimitFromCache
     */
    public void resetRateLimitCache() {
        rateLimit = -1;
    }

    public Status getTweet(long id) throws TwitterException {
        Status st = twitter.showStatus(id);
        rateLimit--;
        return st;
    }

    public void getTest() throws TwitterException {
        System.out.println(twitter.getFollowersIDs("dzone", 0).getIDs());
        System.out.println(twitter.getFriendsStatuses("dzone", 0));
        rateLimit--;
        rateLimit--;
    }

    // this works:
    // curl -u user:pw http://api.twitter.com/1/statuses/13221113653/retweeted_by.xml => Peter
    // curl -u user:pw http://api.twitter.com/1/statuses/13221113653/retweeted_by/ids.xml => 51798603 (my user id)
    List<Status> getRetweets(long id) {
        return Collections.EMPTY_LIST;
//        try {
//            return twitter.getRetweets(id);
//        } catch (TwitterException ex) {
//            throw new RuntimeException(ex);
//        }
    }
    private long lastAccess = 0;

    public List<Tweet> getSomeTweets() {
        if ((System.currentTimeMillis() - lastAccess) < 50 * 1000) {
            logger.info("skipping public timeline");
            return Collections.emptyList();
        }

        lastAccess = System.currentTimeMillis();
        List<Tweet> res = new ArrayList<Tweet>();
        try {
            ResponseList<Status> statusList = twitter.getPublicTimeline();
            rateLimit--;
            for (Status st : statusList) {
                res.add(toTweet(st));
            }
            return res;
        } catch (TwitterException ex) {
            logger.error("Cannot get trends!", ex);
            return res;
        }
    }

    public static Twitter4JTweet toTweet(Status st) {
        return toTweet(st, st.getUser());
    }

    public static Twitter4JTweet toTweet(Status st, User user) {
        if (user == null)
            throw new IllegalArgumentException("User mustn't be null!");
        if (st == null)
            throw new IllegalArgumentException("Status mustn't be null!");

        Twitter4JTweet tw = new Twitter4JTweet(st.getId(), st.getText(), user.getScreenName());
        tw.setCreatedAt(st.getCreatedAt());
        tw.setFromUser(user.getScreenName());

        if (user.getProfileImageURL() != null)
            tw.setProfileImageUrl(user.getProfileImageURL().toString());

        tw.setSource(st.getSource());
        tw.setToUser(st.getInReplyToUserId(), st.getInReplyToScreenName());
        tw.setInReplyToStatusId(st.getInReplyToStatusId());

        if (st.getGeoLocation() != null) {
            tw.setGeoLocation(st.getGeoLocation());
            tw.setLocation(st.getGeoLocation().getLatitude() + ", " + st.getGeoLocation().getLongitude());
        } else if (st.getPlace() != null)
            tw.setLocation(st.getPlace().getCountryCode());
        else if (user.getLocation() != null)
            tw.setLocation(toStandardLocation(user.getLocation()));

        return tw;
    }

    public static String toStandardLocation(String loc) {
        if (loc == null || loc.trim().length() == 0)
            return null;

        String[] locs;
        if (loc.contains("/"))
            locs = loc.split("/", 2);
        else if (loc.contains(","))
            locs = loc.split(",", 2);
        else
            locs = new String[]{loc};

        if (locs.length == 2)
            return locs[0].replaceAll("[,/]", " ").replaceAll("  ", " ").trim() + ", "
                    + locs[1].replaceAll("[,/]", " ").replaceAll("  ", " ").trim();
        else
            return locs[0].replaceAll("[,/]", " ").replaceAll("  ", " ").trim() + ", -";
    }

    public Set<String> getTrends() {
        try {
            // twitter.getPublicTimeline() -> only 20 tweets per minute
            Set<String> set = new LinkedHashSet<String>();
            Trend[] trends = twitter.getTrends().getTrends();
            rateLimit--;
            for (Trend t : trends) {
                set.add(t.getName());
            }
            return set;
        } catch (TwitterException ex) {
            logger.error("Couldn't grab trends", ex);
            return Collections.EMPTY_SET;
        }
    }

    Query createQuery(String str) {
        Query q = new Query(str);
        q.setResultType(Query.RECENT);
        return q;
    }

    // Twitter Search API:
    // Returns up to a max of roughly 1500 results
    // Rate limited by IP address.
    // The specific number of requests a client is able to make to the Search API for a given hour is not released.
    // The number is quite a bit higher and we feel it is both liberal and sufficient for most applications.
    // The since_id parameter will be removed from the next_page element as it is not supported for pagination.
    public long search(String term, Collection<JTweet> result, int tweets, long lastMaxCreateTime) throws TwitterException {
        Map<String, JUser> userMap = new LinkedHashMap<String, JUser>();
        return search(term, result, userMap, tweets, lastMaxCreateTime);
    }

    long search(String term, Collection<JTweet> result,
            Map<String, JUser> userMap, int tweets, long lastMaxCreateTime) throws TwitterException {
        long maxId = 0L;
        long maxMillis = 0L;
        int hitsPerPage;
        int maxPages;
        if (tweets < 100) {
            hitsPerPage = tweets;
            maxPages = 1;
        } else {
            hitsPerPage = 100;
            maxPages = tweets / hitsPerPage;
            if (tweets % hitsPerPage > 0)
                maxPages++;
        }

        boolean breakPaging = false;
        for (int page = 0; page < maxPages; page++) {
            Query query = new Query(term);
            // RECENT or POPULAR
            query.setResultType(Query.MIXED);

            // avoid that more recent results disturb our paging!
            if (page > 0)
                query.setMaxId(maxId);

            query.setPage(page + 1);
            query.setRpp(hitsPerPage);
            QueryResult res = twitter.search(query);

            // is res.getTweets() sorted?
            for (Tweet twe : res.getTweets()) {
                // determine maxId in the first page
                if (page == 0 && maxId < twe.getId())
                    maxId = twe.getId();

                if (maxMillis < twe.getCreatedAt().getTime())
                    maxMillis = twe.getCreatedAt().getTime();

                if (twe.getCreatedAt().getTime() + 1000 < lastMaxCreateTime)
                    breakPaging = true;
                else {
                    String userName = twe.getFromUser().toLowerCase();
                    JUser user = userMap.get(userName);
                    if (user == null) {
                        user = new JUser(userName).init(twe);
                        userMap.put(userName, user);
                    }

                    result.add(new JTweet(twe, user));
                }
            }

            // minMillis could force us to leave earlier than defined by maxPages
            if (breakPaging || res.getTweets().size() < hitsPerPage)
                break;
        }

        return maxMillis;
    }

    /**
     * @deprecated use the search method
     */
    public Collection<JTweet> searchAndGetUsers(String term, Collection<JUser> result,
            int tweets, int maxPage) throws TwitterException {
        Set<JTweet> solrTweets = new LinkedHashSet<JTweet>();
        Map<String, JUser> userMap = new LinkedHashMap<String, JUser>();
        result.addAll(userMap.values());
        return solrTweets;
    }

    /**
     * API COSTS: 1
     * @param users should be maximal 100 users
     * @return the latest tweets of the users
     */
    public Collection<? extends Tweet> updateUserInfo(List<? extends JUser> users) {
        int counter = 0;
        String arr[] = new String[users.size()];

        // responseList of twitter.lookup has not the same order as arr has!!
        Map<String, JUser> userMap = new LinkedHashMap<String, JUser>();

        for (JUser u : users) {
            arr[counter++] = u.getScreenName();
            userMap.put(u.getScreenName(), u);
        }

        int maxRetries = 5;
        for (int retry = 0; retry < maxRetries; retry++) {
            try {
                ResponseList<User> res = twitter.lookupUsers(arr);
                rateLimit--;
                List<Tweet> tweets = new ArrayList<Tweet>();
                for (int ii = 0; ii < res.size(); ii++) {
                    User user = res.get(ii);
                    JUser yUser = userMap.get(user.getScreenName().toLowerCase());
                    if (yUser == null)
                        continue;

                    Status stat = yUser.updateFieldsBy(user);
                    if (stat == null)
                        continue;

                    Twitter4JTweet tw = toTweet(stat, res.get(ii));
                    tweets.add(tw);
                }
                return tweets;
            } catch (TwitterException ex) {
                logger.warn("Couldn't lookup users. Retry:" + retry + " of " + maxRetries, ex);
                if (retry < 1)
                    continue;
                else
                    break;
            }
        }

        return Collections.EMPTY_LIST;
    }

    public List<JTweet> getTweets(JUser user, Collection<JUser> users,
            int twPerPage) throws TwitterException {
        Map<String, JUser> map = new LinkedHashMap<String, JUser>();
        List<JTweet> userTweets = getTweets(user, twPerPage);
        users.addAll(map.values());
        return userTweets;
    }

    // http://apiwiki.twitter.com/Twitter-REST-API-Method:-statuses-user_timeline
    // -> without RETWEETS!? => count can be smaller than the requested!
//    public List<SolrTweet> getTweets(String userScreenName) throws TwitterException {
//        if (getRateLimit() == 0) {
//            logger.error("No API calls available");
//            return Collections.EMPTY_LIST;
//        }
//        return getTweets(userScreenName, 100);
//    }
    /**
     * You will only be able to access the latest 3200 statuses from a user's timeline
     */
    List<JTweet> getTweets(JUser user, int tweets) throws TwitterException {
        List<JTweet> res = new ArrayList<JTweet>();
        int p = 0;
        int pages = 1;

        for (; p < pages; p++) {
            res.addAll(getTweets(user, p * tweets, tweets));
        }

        return res;
    }

    public List<JTweet> getTweets(JUser user, int start, int tweets) throws TwitterException {
        List<JTweet> res = new ArrayList<JTweet>();
        int currentPage = start / tweets;

        if (tweets > 100)
            throw new IllegalStateException("Twitter does not allow more than 100 tweets per page!");

        if (tweets == 0)
            throw new IllegalStateException("tweets should be positive!");

        for (int trial = 0; trial < 2; trial++) {
            try {
                ResponseList<Status> rList = twitter.getUserTimeline(
                        user.getScreenName(), new Paging(currentPage + 1, tweets, 1));
                rateLimit--;
                for (Status st : rList) {
                    Tweet tw = toTweet(st);
                    res.add(new JTweet(tw, user.init(tw)));
                }
                break;
            } catch (TwitterException ex) {
                logger.warn("Exception while getTweets. trial:" + trial + " page:" + currentPage + " - " + ex.getMessage());
                if (ex.exceededRateLimitation())
                    return res;

                continue;
            }
        }

        return res;
    }

    /**
     * The last 200 tweets will be retrieved
     */
    public Collection<Tweet> getHomeTimeline(int tweets) throws TwitterException {
        ArrayList list = new ArrayList<Tweet>();
        getHomeTimeline(list, tweets, 0);
        return list;
    }

    /**
     * This method only returns up to 800 statuses, including retweets.
     */
    public long getHomeTimeline(Collection<JTweet> result, int tweets, long lastId) throws TwitterException {
        if (lastId <= 0)
            lastId = 1;

        Map<String, JUser> userMap = new LinkedHashMap<String, JUser>();
        int hitsPerPage = 100;
        long maxId = lastId;
        long sinceId = lastId;
        int maxPages = tweets / hitsPerPage + 1;

        END_PAGINATION:
        for (int page = 0; page < maxPages; page++) {
            Paging paging = new Paging(page + 1, tweets, sinceId);
            // avoid that more recent results disturb our paging!
            if (page > 0)
                paging.setMaxId(maxId);

            Collection<Status> tmp = twitter.getHomeTimeline(paging);
            rateLimit--;
            for (Status st : tmp) {
                // determine maxId in the first page
                if (page == 0 && maxId < st.getId())
                    maxId = st.getId();

                if (st.getId() < sinceId)
                    break END_PAGINATION;


                Tweet tw = toTweet(st);
                String userName = tw.getFromUser().toLowerCase();
                JUser user = userMap.get(userName);
                if (user == null) {
                    user = new JUser(userName).init(tw);
                    userMap.put(userName, user);
                }

                result.add(new JTweet(tw, user));
            }

            // sinceId could force us to leave earlier than defined by maxPages
            if (tmp.size() < hitsPerPage)
                break;
        }

        return maxId;
    }

    public void streamingTwitter() throws TwitterException {

        TwitterStream stream = new TwitterStreamFactory().getInstance(twitter.getAuthorization());
        stream.addListener(new StatusListener() {

            @Override
            public void onStatus(Status status) {
                System.out.println(status.getUser().getScreenName());
            }

            @Override
            public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
            }

            @Override
            public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
            }

            @Override
            public void onException(Exception ex) {
            }

            @Override
            public void onScrubGeo(long userId, long upToStatusId) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        });

//        stream.sample();

        //see http://github.com/yusuke/twitter4j/blob/master/twitter4j-examples/src/main/java/twitter4j/examples/PrintFilterStream.java
        // -> stream.filter(new FilterQuery(new int[]{1, 2, 3,}));
    }

    public void getFollowers(String user, AnyExecutor<JUser> anyExecutor) {
        getFriendsOrFollowers(user, anyExecutor, false);
    }

    public void getFriends(String userName, AnyExecutor<JUser> executor) {
        getFriendsOrFollowers(userName, executor, true);
    }

    private void getFriendsOrFollowers(String userName, AnyExecutor<JUser> executor, boolean friends) {
        long cursor = 1;
        resetRateLimitCache();
        while (true) {
            while (getRateLimitFromCache() < LIMIT) {
                int reset = getSecondsUntilReset();
                if (reset != 0) {
                    logger.info("no api points left while getFriendsOrFollowers! Skipping ...");
                    return;
                }
                resetRateLimitCache();
                myWait(0.5f);
            }

            ResponseList<User> res = null;
            IDs ids = null;
            try {
                if (friends)
                    ids = twitter.getFriendsIDs(userName, cursor);
                else
                    ids = twitter.getFollowersIDs(userName, cursor);

                rateLimit--;
            } catch (TwitterException ex) {
                logger.warn(ex.getMessage());
                break;
            }
            if (ids.getIDs().length == 0)
                break;

            long[] intids = ids.getIDs();

            // split into max 100 batch
            for (int offset = 0; offset < intids.length; offset += 100) {
                long[] limitedIds = new long[100];
                for (int ii = 0; ii + offset < intids.length && ii < limitedIds.length; ii++) {
                    limitedIds[ii] = intids[ii + offset];
                }

                // retry at max N times for every id bunch
                for (int i = 0; i < 5; i++) {
                    try {
                        res = twitter.lookupUsers(limitedIds);
                        rateLimit--;
                        for (User user : res) {
                            // strange that this was necessary for ibood
                            if (user.getScreenName().trim().isEmpty())
                                continue;

                            JUser jUser = new JUser(user.getScreenName());
                            jUser.updateFieldsBy(user);
                            executor.execute(jUser);
                        }
                        break;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        myWait(5);
                        continue;
                    }
                }

                if (res == null) {
                    logger.error("giving up");
                    break;
                }
            }

            if (!ids.hasNext())
                break;

            cursor = ids.getNextCursor();
        }
    }

    public Status doRetweet(long twitterId) throws TwitterException {
        Status st = twitter.retweetStatus(twitterId);
        rateLimit--;
        return st;
    }

    private void myWait(float seconds) {
        try {
            Thread.sleep(Math.round(seconds * 1000));
        } catch (Exception ex) {
            throw new UnsupportedOperationException(ex);
        }
    }

    /**
     * @return a message describing the problem with twitter or an empty string
     * if nothing related to twitter!
     */
    public static String getMessage(Exception ex) {
        if (ex instanceof TwitterException) {
            TwitterException twExc = (TwitterException) ex;
            if (twExc.exceededRateLimitation())
                return ("Couldn't process your request. You don't have enough twitter API points!"
                        + " Please wait: " + twExc.getRetryAfter() + " seconds and try again!");
            else if (twExc.isCausedByNetworkIssue())
                return ("Couldn't process your request. Network issue.");
            else
                return ("Couldn't process your request. Something went wrong while communicating with Twitter :-/");
        }

        return "";
    }

    public boolean isInitialized() {
        return twitter != null;
    }
}
