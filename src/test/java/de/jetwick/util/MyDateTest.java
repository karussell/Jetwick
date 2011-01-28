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
package de.jetwick.util;

import java.util.Date;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class MyDateTest {

    public MyDateTest() {
    }

    @Test
    public void testGetTime() {
        assertEquals(10L, new MyDate(10L).getTime());

        MyDate date = new MyDate(new Date());
        long old = date.toDays();
        assertEquals(old + 1, new MyDate(date).plusDays(1).toDays());
        assertEquals(old - 1, new MyDate(date).minusDays(1).toDays());
    }

    @Test
    public void testRound() {
        assertEquals(3660 * 1000, new MyDate(61 * MyDate.ONE_MINUTE).getTime());
        assertEquals(3600 * 1000, new MyDate(61 * MyDate.ONE_MINUTE).castToHour().getTime());
        assertEquals(3600 * 1000, new MyDate((60 + 59) * MyDate.ONE_MINUTE).castToHour().getTime());
    }
    
    @Test
    public void testGetHours() {
        assertEquals(2, new MyDate(2 * MyDate.ONE_HOUR).getHours());
        assertEquals(2, new MyDate((long)(2.9 * MyDate.ONE_HOUR)).getHours());        
    }
}
