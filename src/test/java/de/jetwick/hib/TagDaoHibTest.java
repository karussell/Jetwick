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

import com.google.inject.Inject;
import de.jetwick.data.TagDao;
import de.jetwick.data.YTag;
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
public class TagDaoHibTest extends HibTestClass {

    @Inject
    private TagDao tagDao;

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

    public Session newSess() {
        return HibernateUtil.getSessionFactory().openSession();
    }

    @Test
    public void testSave() throws Exception {
        tagDao.save(new YTag("test"));

        commitAndReopenDB();
        List<YTag> list = tagDao.findAll();
        assertEquals(1, list.size());
    }

    @Test
    public void testSortedFindAll() throws Exception {
        tagDao.save(new YTag("test1").setQueryInterval(100L));
        tagDao.save(new YTag("test2").setQueryInterval(10000L));
        tagDao.save(new YTag("test3").setQueryInterval(1000L));

        commitAndReopenDB();
        List<YTag> list = tagDao.findAllSorted();
        assertEquals("test1", list.get(0).getTerm());
        assertEquals("test3", list.get(1).getTerm());
        assertEquals("test2", list.get(2).getTerm());
    }
}
