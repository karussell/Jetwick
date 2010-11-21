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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class HashtagExtractorTest {

    public HashtagExtractorTest() {
    }

    @Test
    public void testGetTags() {
        assertEquals(0, new HashtagExtractor().setText("").run().getHashtags().size());

        HashtagExtractor extractor = new HashtagExtractor().setText("nohash #validhash #validHash #Validhash #anotherhash").run();
        assertEquals(2, extractor.getHashtags().size());
        assertTrue(extractor.getHashtags().contains("validhash"));
        assertTrue(extractor.getHashtags().contains("anotherhash"));

        extractor = new HashtagExtractor().setText("#validhash #valid-2.hash x#invalid-2.hash2").run();
        assertEquals(2, extractor.getHashtags().size());
        assertTrue(extractor.getHashtags().contains("validhash"));
        assertTrue(extractor.getHashtags().contains("valid-2.hash"));
    }
}
