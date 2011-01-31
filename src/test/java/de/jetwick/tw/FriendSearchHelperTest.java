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

import java.util.ArrayList;
import de.jetwick.solr.SolrUser;
import java.util.Collection;
import org.junit.Before;
import de.jetwick.JetwickTestClass;
import de.jetwick.util.MyDate;
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
            public void updateUser(SolrUser user) {                
            }
            
            @Override
            public void updateFromTwitter(Collection<String> friends, String screenName) {
                friends.add("test");
            }
        };
        assertEquals(1, helper.updateFriendsOf(new SolrUser("test")).size());
    }
    
    @Test
    public void testDayRefresh() {
        final Collection<String> list = new ArrayList<String>();
        FriendSearchHelper helper = new FriendSearchHelper(null, null) {

            @Override
            public void updateUser(SolrUser user) {                
                list.add("now");
            }
            
            @Override
            public void updateFromTwitter(Collection<String> friends, String screenName) {            
            }
        };
        
        SolrUser user = new SolrUser("peter");
        helper.updateFriendsOf(user);        
        assertEquals(1, list.size());        
        
        helper.updateFriendsOf(user);        
        assertEquals(1, list.size());
        
        // force 'aging' of user update
        user.setLastFriendsUpdate(new MyDate().minusDays(4).toDate());        
        helper.updateFriendsOf(user);        
        assertEquals(2, list.size());
        
        user.setLastFriendsUpdate(new MyDate().minusDays(3).toDate());        
        helper.updateFriendsOf(user);        
        assertEquals(2, list.size());
    }
}