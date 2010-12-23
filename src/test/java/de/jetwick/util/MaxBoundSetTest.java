/*
 *  Copyright 2010 Peter Karich jetwick_@_pannous_._info
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetwick.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class MaxBoundSetTest {

    public MaxBoundSetTest() {
    }

    @Test
    public void testAdd() {
        MaxBoundSet<String> set = new MaxBoundSet<String>(1, 2);
        assertEquals(true, set.add("1"));
        assertEquals(1, set.size());
        assertEquals(false, set.add("1"));
        assertEquals(1, set.size());

        assertEquals(true, set.add("2"));
        assertEquals(2, set.size());
        assertEquals(true, set.add("3"));
        assertEquals(1, set.size());
        assertEquals(true, set.add("4"));
        assertEquals(2, set.size());
        assertEquals(true, set.add("5"));
        assertEquals(1, set.size());
    }

    @Test
    public void testAddWithAge() throws InterruptedException {
        MaxBoundSet<String> set = new MaxBoundSet<String>(1, 2);
        set.setMaxAge(100);
        assertEquals(true, set.add("1"));
        assertEquals(1, set.size());
        assertEquals(false, set.add("1"));
        assertEquals(1, set.size());
        Thread.sleep(200);
        assertEquals(true, set.add("1"));
        assertEquals(1, set.size());
    }

    @Test
    public void testContainsAge() throws InterruptedException {
        MaxBoundSet<String> set = new MaxBoundSet<String>(1, 2);
        set.setMaxAge(100);
        set.add("test");
        assertEquals(true, set.contains("test"));
        assertEquals(true, set.contains("test"));
        Thread.sleep(200);
        assertEquals(false, set.contains("test"));
    }
}
