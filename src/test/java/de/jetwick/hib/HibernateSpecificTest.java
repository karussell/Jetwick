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

package de.jetwick.hib;

import de.jetwick.data.YUser;
import org.hibernate.Session;
import org.hibernate.StaleObjectStateException;
import org.hibernate.Transaction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class HibernateSpecificTest extends HibTestClass {

    @Override
    @Before
    public void setUp() throws Exception {
        cleanup();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        cleanup();
    }

    @Test
    public void testLock() {
        Session s1 = HibernateUtil.getSessionFactory().openSession();

        Long id;
        // store a user
        Transaction ta1 = s1.beginTransaction();
        try {
            YUser u = new YUser("test");
            s1.save(u);
            id = u.getId();
        } finally {
            ta1.commit();
        }

        ta1 = s1.beginTransaction();
        try {
            YUser u1 = (YUser) s1.load(YUser.class, id);
            YUser u2 = (YUser) s1.load(YUser.class, id);

            u1.setDescription("YEAH!");
            assertEquals("YEAH!", u2.getDescription());
        } finally {
            ta1.rollback();
        }

        s1.close();
    }

    @Test
    public void testLockDifferentSessions() {
        // emulate two different threads
        Session s1 = HibernateUtil.getSessionFactory().openSession();
        Session s2 = HibernateUtil.getSessionFactory().openSession();

        Long id;
        // store a user
        Transaction ta1 = s1.beginTransaction();
        try {
            YUser u = new YUser("test");
            s1.save(u);
            id = u.getId();
        } finally {
            ta1.commit();
        }

        ta1 = s1.beginTransaction();
        Transaction ta2 = s2.beginTransaction();
        try {
            YUser u1 = (YUser) s1.load(YUser.class, id);
            YUser u2 = (YUser) s2.load(YUser.class, id);

            u1.setDescription("overwrite me");
            u2.setDescription("YEAH!");
        } finally {
            ta1.commit();
            try {
                ta2.commit();
                fail("Shouldn't commit. Two different versions!");
            } catch (StaleObjectStateException ex) {
            }
        }

        s1.close();
        s2.close();
    }
}
