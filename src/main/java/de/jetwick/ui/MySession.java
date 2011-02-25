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
package de.jetwick.ui;

import de.jetwick.config.DefaultModule;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.data.JUser;
import de.jetwick.tw.TwitterSearch;
import java.util.Collection;
import javax.servlet.http.Cookie;
import org.apache.wicket.Request;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.protocol.http.WebSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.http.AccessToken;

/**
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class MySession extends WebSession {

    private static final long serialVersionUID = 1L;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    /**
     * twitter4j saves state. so it is a bit complicated.
     * twitterSearch is always a new instance with the same twitter4j reference.
     * BUT we need to replace the twitter4j instance for every logged in user via
     * TwitterSearch.setTwitter4JInstance to get the correct user name etc
     *
     * @see DefaultModule
     */
    private TwitterSearch twitterSearch;
    private JUser user = null;
    private boolean twitterSearchInitialized = false;
    private boolean sessionTimedOut = false;

    public MySession(Request request) {
        super(request);
    }

    public void setSessionTimedOut(boolean sessionTimedout) {
        this.sessionTimedOut = sessionTimedout;
    }

    public String getSessionTimeOutMessage() {
        String str = "";
        if (sessionTimedOut)
            str = "You have been inactivate a while. Please try again.";

        sessionTimedOut = false;
        return str;
    }

    void setTwitterSearchInitialized(boolean twitterSearchInitialized) {
        this.twitterSearchInitialized = twitterSearchInitialized;
    }

    public TwitterSearch getTwitterSearch() {
        return twitterSearch;
    }

    // called for every request. initialized with default karussell data
    public void setTwitterSearch(TwitterSearch twitterSearch) {
        this.twitterSearch = twitterSearch;
    }

    public void init(WebRequest request, ElasticUserSearch uSearch) {
        if (!twitterSearchInitialized) {
            twitterSearchInitialized = true;
            Cookie cookie = request.getCookie(TwitterSearch.COOKIE);
            if (cookie != null) {
                setUser(uSearch.findByTwitterToken(cookie.getValue()));
                if (user != null)
                    logger.info("Found cookie for user:" + getUser().getScreenName());
            } else
                logger.info("No cookie found. IP=" + request.getHttpServletRequest().getRemoteHost());
        }
    }

    public JUser getUser() {
        return user;
    }

    private void setUser(JUser user) {
        this.user = user;
        dirty();
    }

    public boolean hasLoggedIn() {
        return getUser() != null;
    }

    /**
     * Use only if specified ts is already initialized
     */
    public Cookie setTwitterSearch(AccessToken token, ElasticUserSearch uSearch, WebResponse response) {
        twitterSearch.initTwitter4JInstance(token.getToken(), token.getTokenSecret());
        twitterSearchInitialized = true;
        try {
//            logger.info("TOKEN:" + token);
            Cookie cookie = new Cookie(TwitterSearch.COOKIE, token.getToken());
            // LATER: use https: cookie.setSecure(true);
            cookie.setComment("Supply autologin for jetwick.com");
            // four weeks
            cookie.setMaxAge(4 * 7 * 24 * 60 * 60);
            response.addCookie(cookie);

            // get current infos
            User twitterUser = twitterSearch.getTwitterUser();
            if (twitterUser == null)
                throw new IllegalStateException("user from twitterSearch cannot be null");

            logger.info("new twitter4j initialized for user:" + twitterUser.getScreenName());

            // get current saved searches and update with current twitter infos
            JUser tmpUser = uSearch.findByTwitterToken(token.getToken());
            if (tmpUser == null) {
                logger.info("token for " + twitterUser.getScreenName() + " not found in user index");
                // token will be removed on logout so get saved searches from uindex
                tmpUser = uSearch.findByScreenName(twitterUser.getScreenName());
                if (tmpUser == null) {
                    logger.info("user " + twitterUser.getScreenName() + " not found in user index");
                    tmpUser = new JUser(twitterUser.getScreenName());
                }
            }

            tmpUser.updateFieldsBy(twitterUser);
            tmpUser.setTwitterToken(token.getToken());
            tmpUser.setTwitterTokenSecret(token.getTokenSecret());

            // save user into user index
            uSearch.save(tmpUser, true);

            // save user into session
            setUser(tmpUser);

            logger.info("[stats] user login:" + twitterUser.getScreenName() + " " + tmpUser.getScreenName());
            return cookie;
        } catch (TwitterException ex) {
            logger.error("Couldn't change twitterSearch user" + ex.getMessage());
            return null;
        }
    }

    public void logout(ElasticUserSearch uSearch, WebResponse response) {
        response.clearCookie(new Cookie(TwitterSearch.COOKIE, ""));

        JUser tmpUser = getUser();
        if (tmpUser != null) {
            logger.info("[stats] user logout:" + tmpUser.getScreenName());
            tmpUser.setTwitterToken(null);
            tmpUser.setTwitterTokenSecret(null);
            uSearch.save(tmpUser, true);
        }

        setUser(null);
    }

    public Collection<String> getFriends(ElasticUserSearch uSearch) {
        Collection<String> friends = getUser().getFriends();
        if(friends.isEmpty()) {
            // we will need to update regularly to avoid missing the friends-update from TweetProducer
            user = uSearch.findByScreenName(getUser().getScreenName());
            friends = user.getFriends();            
        }        
        return friends;
    }
}
