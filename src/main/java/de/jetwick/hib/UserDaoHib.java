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
import com.google.inject.Provider;
import com.wideplay.warp.persist.Transactional;
import de.jetwick.data.UserDao;
import de.jetwick.data.YUser;
import de.jetwick.util.MyDate;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class UserDaoHib extends AbstractDaoHib<YUser> implements UserDao {

    @Inject
    public UserDaoHib(Provider<Session> ocp) {
        super(YUser.class, ocp);
    }

    @Transactional
    @Override
    public boolean add(YUser u) {
        YUser tmpUser = findByName(u.getScreenName());
        if (tmpUser == null) {
            save(u);
            return true;
        }

        return false;
    }

    @Override
    public YUser findByName(String name) {
        name = name.toLowerCase();

//        Query q = sess().createQuery("from " + YUser.class.getName()
//                + " as u where u." + YUser.SCREEN_NAME + " = :sn");
//        q.setParameter("sn", name);
        return findExactHitBy(YUser.SCREEN_NAME, name);
    }

    @Override
    public List<YUser> findByNames(Collection<String> names) {
        return sess().createCriteria(getType()).add(Restrictions.in(YUser.SCREEN_NAME, names)).list();
    }

    @Override
    public List<YUser> findEmptyUsers() {
        return sess().createCriteria(getType()).add(Restrictions.isEmpty(YUser.OWN_TWEETS)).
                list();
    }

    @Override
    public long countEmptyUsers() {
        return (Long) sess().createCriteria(getType()).add(Restrictions.isEmpty(YUser.OWN_TWEETS)).
                setProjection(Projections.rowCount()).list().get(0);
    }

    private Criteria createOutOfDateCriteria() {
        Date nowMinusSomeDays = new MyDate().minusDays(YUser.OUT_OF_DATE_DAYS).toDate();

        return sess().createCriteria(getType()).
                add(
                Restrictions.or(
                Restrictions.isNull(YUser.UPDATE_AT),
                Restrictions.le(YUser.UPDATE_AT, nowMinusSomeDays)));
    }

    @Override
    public List<YUser> findAllOutOfDate(int start, int rows) {
        return createOutOfDateCriteria().
                setFirstResult(start).
                setMaxResults(rows).list();
    }

    @Override
    public long countAllOutOfDate() {
        Criteria crit = createOutOfDateCriteria();
        crit.setProjection(Projections.rowCount());
        return (Long) crit.list().get(0);
    }
}
