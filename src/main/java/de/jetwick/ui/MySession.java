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
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.wicket.Request;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.protocol.http.WebSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterException;

/**
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class MySession extends WebSession {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Inject
    private TwitterSearch twitterSearch;
    private boolean useDefaultUser = false;
    private SolrUser user = null;
    private boolean twitterSearchInitialized = false;
    private Cookie cookie;

    public MySession(Request request, boolean useDefaultUser) {
        super(request);
        this.useDefaultUser = useDefaultUser;
    }

    MySession setUseDefaultUser(boolean useDefaultUser) {
        this.useDefaultUser = useDefaultUser;
        return this;
    }

    void setTwitterSearchInitialized(boolean twitterSearchInitialized) {
        this.twitterSearchInitialized = twitterSearchInitialized;
    }

    public TwitterSearch getTwitterSearch() {
        return twitterSearch;
    }

    public void init(WebRequest request, SolrUserSearch search) {
        if (!twitterSearchInitialized) {
            twitterSearchInitialized = true;
            cookie = request.getCookie(TwitterSearch.COOKIE);
            if (cookie != null) {
                try {
                    setUser(search.getUserByToken(cookie.getValue()));
                    if (user != null) {
                        logger.info("Found cookie for user:" + getUser().getScreenName());
                        twitterSearch.getCredits().setToken(getUser().getTwitterToken());
                        twitterSearch.getCredits().setTokenSecret(getUser().getTwitterTokenSecret());
                    }
                } catch (SolrServerException ex) {
                    logger.error("Exception while querying user index:" + ex.getMessage(), ex);
                }
            } else
                logger.info("No cookie");

            logger.info("twitter4j initialized for session");
            // twitterSearch.init() is expensive so call it only once per session
            if (twitterSearch.init() && useDefaultUser) {
                try {
                    setUser(twitterSearch.getUser());
                } catch (TwitterException ex) {
                    logger.error("Couldn't init dev account!", ex);
                }
            }
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
     * Use only if twitterSearch is already initialized
     */
    public Cookie setTwitterSearch(TwitterSearch ts, SolrUserSearch uSearch, WebResponse response) {
        twitterSearchInitialized = true;
        twitterSearch = ts;
        try {
            cookie = new Cookie(TwitterSearch.COOKIE, getTwitterSearch().getCredits().getToken());
            // TODO use https
            //cookie.setSecure(true);
            cookie.setComment("Supply autologin for jetwick");
            // two weeks
            cookie.setMaxAge(2 * 7 * 24 * 60 * 60);
            response.addCookie(cookie);

            SolrUser tmpUser = getTwitterSearch().getUser();
            if (tmpUser != null) {
                tmpUser.setTwitterToken(getTwitterSearch().getCredits().getToken());
                tmpUser.setTwitterTokenSecret(getTwitterSearch().getCredits().getTokenSecret());
                uSearch.save(tmpUser, true);
                logger.info("[stats] user login:" + tmpUser.getScreenName());
                setUser(tmpUser);
            } else
                throw new IllegalStateException("user from twitterSearch cannot be null");

            return cookie;
        } catch (TwitterException ex) {
            logger.error("Couldn't change twitterSearch user" + ex.getMessage());
            return null;
        }
    }

    public void logout(SolrUserSearch uSearch, WebResponse response) {
        if (cookie != null)
            response.clearCookie(cookie);

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
