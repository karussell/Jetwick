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

import com.google.inject.Module;
import de.jetwick.config.DefaultModule;
import java.util.ArrayList;
import de.jetwick.data.JUser;
import java.util.Collection;
import org.junit.Before;
import de.jetwick.JetwickTestClass;
import de.jetwick.util.GenericUrlResolver;
import de.jetwick.util.MyDate;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class FriendSearchHelperTest extends JetwickTestClass {

    public FriendSearchHelperTest() {
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testGetFriendsOf() {
        FriendSearchHelper helper = new FriendSearchHelper(null, null) {

            @Override
            public void updateUser(JUser user) {
            }

            @Override
            public void updateFromTwitter(Collection<String> friends, String screenName, int max) {
                friends.add("test_fest");
                friends.add("test_pest");
            }
        };
        assertEquals(2, helper.updateFriendsOf(new JUser("test")).size());
    }

    @Test
    public void testDayRefresh() {
        final Collection<String> list = new ArrayList<String>();
        FriendSearchHelper helper = new FriendSearchHelper(null, null) {

            @Override
            public void updateUser(JUser user) {
                list.add("now");
            }

            @Override
            public void updateFromTwitter(Collection<String> friends, String screenName, int max) {
                friends.add("add_at_least_one_friend");
            }
        };

        JUser user = new JUser("peter");
        helper.updateFriendsOf(user);
        assertEquals(1, list.size());

        helper.updateFriendsOf(user);
        assertEquals(1, list.size());

        // force 'aging' of user update
        user.setLastFriendsUpdate(new MyDate().minusDays(4).toDate());
        helper.updateFriendsOf(user);
        assertEquals(2, list.size());

        user.setLastFriendsUpdate(new MyDate().minusDays(3).toDate());
        assertEquals(1, helper.updateFriendsOf(user).size());
        assertEquals(2, list.size());
    }

    @Test
    public void testOnTwitterException() {
        final Collection<String> list = new ArrayList<String>();
        FriendSearchHelper helper = new FriendSearchHelper(null, null) {

            @Override
            public void updateUser(JUser user) {
                list.add("now");
            }

            @Override
            public void updateFromTwitter(Collection<String> friends, String screenName, int max) {
                throw new RuntimeException();
            }
        };

        JUser user = new JUser("peter");
        user.setFriends(Arrays.asList("test", "pest"));
        assertEquals(2, helper.updateFriendsOf(user).size());
        assertEquals(2, user.getFriends().size());
    }

    @Override
    public Module createModule() {
        return new DefaultModule() {

            @Override
            public void installSearchModule() {
                // avoid that we need to set up (user/tweet) search
            }

            @Override
            public GenericUrlResolver createGenericUrlResolver() {
                return null;
            }
        };
    }
}