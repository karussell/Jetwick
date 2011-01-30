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
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class FriendSearchHelper {

    @Inject
    public TwitterSearch twitter4j;
    @Inject
    public ElasticUserSearch userSearch;

    public Collection<String> getFriendsOf(String screenName) {
        SolrUser user = userSearch.findByScreenName(screenName);
        if(user == null)
            return Collections.EMPTY_LIST;
        
        MyDate cacheTime = null;
        if (user.getLastFriendsUpdate() != null)
            cacheTime = new MyDate(user.getLastFriendsUpdate());
        
        if (cacheTime == null || new MyDate().minus(cacheTime).getDays() > 3) {
            final Collection<String> friends = new ArrayList<String>();
            cacheTime = new MyDate();
            twitter4j.getFriends(screenName, new AnyExecutor<SolrUser>() {

                @Override
                public SolrUser execute(SolrUser u) {
                    friends.add(u.getScreenName());
                    return u;
                }
            });

            user.setLastFriendsUpdate(cacheTime.toDate());
            user.setFriends(friends);
            userSearch.update(user, false, true);
            return friends;
        } else 
            return user.getFriends();
        
    }
}
