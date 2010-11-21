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

import java.util.Collection;
import java.util.List;

/**
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public interface TagDao extends Dao<YTag> {

    void createOrUpdate(YTag st);

    void createOrUpdateAll(Collection<YTag> tags);

    /**
     * Avoid having several 'overlapping' tags and thus wasting search capacity.
     * E.g. the results of the twitter search against "Apache Solr" are
     * included in a search against "Apache"
     */
    void cleanUp();

    /**
     * adds the specified tags and updates existing.
     */
    void addAll(Collection<String> coll);

    boolean deleteByName(String str);

    YTag incCounter(String term);

    List<YTag> findAllSorted();
}
