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

package de.jetwick.ui.util;

import java.io.Serializable;

public class FacetHelper<T> implements Serializable {

    public String key;
    public T value;
    public String displayName;
    public long count;

    public FacetHelper(String filterKey, T filterValue, String displayName, long count) {
        this.key = filterKey;
        this.value = filterValue;
        this.displayName = displayName;
        this.count = count;
    }

    public String getFilter() {
        return key + ":" + value.toString();
    }

    @Override
    public String toString() {
        return displayName + " " + getFilter() + " (" + count + ")";
    }
}
