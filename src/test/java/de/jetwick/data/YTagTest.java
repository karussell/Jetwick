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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class YTagTest {

    @Test
    public void testOptimizeQueryFrequency() {
        YTag st = new YTag("java");
        st.optimizeQueryFrequency(10);
        assertTrue(st.getQueryInterval() > 1000);
        st.optimizeQueryFrequency(1);
        assertTrue(st.getQueryInterval() > 1000);
        st.optimizeQueryFrequency(100);
        assertTrue(st.getQueryInterval() > 1000);
        st.optimizeQueryFrequency(1000);
        assertTrue(st.getQueryInterval() > 1000);
    }

    @Test
    public void testCompare() {
        YTag tag1 = new YTag("java");
        tag1.optimizeQueryFrequency(100);
        tag1.setLastMillis(System.currentTimeMillis());
        YTag tag2 = new YTag("solr");
        tag2.optimizeQueryFrequency(1);
        tag2.setLastMillis(System.currentTimeMillis());
        
        assertTrue(tag2.compareTo(tag1) > 0);
    }
}
