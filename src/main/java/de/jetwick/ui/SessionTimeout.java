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

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SessionTimeout extends WebPage {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public SessionTimeout(final PageParameters oldParams) {
        PageParameters newParams = new PageParameters();
        if (oldParams != null) {
            if (oldParams.getString("q") != null)
                newParams.add("q", oldParams.getString("q"));
            String userAsStr = oldParams.getString("user");
            if (userAsStr == null)
                userAsStr = oldParams.getString("u");
            if (userAsStr != null)
                newParams.add("user", userAsStr);
        }

        ((MySession) getSession()).setSessionTimedOut(true);
        logger.info("session timed out. old params: " + oldParams + " new params:" + newParams
                + " IP=" + getWebRequestCycle().getWebRequest().getHttpServletRequest().getRemoteHost()
                + " session=" + getWebRequestCycle().getSession().getId()
                + " cookie=" + getWebRequestCycle().getWebRequest().getCookie("jetwick JSESSIONID"));
        setResponsePage(HomePage.class, newParams);
    }
}
