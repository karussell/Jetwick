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

import de.jetwick.util.Helper;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetUserExtractorTest {

    public TweetUserExtractorTest() {
    }

    @Test
    public void testGetUsers() {
        assertEquals(1, new TweetUserExtractor().setText("@notfail").run().getUsers().size());
        assertEquals(0, new TweetUserExtractor().setText("@ fail").run().getUsers().size());
        assertEquals(0, new TweetUserExtractor().setText("@?").run().getUsers().size());

        assertEquals(1, new HashSet<String>(new TweetUserExtractor().setText("@user @User").run().getUsers().values()).size());

        Map<Integer, String> list = new TweetUserExtractor().setText(Helper.fixForUserInput("@<B>user</B>")).run().getUsers();
        assertEquals(1, list.size());
        assertEquals("user", list.get(3));

        assertEquals("@peter_nitsch", Helper.stripOutLuceneHighlighting("@<B>peter</B>_nitsch"));
    }
}
