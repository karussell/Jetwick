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
import de.jetwick.data.YUser;
import de.jetwick.tw.TwitterSearch;
import org.apache.wicket.Request;
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
    private boolean dev = false;
    private YUser user = null;

    public MySession(Request request, boolean dev) {
        super(request);
        this.dev = dev;
    }

    public TwitterSearch getTwitterSearch() {
        return twitterSearch.init();
    }

    public YUser getUser() {
        if (dev) {
            try {
                user = getTwitterSearch().getUser();
            } catch (TwitterException ex) {
                logger.error("Couldn't init dev account!", ex);
            }
        }
        return user;
    }

    private void setUser(YUser user) {
        this.user = user;
        dirty();
    }

    public boolean hasLoggedIn() {
        return user != null;
    }

    public void setTwitterSearch(TwitterSearch ts) {
        twitterSearch = ts;
        try {
            YUser tmpUser = getTwitterSearch().getUser();
            logger.info("[stats] user login:" + tmpUser.getScreenName());
            setUser(tmpUser);
        } catch (TwitterException ex) {
            logger.error("Couldn't change");
        }
    }

    public void logout() {
        setUser(null);
    }
}
