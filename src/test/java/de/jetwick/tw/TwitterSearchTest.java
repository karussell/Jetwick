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

package de.jetwick.tw;

import de.jetwick.data.YUser;
import de.jetwick.util.Helper;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import twitter4j.Tweet;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TwitterSearchTest {

    public TwitterSearchTest() {
    }

    @Test
    public void testUtf8NotXmlCompatible() {
        // takem from http://twitter.com/szeyan1220/status/14805768527
        String str = "@aiww ￿装逼犯们听着";
        String str2 = Helper.xmlCharacterWhitelist(str);
        assertTrue(str.length() > str2.length());
    }

    public TwitterSearch emptyTwitter() {
        return new TwitterSearch(new Credits()) {

            @Override
            public TwitterSearch init() {
                return null;
            }

            @Override
            public int getRateLimit() {
                return 150;
            }

            @Override
            public Collection<Tweet> updateUserInfo(List<YUser> users) {
                return Collections.EMPTY_LIST;
            }
        };
    }

    @Test
    public void testToUser() throws Exception {
        Twitter4JTweet tw = new Twitter4JTweet(1L, "test", "peter");
        assertNotNull(TwitterSearch.toUser(tw));
        assertNull(TwitterSearch.toUser(tw).getTwitterId());
    }

    @Test
    public void testLocation() {
        assertEquals("Berlin, -", TwitterSearch.toStandardLocation("Berlin"));
        assertEquals("Berlin, Germany", TwitterSearch.toStandardLocation("Berlin, Germany"));
        assertEquals("Mountain View, CA", TwitterSearch.toStandardLocation("Mountain View, CA"));
        assertEquals("Scotland, SF", TwitterSearch.toStandardLocation("Scotland / SF"));
        assertEquals("A, B  C", TwitterSearch.toStandardLocation("A,B,  C"));
        assertEquals(null, TwitterSearch.toStandardLocation(""));
    }
}
