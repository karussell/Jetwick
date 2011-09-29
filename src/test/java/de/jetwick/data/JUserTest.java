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
package de.jetwick.data;

import de.jetwick.util.Helper;
import java.util.Date;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class JUserTest {

    public JUserTest() {
    }

    @Test
    public void testCtor() {
        assertEquals("peter", new JUser("Peter").getScreenName());
    }

    @Test
    public void testOwnTweets() {
        JUser user = new JUser("Peter");
        new JTweet(1, "1", user);
        new JTweet(1, "1", user);
        assertEquals(1, user.getOwnTweets().size());
        assertEquals(user, user.getOwnTweets().iterator().next().getFromUser());
    }
//    @Test
//    public void testSortedTweets() {
//        SolrUser user = new SolrUser("Peter");
//        user.addOwnTweet(new SolrTweet(1, "1", user));
//        user.addOwnTweet(new SolrTweet(3, "3", user));
//        user.addOwnTweet(new SolrTweet(2, "2", user));
//
//        Iterator<SolrTweet> iter = user.getOwnTweets().iterator();
//        assertEquals(3, (long) iter.next().getTwitterId());
//        assertEquals(2, (long) iter.next().getTwitterId());
//
//        user = new SolrUser("Peter");
//        user.addOwnTweet(new SolrTweet(14054091404L, "May", user));
//        user.addOwnTweet(new SolrTweet(9930228724L, "March", user));
//
//        iter = user.getOwnTweets().iterator();
//        assertEquals(14054091404L, (long) iter.next().getTwitterId());
//        assertEquals(9930228724L, (long) iter.next().getTwitterId());
//    }
//    @Test
//    public void testNoDuplicateTweets() {
//        SolrUser user = new SolrUser("Peter");
//        user.addOwnTweet(new SolrTweet(new Long(123523523522L), "1", user));
//        user.addOwnTweet(new SolrTweet(new Long(223523523522L), "tweet 2", user));
//        user.addOwnTweet(new SolrTweet(new Long(123523523522L), "tweet 1", user));
//        user.addOwnTweet(new SolrTweet(new Long(223523523522L), "tweet 2", user));
//
//        Collection<SolrTweet> tweets = user.getOwnTweets();
//        assertEquals(2, tweets.size());
//        Iterator<SolrTweet> iter = tweets.iterator();
//        assertEquals(223523523522L, (long) iter.next().getTwitterId());
//        assertEquals(123523523522L, (long) iter.next().getTwitterId());
//    }
}
