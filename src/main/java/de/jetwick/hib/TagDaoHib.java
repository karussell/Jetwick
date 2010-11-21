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
import de.jetwick.data.TagDao;
import de.jetwick.data.YTag;
import java.util.Collection;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.criterion.Order;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TagDaoHib extends AbstractDaoHib<YTag> implements TagDao {

    @Inject
    public TagDaoHib(Provider<Session> ocp) {
        super(YTag.class, ocp);
    }

    /**
     * Add but avoid duplicate via findByName
     */    
    private YTag add(String term) {
        YTag oldTag = findByName(term);
        if (oldTag != null)
            return oldTag;

        YTag newTag = new YTag(term);
        save(newTag);
        return newTag;
    }

    @Transactional
    @Override
    public void addAll(Collection<String> coll) {
        for (String term : coll) {
            add(term);
        }
    }

    @Transactional
    @Override
    public YTag findByName(String name) {
        name = name.toLowerCase();
        return findExactHitBy(YTag.TERM, name);
    }

    @Transactional
    @Override
    public void createOrUpdateAll(Collection<YTag> tags) {
        for (YTag st : tags) {
            createOrUpdate(st);
        }
    }

    @Transactional
    @Override
    public void createOrUpdate(YTag st) {
        YTag oldTag = findByName(st.getTerm());
        if (oldTag != null)
            oldTag.update(st);
        else
            oldTag = st;

        save(oldTag);
    }

    /**
     * Avoid having several 'overlapping' tags and thus wasting search capacity.
     * E.g. the results of the twitter search against "Apache Solr" are
     * included in a search against "Apache"
     */
    @Transactional
    @Override
    public void cleanUp() {
        List<YTag> coll = findAll();
        for (YTag st1 : coll) {
            for (YTag st2 : coll) {
                if (st1 == st2)
                    continue;

                if (st1.getTerm().contains(st2.getTerm()))
                    delete(st1);
            }
        }
    }

    @Transactional
    @Override
    public boolean deleteByName(String str) {
        YTag tag = findByName(str);
        if (tag == null)
            return false;

        delete(tag);
        return true;
    }

    @Transactional
    @Override
    public YTag incCounter(String term) {
        YTag tag = add(term);
        tag.incSearchCounter();
        save(tag);
        return tag;
    }

    @Override
    public List<YTag> findAllSorted() {
        // smallest first
        return sess().createCriteria(getType()).addOrder(Order.asc("queryInterval")).list();
    }

}

