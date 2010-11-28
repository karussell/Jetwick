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
import de.jetwick.data.YUser;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
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
import twitter4j.AsyncTwitter;
import twitter4j.AsyncTwitterFactory;
import twitter4j.IDs;
import twitter4j.Paging;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.Trend;
import twitter4j.Tweet;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.TwitterListener;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.User;
import twitter4j.http.AccessToken;
import twitter4j.http.RequestToken;

/**
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TwitterSearch implements Serializable {

    /**
     * Do not use less than 20 api points for queueing searches of unloggedin users
     */
    public final static int LIMIT = 50;
    private Twitter twitter;
    protected Logger logger = LoggerFactory.getLogger(TwitterSearch.class);
    private Credits credits;

    public TwitterSearch() {
    }

    public TwitterSearch setCredits(Credits credits) {
        this.credits = credits;
        return this;
    }

    public Credits getCredits() {
        return credits;
    }

    public boolean init() {
        if (twitter == null) {
            if (credits == null)
                throw new NullPointerException("Please use 'Credits' constructor!");

            logger.info("create new TwitterSearch");
            twitter = createTwitter(credits.getToken(), credits.getTokenSecret());
            return true;
        }

        return false;
    }

    public void setup() {
        // http://groups.google.com/group/twitter4j/browse_thread/thread/6f6d5b35149e2faa
        System.setProperty("twitter4j.http.useSSL", "false");

        // changing some properties to be applied on HttpURLConnection
        // default read timeout 120000 see twitter4j.internal.http.HttpClientImpl
        System.setProperty("twitter4j.http.readTimeout", "10000");

        // default connection time out 20000
        System.setProperty("twitter4j.http.connectionTimeout", "10000");
    }

    public Twitter createTwitter(String token, String tokenSecret) {
        setup();

        // get this from your application details side !
        AccessToken aToken = new AccessToken(token, tokenSecret);
        Twitter t = new TwitterFactory().getOAuthAuthorizedInstance(
                credits.getConsumerKey(), credits.getConsumerSecret(), aToken);
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
        twitter = new TwitterFactory().getOAuthAuthorizedInstance(credits.getConsumerKey(), credits.getConsumerSecret());
        tmpRequestToken = twitter.getOAuthRequestToken(callbackUrl);
        return tmpRequestToken.getAuthenticationURL();
    }

    /**
     * grab oauth_verifier from request of callback site
     * @return screenname or null
     */
    public String oAuthOnCallBack(String oauth_verifierParameter) throws TwitterException {
        if (tmpRequestToken == null)
            throw new IllegalStateException("RequestToken is empty. Call testOAuth before!");

        AccessToken aToken = twitter.getOAuthAccessToken(tmpRequestToken, oauth_verifierParameter);
        twitter.verifyCredentials();
        tmpRequestToken = null;
        return aToken.getScreenName();
    }

    public int getSecondsUntilReset() {
        try {
            return twitter.getRateLimitStatus().getSecondsUntilReset();
        } catch (TwitterException ex) {
            logger.error("Cannot determine rate limit", ex);
            return -1;
        }
    }

    public SolrUser getUser() throws TwitterException {
        SolrUser user = new SolrUser(twitter.getScreenName());
        updateUserInfo(Arrays.asList(user));
        return user;
    }

    /**
     * Check with this method otherwise you'll get TwitterException
     */
    public int getRateLimit() {
        try {
            return twitter.getRateLimitStatus().getRemainingHits();
        } catch (TwitterException ex) {
            logger.error("Cannot determine rate limit", ex);
            return 0;
        }
    }

    public Status getTweet(long id) throws TwitterException {
        return twitter.showStatus(id);
    }

    public void getTest() throws TwitterException {
        System.out.println(twitter.getFollowersIDs("dzone").getIDs());
        System.out.println(twitter.getFriendsStatuses("dzone"));
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
            for (Trend t : twitter.getTrends().getTrends()) {
                set.add(t.getName());
            }
            return set;
        } catch (TwitterException ex) {
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
    public long search(String term, Collection<SolrTweet> result, int tweets, long lastId) throws TwitterException {
        Map<String, SolrUser> userMap = new LinkedHashMap<String, SolrUser>();
        return search(term, result, userMap, tweets, lastId);
    }

    long search(String term, Collection<SolrTweet> result,
            Map<String, SolrUser> userMap, int tweets, long lastId) throws TwitterException {
        long maxId = lastId;
        long sinceId = lastId;

        int hitsPerPage = 100;
        int maxPages = tweets / hitsPerPage;
        if (tweets % hitsPerPage > 0)
            maxPages++;

        END_PAGINATION:
        for (int page = 0; page < maxPages; page++) {
            Query query = createQuery(term);
            // avoid that more recent results disturb our paging!
            if (page > 0)
                query.setMaxId(maxId);

            query.setPage(page + 1);
            query.setRpp(hitsPerPage);
            QueryResult res = twitter.search(query);

            for (Tweet twe : res.getTweets()) {
                // determine maxId in the first page
                if (page == 0 && maxId < twe.getId())
                    maxId = twe.getId();

                if (twe.getId() < sinceId)
                    break END_PAGINATION;

                String userName = twe.getFromUser().toLowerCase();
                SolrUser user = userMap.get(userName);
                if (user == null) {
                    user = new SolrUser(userName).init(twe);
                    userMap.put(userName, user);
                }

                result.add(new SolrTweet(twe, user));
            }

            // sinceId could force us to leave earlier than defined by maxPages
            if (res.getTweets().size() < hitsPerPage)
                break;
        }

        return maxId;
    }

    /**
     * @deprecated use the search method
     */
    public Collection<SolrTweet> searchAndGetUsers(String term, Collection<SolrUser> result,
            int tweets, int maxPage) throws TwitterException {
        Set<SolrTweet> solrTweets = new LinkedHashSet<SolrTweet>();
        Map<String, SolrUser> userMap = new LinkedHashMap<String, SolrUser>();
        result.addAll(userMap.values());
        return solrTweets;
    }

    public AsyncTwitter getAsyncTwitter(TwitterListener listener) {
        try {
            AsyncTwitterFactory factory = new AsyncTwitterFactory(listener);
            AsyncTwitter asyncTwitter = factory.getInstance(twitter.getAuthorization());
            return asyncTwitter;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * API COSTS: 1
     * @param users should be maximal 100 users
     * @return the latest tweets of the users
     */
    public Collection<? extends Tweet> updateUserInfo(List<? extends YUser> users) {
        if (getRateLimit() == 0) {
            logger.error("No API calls available");
            return Collections.EMPTY_LIST;
        }

        int counter = 0;
        String arr[] = new String[users.size()];

        // responseList of twitter.lookup has not the same order as arr has!!
        Map<String, YUser> userMap = new LinkedHashMap<String, YUser>();

        for (YUser u : users) {
            arr[counter++] = u.getScreenName();
            userMap.put(u.getScreenName(), u);
        }

        int maxRetries = 5;
        for (int retry = 0; retry < maxRetries; retry++) {
            try {
                ResponseList<User> res = twitter.lookupUsers(arr);
                List<Tweet> tweets = new ArrayList<Tweet>();
                for (int ii = 0; ii < res.size(); ii++) {
                    User user = res.get(ii);
                    YUser yUser = userMap.get(user.getScreenName().toLowerCase());
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

    public List<SolrTweet> getTweets(SolrUser user,
            Collection<SolrUser> users, int twPerPage) throws TwitterException {

        if (getRateLimit() == 0) {
            logger.error("No API calls available");
            return Collections.EMPTY_LIST;
        }
        Map<String, SolrUser> map = new LinkedHashMap<String, SolrUser>();
        List<SolrTweet> userTweets = getTweets(user, twPerPage);
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
    List<SolrTweet> getTweets(SolrUser user, int tweets) throws TwitterException {
        List<SolrTweet> res = new ArrayList<SolrTweet>();
        int p = 0;
        int pages = 1;

        for (; p < pages; p++) {
            res.addAll(getTweets(user, p * tweets, tweets));
        }

        return res;
    }

    public List<SolrTweet> getTweets(SolrUser user, int start, int tweets) throws TwitterException {
        List<SolrTweet> res = new ArrayList<SolrTweet>();
        int currentPage = start / tweets;

        if (tweets > 100)
            throw new IllegalStateException("Twitter does not allow more than 100 tweets per page!");

        if (tweets == 0)
            throw new IllegalStateException("tweets should be positive!");

        for (int trial = 0; trial < 2; trial++) {
            try {
                for (Status st : twitter.getUserTimeline(user.getScreenName(), new Paging(currentPage + 1, tweets, 1))) {
                    Tweet tw = toTweet(st);
                    res.add(new SolrTweet(tw, user.init(tw)));
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
     * This method is can only return up to 800 statuses, including retweets.
     */
    public long getHomeTimeline(Collection<SolrTweet> result, int tweets, long lastId) throws TwitterException {
        if (lastId <= 0)
            lastId = 1;

        Map<String, SolrUser> userMap = new LinkedHashMap<String, SolrUser>();
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
            for (Status st : tmp) {
                // determine maxId in the first page
                if (page == 0 && maxId < st.getId())
                    maxId = st.getId();

                if (st.getId() < sinceId)
                    break END_PAGINATION;


                Tweet tw = toTweet(st);
                String userName = tw.getFromUser().toLowerCase();
                SolrUser user = userMap.get(userName);
                if (user == null) {
                    user = new SolrUser(userName).init(tw);
                    userMap.put(userName, user);
                }

                result.add(new SolrTweet(tw, user));
            }

            // sinceId could force us to leave earlier than defined by maxPages
            if (tmp.size() < hitsPerPage)
                break;
        }

        return maxId;
    }

    public void streamingTwitter() throws TwitterException {
        TwitterStream stream = new TwitterStreamFactory(
                new StatusListener() {

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
                }).getInstance(twitter.getAuthorization());

//        stream.sample();

        //see http://github.com/yusuke/twitter4j/blob/master/twitter4j-examples/src/main/java/twitter4j/examples/PrintFilterStream.java
        // -> stream.filter(new FilterQuery(new int[]{1, 2, 3,}));
    }

    public void getFollowers(String userName, AnyExecutor<YUser> executor) {
        long cursor = -1;
        while (true) {
            int rate;

            while ((rate = getRateLimit()) < 5) {
                int reset = -1;
                try {
                    reset = twitter.getRateLimitStatus().getSecondsUntilReset();
                } catch (Exception ex) {
                }
                logger.info("... waiting 5 minutes. rate limit:" + rate
                        + ". minutes until reset:" + (int) (reset / 60.0f));
                myWait(5 * 60);
            }

            ResponseList<User> res = null;
            IDs ids = null;

            logger.info("get followers from cursor " + cursor);
            try {
                if (cursor < 0)
                    ids = twitter.getFollowersIDs(userName);
                else
                    ids = twitter.getFollowersIDs(userName, cursor);
            } catch (TwitterException ex) {
                logger.warn(ex.getMessage());
                break;
            }
            logger.info("found " + ids.getIDs().length);
            if (ids.getIDs().length == 0)
                break;

            int[] intids = ids.getIDs();

            // split into max 100 batch
            for (int offset = 0; offset < intids.length; offset += 100) {
                int[] limitedIds = new int[100];
                for (int ii = 0; ii + offset < intids.length && ii < limitedIds.length; ii++) {
                    limitedIds[ii] = intids[ii + offset];
                }

                // retry at max N times for every id bunch
                for (int i = 0; i < 5; i++) {
                    try {
                        res = twitter.lookupUsers(limitedIds);
                        for (User user : res) {
                            // strange that this was necessary for ibood
                            if (user.getScreenName().trim().isEmpty())
                                continue;

                            YUser yUser = new YUser(user.getScreenName());
                            yUser.updateFieldsBy(user);
                            executor.execute(yUser);
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

    private void myWait(int i) {
        try {
            Thread.sleep(i * 1000);
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
}
