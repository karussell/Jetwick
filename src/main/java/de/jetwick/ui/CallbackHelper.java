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

import de.jetwick.data.YUser;
import de.jetwick.tw.TwitterSearch;
import org.apache.wicket.PageParameters;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.protocol.http.RequestUtils;
import org.apache.wicket.request.target.basic.RedirectRequestTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterException;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class CallbackHelper {

    private static final Logger logger = LoggerFactory.getLogger(CallbackHelper.class);

    public static TwitterSearch getParseTwitterUrl(TwitterSearch twitterSearch,
            final PageParameters parameters) throws TwitterException {
        String oAuthVerifier = parameters.getString("oauth_verifier");

        if (oAuthVerifier == null)
            throw new IllegalArgumentException("parameter oauth_verifier was null");

        twitterSearch.oAuthOnCallBack(oAuthVerifier);
        return twitterSearch;
    }

    public static AjaxLink createLink(String id, final HomePage page) {
        return new AjaxLink(id) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                String url;
                try {
                    logger.info("Clicked Login!");
                    PageParameters params = new PageParameters();
                    params.add("callback", "true");
                    String callbackUrl = RequestUtils.toAbsolutePath(urlFor(HomePage.class, params).toString());
                    url = page.getTwitterSearch().oAuthLogin(callbackUrl);
                    if (url != null)
                        getRequestCycle().setRequestTarget(new RedirectRequestTarget(url));
                } catch (Exception ex) {
                    logger.error("Cannot login!", ex);
                }
            }
        };
    }

    public static String getMessage(YUser user) {
        String realName = "";
        if (user.getRealName() != null)
            realName = user.getRealName().toLowerCase();
        String screenName = user.getScreenName().toLowerCase();
        if (realName.equals(screenName))
            return "Welcome " + user.getRealName() + "!";
        else
            return "Welcome " + user.getRealName() + " aka " + user.getScreenName() + "!";
    }
}
