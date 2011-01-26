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
package de.jetwick.es;

import java.util.Collection;
import org.apache.lucene.analysis.Tokenizer;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TokenizerFromSetTest {

    public TokenizerFromSetTest() {
    }

    @Test
    public void testIncrementToken() throws Exception {
        Tokenizer ts = new TokenizerFromSet(Arrays.asList("test", "pest", "fest").iterator());
        Collection<String> res = new TweetESQuery().doSnowballStemming(ts);
        assertEquals(3, res.size());
        assertTrue(res.contains("test"));
        assertTrue(res.contains("pest"));
        assertTrue(res.contains("fest"));
    }
}