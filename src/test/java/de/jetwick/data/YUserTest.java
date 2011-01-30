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

import java.util.Collection;
import de.jetwick.util.Helper;
import java.util.Date;
import java.util.Iterator;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class YUserTest {

    public YUserTest() {
    }

    @Test
    public void testCtor() {
        assertEquals("peter", new YUser("Peter").getScreenName());
    }

    @Test
    public void testCanBeUpdated() {
        YUser user = new YUser("Peter");

        assertTrue(user.isOutOfDate());

        user.setUpdateAt(new Date());
        assertFalse(user.isOutOfDate());

        user.setUpdateAt(Helper.plusDays(new Date(), -8));
        assertTrue(user.isOutOfDate());

        user.setUpdateAt(Helper.plusDays(new Date(), -7));
        assertFalse(user.isOutOfDate());
    }
}
