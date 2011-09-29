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
        assertEquals(2, new MyDate((long) (2.9 * MyDate.ONE_HOUR)).getHours());
    }

    @Test
    public void getTimeAgo() {
        assertEquals("0 second ago", new MyDate().minus(123).getTimesAgo());
        assertEquals("5 seconds ago", new MyDate().minus((5 * MyDate.ONE_SECOND) + 123).getTimesAgo());
        assertEquals("3 hours ago", new MyDate().minus((3 * MyDate.ONE_HOUR)
                + (2 * MyDate.ONE_MINUTE) + MyDate.ONE_SECOND).getTimesAgo());
        assertEquals("1 hour ago", new MyDate().minus(MyDate.ONE_HOUR
                + (2 * MyDate.ONE_MINUTE) + MyDate.ONE_SECOND).getTimesAgo());
        assertEquals("42 seconds ago", new MyDate().minus(42 * MyDate.ONE_SECOND).getTimesAgo());
        
        // print full date if older than one day
        assertEquals(Helper.toSimpleDateTime(new MyDate().minusDays(1).minusHours(1).castToHour().toDate()),
                new MyDate().minus(MyDate.ONE_DAY + MyDate.ONE_HOUR).castToHour().getTimesAgo());
        assertEquals(Helper.toSimpleDateTime(new MyDate().minusDays(1).minus(2 * MyDate.ONE_SECOND).castToHour().toDate()),
                new MyDate().minus(MyDate.ONE_DAY + 2 * MyDate.ONE_SECOND).castToHour().getTimesAgo());
    }
}
