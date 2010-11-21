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

import com.google.inject.Inject;
import de.jetwick.tw.Twitter4JTweet;
import de.jetwick.tw.TwitterSearch;
import de.jetwick.util.MyDate;
import java.util.Random;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public abstract class AbstractDbHelperTestClass {

    @Inject
    private UserDao userDao;    
    @Inject
    private TagDao tagDao;

    @Before
    public void setUp() throws Exception {
        getTester().setUp();
    }

    @After
    public void tearDown() throws Exception {
        getTester().tearDown();
    }

    public abstract DbTestInterface getTester();

    public abstract void commitAndReopenDB() throws Exception;

    @Test
    public void testSearchTag() {
        YTag st = new YTag("test");
        tagDao.incCounter(st.getTerm());
        assertEquals(1, tagDao.findAll().size());
        assertEquals(0, tagDao.findAll().get(0).getLastId());

        // no duplicate
        tagDao.incCounter("test");
        assertEquals(1, tagDao.findAll().size());
        assertEquals(0, tagDao.findAll().get(0).getLastId());

        // update
        st.setLastId(4);
        tagDao.createOrUpdate(st);
        assertEquals(1, tagDao.findAll().size());
        assertEquals(4, tagDao.findAll().get(0).getLastId());
    }

    @Test
    public void testCleanup() {
        YTag st = new YTag("Apache");
        tagDao.incCounter(st.getTerm());
        st = new YTag("Apache Solr");
        tagDao.incCounter(st.getTerm());
        assertEquals(2, tagDao.findAll().size());
        tagDao.cleanUp();
        assertEquals(1, tagDao.findAll().size());
        assertEquals("apache", tagDao.findAll().get(0).getTerm());
    }

    @Test
    public void testAddSearchTerm() throws Exception {
        YTag tag = tagDao.incCounter("test");
        assertEquals(1, (long) tag.getSearchCounter());

        commitAndReopenDB();
        tag = tagDao.incCounter("test");
        assertEquals(2, (long) tag.getSearchCounter());
    }

    @Test
    public void testStoreAfterFind() throws Exception {
        YTag tag = new YTag("test");
        tagDao.save(tag);
        assertEquals(0, (long) tag.getSearchCounter());
        commitAndReopenDB();
        tag = tagDao.findAll().get(0);
        tag.incSearchCounter();
        tagDao.save(tag);
        assertEquals(1, (long) tag.getSearchCounter());
        commitAndReopenDB();

        tag = tagDao.findAll().get(0);
        assertEquals(1, (long) tag.getSearchCounter());
        assertEquals("test", tag.getTerm());
    }

    @Test
    public void testStoreAfterIncrease() throws Exception {
        YTag tag = new YTag("test");
        tag.incSearchCounter();
        tagDao.save(tag);

        commitAndReopenDB();
        assertEquals("test", tagDao.findAll().get(0).getTerm());
        assertEquals(1, (long) tagDao.findAll().get(0).getSearchCounter());
    }

    @Test
    public void testPaginationList() throws Exception {
        userDao.add(new YUser("peter1"));
        userDao.add(new YUser("peter2"));
        userDao.add(new YUser("peter3"));
        userDao.add(new YUser("peter4"));
        userDao.add(new YUser("peter5"));
        userDao.add(new YUser("peter6"));
        commitAndReopenDB();

        assertEquals(5, userDao.findAll(0, 5).size());
        assertEquals("peter1", userDao.findAll(0, 5).get(0).getScreenName());
        assertEquals("peter5", userDao.findAll(0, 5).get(4).getScreenName());

        assertEquals(1, userDao.findAll(5, 5).size());
        assertEquals("peter6", userDao.findAll(5, 5).get(0).getScreenName());
    }
    
    YUser newRandU() {
        YUser u = new YUser("" + new Random().nextInt());
        userDao.save(u);
        return u;
    }
}
