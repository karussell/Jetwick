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

import java.util.Arrays;
import com.google.inject.Inject;
import de.jetwick.data.UserDao;
import de.jetwick.data.YUser;
import de.jetwick.util.MyDate;
import java.util.List;
import org.hibernate.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class UserDaoHibTest extends HibTestClass {

    @Inject
    private UserDao userDao;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testGetTweet() throws Exception {
        userDao.save(new YUser("test"));

        commitAndReopenDB();
        List<YUser> list = userDao.findAll();
        assertEquals(1, list.size());
    }

    @Test
    public void testFindByNames() throws Exception {
        YUser user = new YUser("test");
        YUser user2 = new YUser("test2");
        userDao.save(user);
        userDao.save(user2);

        assertEquals(2, userDao.findByNames(Arrays.asList("test", "unknown", "test2")).size());
    }

    @Test
    public void testFindAllOutOfDate() throws Exception {
        YUser user1 = new YUser("test1");
        YUser user2 = new YUser("test2");
        YUser user3 = new YUser("test3");

        userDao.save(user1);
        userDao.save(user2);
        userDao.save(user3);
        assertEquals(3, userDao.findAllOutOfDate(0, 10).size());
        assertEquals(3, userDao.countAllOutOfDate());

        user1.setUpdateAt(new MyDate().minusDays(YUser.OUT_OF_DATE_DAYS + 2).toDate());
        user3.setUpdateAt(new MyDate().minusDays(YUser.OUT_OF_DATE_DAYS - 2).toDate());
        userDao.save(user1);
        userDao.save(user3);
        List<YUser> list = userDao.findAllOutOfDate(0, 10);
        assertEquals(2, list.size());
        assertEquals(2, userDao.countAllOutOfDate());
        assertEquals("test1", list.get(0).getScreenName());
        assertEquals("test2", list.get(1).getScreenName());
    }

    @Test
    public void testFindAndSave() throws Exception {
        userDao.save(new YUser("karsten"));
        commitAndReopenDB();

        findAndSave();

        commitAndReopenDB();
        assertEquals(1, userDao.countAll());
        assertEquals("TEST", userDao.findAll().get(0).getDescription());
    }

    // The @Transactional does not work with injectMembers!? -> getInstance would be better
    // but isn't possible (because this test already exists)
    public void findAndSave() {
        Session s = ((UserDaoHib) userDao).sess();
        YUser u = (YUser) s.createQuery("FROM " + YUser.class.getName()).list().get(0);
        u.setDescription("TEST");
        userDao.save(u);
    }
}
