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
import de.jetwick.solr.SolrUser;
import de.jetwick.util.AnyExecutor;
import de.jetwick.util.MyDate;
import java.util.ArrayList;
import java.util.Collection;
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

    public Collection<String> getFriendsOf(SolrUser user) {        
        MyDate cacheTime = null;
        if (user.getLastFriendsUpdate() != null)
            cacheTime = new MyDate(user.getLastFriendsUpdate());

        if (cacheTime == null || new MyDate().minus(cacheTime).getDays() > 3) {
            final Collection<String> friends = new ArrayList<String>();
            cacheTime = new MyDate();
            try {
                updateFromTwitter(friends, user.getScreenName());
                user.setLastFriendsUpdate(cacheTime.toDate());
            } catch (Exception ex) {
                logger.error("Error while getting friends for " + user.getScreenName(), ex);
            }
            logger.info("Grab " + friends.size() + " friends for " + user.getScreenName());
            user.setFriends(friends);
            updateUser(user);
            return friends;
        } else
            return user.getFriends();
    }

    public void updateUser(SolrUser user) {
        // avoid refresh if more users are registered
        userSearch.update(user, false, true);
    }

    public void updateFromTwitter(final Collection<String> friends, String screenName) {
        twitter4j.getFriends(screenName, new AnyExecutor<SolrUser>() {

            @Override
            public SolrUser execute(SolrUser u) {
                friends.add(u.getScreenName());
                return u;
            }
        });
    }
}
