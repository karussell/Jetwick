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
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public interface Dao<T extends DbObject> {

    void delete(T o);

    void deleteAll();

    /**
     * Creates a new entity
     */
    void save(T o);

    void saveAll(Collection<T> coll);

    boolean isAttached(T o);

    List<T> findAll(int first, int count);

    List<T> findAll();

    /**
     * Should be implemented as a O(1) method!
     */
    long countAll();

    T findById(Object id);

    T findByName(String str);

    /**
     * WARNING: if not found the object won't be null!
     */
    Collection<T> findByNames(Collection<String> names);

    void getInfo(StringBuilder sb);

    Class<T> getType();
}
