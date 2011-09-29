/*
 * Copyright 2011 Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetwick.tw;

import com.google.inject.Inject;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.data.JUser;
import de.jetwick.util.AnyExecutor;
import de.jetwick.util.MyDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class FriendSearchHelper {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private ElasticUserSearch userSearch;
    private TwitterSearch twitter4j;

    @Inject
    public FriendSearchHelper(ElasticUserSearch userSearch, TwitterSearch twitter4j) {
        this.userSearch = userSearch;
        this.twitter4j = twitter4j;
    }

    public Collection<String> updateFriendsOf(JUser user) {
        Date cacheTime = user.getLastFriendsUpdate();
        if (cacheTime == null || new MyDate().minus(cacheTime.getTime()).getDays() > 3) {
            final Collection<String> friends = new ArrayList<String>();            
            try {
                updateFromTwitter(friends, user.getScreenName(), 1000);
                if (friends.size() > 0) {
                    user.setLastFriendsUpdate(new Date());
                    user.setFriends(friends);
                    updateUser(user);
                    logger.info("Grabbed " + friends.size() + " friends for " + user.getScreenName() + " cacheTime:" + cacheTime);
                }
            } catch (Exception ex) {
                logger.error("Error while getting friends for " + user.getScreenName() + " Message:" + TwitterSearch.getMessage(ex));
            }
        }

        return user.getFriends();
    }

    public void updateUser(JUser user) {
        // avoid refresh
        userSearch.update(user, false, false);
    }

    public void updateFromTwitter(final Collection<String> friends, final String screenName, final int max) {
        twitter4j.getFriends(screenName, new AnyExecutor<JUser>() {

            @Override
            public JUser execute(JUser u) {
                friends.add(u.getScreenName());
                if (friends.size() > max) {
                    logger.error("Reached maximum number of friends for " + screenName + ". last friend:" + u.getScreenName());
                    return null;
                }

                return u;
            }
        });
    }
}
