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

import de.jetwick.data.Dao;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.wideplay.warp.persist.Transactional;
import de.jetwick.data.DbObject;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;

/**
 * The main data access layer class for hibernate.
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public abstract class AbstractDaoHib<T extends DbObject> implements Dao<T> {

    @Inject
    protected Provider<Session> sessp;
    private Class<T> clazz;

    @Inject
    public AbstractDaoHib(Class<T> clazz, Provider<Session> ocp) {
        this.clazz = clazz;
        this.sessp = ocp;
    }

    public Session sess() {
        return sessp.get();
    }

    @Transactional
    @Override
    public void save(T o) {
        sess().save(o);
    }

    @Transactional
    public void createOrUpdate(T o) {
        sess().saveOrUpdate(o);
    }

    // difference between saveOrUpdate and merge?
    // https://forum.hibernate.org/viewtopic.php?t=957265
    // http://docs.jboss.org/hibernate/entitymanager/3.5/reference/en/html/objectstate.html
    // http://scbcd.blogspot.com/2007/01/hibernate-persistence-lifecycle.html
    // http://jpa.ezhibernate.com/Javacode/learn.jsp?tutorial=07howhibernateworks
    @Transactional
    @Override
    public void saveAll(Collection<T> coll) {
        for (T o : coll) {
            try {
                sess().saveOrUpdate(o);
//                sess().merge(o);
            } catch (Exception e) {
                throw new IllegalStateException("Merging failed on:" + o, e);
            }
        }
    }

    @Transactional
    @Override
    public void delete(T persistentObject) {
        sess().delete(persistentObject);
    }

    @Transactional
    @Override
    public void deleteAll() {
        // the following code is faster but fails some more times with an contraintviolationexception 
        String hqlUpdate = "delete " + getType().getName();
        sess().createQuery(hqlUpdate).executeUpdate();

//        for (T t : findAll()) {
//            sess().delete(t);
//            if(counter % 100 == 0)
//                session.flush();
//        }
    }

    @Transactional
    @Override
    public List<T> findAll() {
        return sess().createCriteria(clazz).list();
    }

    @Transactional
    @Override
    public List<T> findAll(int start, int rows) {
        return sess().createCriteria(clazz).
                setFirstResult(start).
                setMaxResults(rows).list();
    }

    @Transactional
    @Override
    public long countAll() {
        // see countAllOutOfDate for a different method
        return (Long) sess().createQuery("select count(*) from " + clazz.getName()).iterate().next();
    }

    @Override
    public T findByName(String name) {
        throw new IllegalStateException("not impl");
    }

    @Override
    public List<T> findByNames(Collection<String> name) {
        throw new IllegalStateException("not impl");
    }

    @Transactional
    @Override
    public T findById(Object id) {
        if (id == null)
            return null;

        return (T) sess().get(clazz, (Serializable) id);
    }

    @Transactional
    public T findExactHitBy(String attribute, Object value) {
        return (T) sess().createCriteria(clazz).add(
                Restrictions.eq(attribute, value)).uniqueResult();
    }

    @Override
    public boolean isAttached(T obj) {
        return obj.getId() != null;
    }

    @Override
    public Class<T> getType() {
        return clazz;
    }

    @Override
    public void getInfo(StringBuilder sb) {
        sb.append("all ").append(getType().getSimpleName());
        sb.append("\t");
        sb.append(countAll());
        sb.append("\n");
    }
}
