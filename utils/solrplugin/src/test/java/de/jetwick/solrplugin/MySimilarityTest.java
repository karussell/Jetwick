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

package de.jetwick.solrplugin;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class MySimilarityTest {

    public MySimilarityTest() {
    }

    @Test
    public void testLengthNorm() {
        MySimilarity s = new MySimilarity();
        assertEquals(0, s.lengthNorm("test", 0), 1e-6);
        assertEquals(0.289400, s.lengthNorm("test", 10), 1e-6);
        assertEquals(0.234860, s.lengthNorm("test", 19), 1e-6);
        assertEquals(0.223606, s.lengthNorm("test", 20), 1e-6);
    }
}
