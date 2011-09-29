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
import de.jetwick.util.Helper;
import java.util.Collection;
import java.util.Date;
import javax.servlet.http.Cookie;
import org.apache.wicket.Request;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.protocol.http.WebSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.auth.AccessToken;

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
     * onNewSession session called for every request. everytime a new instance 
     */
    private TwitterSearch twitterSearch;
    private JUser user = null;
    private boolean twitterSearchInitialized = false;
    private boolean sessionTimedOut = false;
    private String email;

    public MySession(Request request) {
        super(request);
    }

    public void setSessionTimedOut(boolean sessionTimedout) {
        this.sessionTimedOut = sessionTimedout;
    }

    public String getSessionTimeOutMessage() {
        String str = "";
        if (sessionTimedOut)
            str = "You have been inactivate a while. Please go back and refresh or try again.";

        sessionTimedOut = false;
        return str;
    }

    void setTwitterSearchInitialized(boolean twitterSearchInitialized) {
        this.twitterSearchInitialized = twitterSearchInitialized;
    }

    public TwitterSearch getTwitterSearch() {
        return twitterSearch;
    }

    // for test only
    void setTwitterSearch(TwitterSearch twitterSearch) {
        this.twitterSearch = twitterSearch;
    }

    public JUser getUser() {
        return user;
    }

    // use only for tests!
    public void setUser(JUser user) {
        this.user = user;
        dirty();
    }

    public boolean hasLoggedIn() {
        return getUser() != null;
    }

    public void onNewSession(WebRequest request, ElasticUserSearch uSearch) {
        if (!twitterSearchInitialized) {
            twitterSearchInitialized = true;
            Cookie cookie = request.getCookie(TwitterSearch.COOKIE);
            if (cookie != null) {                
                setUser(uSearch.findByTwitterToken(cookie.getValue()));
                if (user != null) {
                    user.setLastVisit(new Date());
                    uSearch.save(user, false);
                    logger.info("Found cookie for user:" + getUser().getScreenName());
                }
            } else
                logger.info("No cookie found. IP=" + request.getHttpServletRequest().getRemoteHost());
        }
    }

    public Cookie afterLogin(AccessToken token, ElasticUserSearch uSearch,
            WebResponse response)
            throws TwitterException {
        if (email == null)
            throw new IllegalArgumentException("Email not available. Please login again.");

        twitterSearch.initTwitter4JInstance(token.getToken(), token.getTokenSecret(), true);
        twitterSearchInitialized = true;
//            logger.info("TOKEN:" + token);
        Cookie cookie = new Cookie(TwitterSearch.COOKIE, token.getToken());
        // LATER: use https: cookie.setSecure(true);
        cookie.setComment("Supply autologin for " + Helper.JETSLIDE_URL);
        // four weeks
        cookie.setMaxAge(4 * 7 * 24 * 60 * 60);
        // set path because the cookie should be sent for / and /slide* and /login*
        cookie.setPath("/");
        response.addCookie(cookie);

        // get current infos
        User twitterUser = twitterSearch.getTwitterUser();

        // get current saved searches and update with current twitter infos
        JUser tmpUser = uSearch.findById(token.getUserId());
        if (tmpUser == null) {
            tmpUser = uSearch.findByScreenName(twitterUser.getScreenName());
            if (tmpUser == null)
                tmpUser = new JUser(twitterUser.getScreenName());
        }

        tmpUser.updateFieldsBy(twitterUser);
        // now set email entered on form one page before twitter redirect
        tmpUser.setEmail(email);
        tmpUser.setTwitterToken(token.getToken());
        tmpUser.setTwitterTokenSecret(token.getTokenSecret());
        tmpUser.setLastVisit(new Date());

        // save user into user index
        uSearch.save(tmpUser, false);

        // save user into session
        setUser(tmpUser);

        logger.info("[stats] user login:" + twitterUser.getScreenName() + " " + tmpUser.getScreenName());
        return cookie;
    }

    public void clearCookie(WebResponse response, String name) {
        Cookie c = new Cookie(name, "");
        c.setPath("/");
        response.clearCookie(c);
    }

    public void logout(ElasticUserSearch uSearch, WebResponse response, boolean invalidate) {
        // clear old cookie version too:
        clearCookie(response, "jetwick");
        clearCookie(response, TwitterSearch.COOKIE);
        JUser tmpUser = getUser();
        if (tmpUser != null) {
            logger.info("[stats] user logout:" + tmpUser.getScreenName());
//            tmpUser.setTwitterToken(null);
//            tmpUser.setTwitterTokenSecret(null);
            uSearch.save(tmpUser, false);
        }
        setUser(null);
        if(invalidate)
            invalidate();
    }

    public Collection<String> getFriends(ElasticUserSearch uSearch) {
        Collection<String> friends = getUser().getFriends();
        if (friends.isEmpty()) {
            // we will need to update regularly to avoid missing the friends-update from TweetProducer
            JUser tmp = uSearch.findByScreenName(getUser().getScreenName());
            if (tmp != null) {                            
                user = tmp;
                friends = tmp.getFriends();
            }
        }
        return friends;
    }

    public void setFormData(String email, String pw) {
        this.email = email;
//        this.password = pw;
    }
}
