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

import de.jetwick.tw.TwitterSearch;
import org.apache.wicket.Page;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.protocol.http.WebResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.auth.AccessToken;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class Login extends JetwickPage {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    public final static String CALLBACK = "callback";
    public final static String LOGOUT = "logout";
    public final static String TW_VERIFIER = "oauth_verifier";
    public final static String TW_TOKEN = "oauth_token";

    public Login(PageParameters parameters) {
        boolean slide = false;
        String callback = parameters.getString(CALLBACK);
        if ("true".equals(callback)) {
            // redirect to remove ugly and insecure data
            setRedirect(true);
            boolean success = initCallback(parameters);
            parameters.remove(CALLBACK);
            parameters.remove(TW_VERIFIER);
            parameters.remove(TW_TOKEN);

            if (success) {
                success(false, parameters);
            } else
                // we want to have the error message so don't use "Login.class, parameters"
                setResponsePage(new Login(parameters));

            return;
        }
        boolean logout = parameters.getBoolean(LOGOUT);
        if (logout) {
            getMySession().logout(uindexProvider.get(), (WebResponse) getResponse(), true);
            parameters.remove(LOGOUT);
            info("Successfully logged out!");
            success(slide, parameters);
        }

        final String appName;
        final Class<? extends Page> page;
        appName = "Jetwick";
        page = TweetSearchPage.class;

        Link backLink = new Link("backLink") {

            @Override
            public void onClick() {
                setResponsePage(page);
            }
        };
        add(backLink);
        backLink.add(new Label("appName", appName));
        add(new Label("appName", appName));
        add(new Label("title", "Login to " + appName));
        add(new FeedbackPanel("feedback"));
        add(new LoginPanel("login", parameters));
    }

    private boolean initCallback(PageParameters parameters) {
        // when log out on error then do not invalidate session as we don't want to get session timeout
        try {
            logger.info("Received callback via slide=false Session=" + getSession().getId());
            String oAuthVerifier = parameters.getString(TW_VERIFIER);
            if (oAuthVerifier == null)
                throw new IllegalArgumentException("Cannot get authorization data from twitter");

            AccessToken token = getTwitterSearch().oAuthOnCallBack(oAuthVerifier);
            getMySession().afterLogin(token, uindexProvider.get(), (WebResponse) getResponse());
            return true;
        } catch (IllegalArgumentException ex) {
            logger.error(ex.getMessage());
            error(ex.getMessage());
            getMySession().logout(uindexProvider.get(), (WebResponse) getResponse(), false);
        } catch (Exception ex) {
            logger.error("Error while receiving callback.", ex);
            String msg = TwitterSearch.getMessage(ex);
            if (msg.length() > 0)
                error(msg);
            else
                error("Error when getting information from twitter! Please try again!");
            getMySession().logout(uindexProvider.get(), (WebResponse) getResponse(), false);
        }
        return false;
    }

    private void success(boolean slide, PageParameters parameters) {
        // 1. avoid showing the url parameters (e.g. refresh would let it fail)
        // 2. setResponsePage(TweetSearchPage.class); fails sometimes for firefox!?        
        setResponsePage(TweetSearchPage.class, parameters);
    }
}
