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

import com.google.inject.Inject;
import de.jetwick.solr.SolrUser;
import de.jetwick.solr.SolrUserSearch;
import de.jetwick.tw.TwitterSearch;
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

    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Inject
    private TwitterSearch twitterSearch;
    private SolrUser user = null;
    private boolean twitterSearchInitialized = false;
    private boolean sessionTimedout = false;

    public MySession(Request request) {
        super(request);
    }

    public void setSessionTimedout(boolean sessionTimedout) {
        this.sessionTimedout = sessionTimedout;
    }

    public String getSessionTimeoutMessage() {
        String str = "";
        if (sessionTimedout)
            str = "Jetwick was in sleepmode. Please try again.";

        sessionTimedout = false;
        return str;
    }

    void setTwitterSearchInitialized(boolean twitterSearchInitialized) {
        this.twitterSearchInitialized = twitterSearchInitialized;
    }

    public TwitterSearch getTwitterSearch() {
        return twitterSearch;
    }

    public void setTwitterSearch(TwitterSearch twitterSearch) {
        this.twitterSearch = twitterSearch;
    }

    public void init(WebRequest request, SolrUserSearch uSearch) {
        if (!twitterSearchInitialized) {
            twitterSearchInitialized = true;
            Cookie cookie = request.getCookie(TwitterSearch.COOKIE);
            if (cookie != null) {
                setUser(uSearch.findByTwitterToken(cookie.getValue()));
                if (user != null)
                    logger.info("Found cookie for user:" + getUser().getScreenName());
            } else
                logger.info("No cookie found");
        }
    }

    public SolrUser getUser() {
        return user;
    }

    private void setUser(SolrUser user) {
        this.user = user;
        dirty();
    }

    public boolean hasLoggedIn() {
        return getUser() != null;
    }

    /**
     * Use only if specified ts is already initialized
     */
    public Cookie setTwitterSearch(AccessToken token, SolrUserSearch uSearch, WebResponse response) {
        twitterSearch.setTwitter4JInstance(token.getToken(), token.getTokenSecret());
        twitterSearchInitialized = true;
        try {
//            logger.info("TOKEN:" + token);
            Cookie cookie = new Cookie(TwitterSearch.COOKIE, token.getToken());
            // TODO use https: cookie.setSecure(true);
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
            SolrUser tmpUser = uSearch.findByTwitterToken(token.getToken());
            if (tmpUser == null) {
                logger.info("token for " + twitterUser.getScreenName() + " not found in user index");
                // token will be removed on logout so get saved searches from uindex
                tmpUser = uSearch.findByScreenName(twitterUser.getScreenName());
                if (tmpUser == null) {
                    logger.info("user " + twitterUser.getScreenName() + " not found in user index");
                    tmpUser = new SolrUser(twitterUser.getScreenName());
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

    public void logout(SolrUserSearch uSearch, WebResponse response) {
        response.clearCookie(new Cookie(TwitterSearch.COOKIE, ""));

        SolrUser tmpUser = getUser();
        if (tmpUser != null) {
            logger.info("[stats] user logout:" + tmpUser.getScreenName());
            tmpUser.setTwitterToken(null);
            tmpUser.setTwitterTokenSecret(null);
            uSearch.save(tmpUser, true);
        }

        setUser(null);
    }
}
