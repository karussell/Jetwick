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

package de.jetwick.ui;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class NavigationPanelTest extends WicketPagesTestClass {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testOnPageChange() {
        NavigationPanel panel = (NavigationPanel) tester.startPanel(NavigationPanel.class);
        panel.setHitsPerPage(15);

        panel.setHits(10);                
        panel.setPage(0);
        assertFalse(panel.isNextPossible());
        assertFalse(panel.isPreviousPossible());

        //
        panel.setHits(15);        
        panel.setPage(0);
        assertFalse(panel.isNextPossible());
        assertFalse(panel.isPreviousPossible());

        //
        panel.setHits(20);        
        panel.setPage(0);
        assertTrue(panel.isNextPossible());
        assertFalse(panel.isPreviousPossible());
        panel.setPage(1);
        assertFalse(panel.isNextPossible());
        assertTrue(panel.isPreviousPossible());

        //
        panel.setHits(50);
        panel.setPage(0);
        assertTrue(panel.isNextPossible());
        assertFalse(panel.isPreviousPossible());
        panel.setPage(1);
        assertTrue(panel.isNextPossible());
        assertTrue(panel.isPreviousPossible());
    }
}
