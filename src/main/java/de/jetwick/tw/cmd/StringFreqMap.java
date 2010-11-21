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

package de.jetwick.tw.cmd;

import de.jetwick.util.Helper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Holds string frequency
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class StringFreqMap extends LinkedHashMap<String, Integer> {

    public StringFreqMap() {
    }

    public StringFreqMap(Map<? extends String, ? extends Integer> m) {
        super(m);
    }

    public StringFreqMap(int initialCapacity) {
        super(initialCapacity);
    }

    public StringFreqMap set(String key, Integer count) {
        put(key, count);
        return this;
    }

    public StringFreqMap setAll(Map<String, Integer> map) {
        putAll(map);
        return this;
    }

//    public Set<String> and(Map<String, Integer> map) {
//        Set<String> res = new LinkedHashSet<String>();
//        Set<String> iterSet;
//        Set<String> otherSet;
//        if (size() > map.size()) {
//            iterSet = map.keySet();
//            otherSet = keySet();
//        } else {
//            iterSet = this.keySet();
//            otherSet = map.keySet();
//        }
//
//        for (String iterStr : iterSet) {
//            if (otherSet.contains(iterStr))
//                res.add(iterStr);
//        }
//
//        return res;
//    }
    public int andSize(Map<String, Integer> other) {
        Set<String> iterSet;
        Set<String> otherSet;
        if (size() > other.size()) {
            iterSet = other.keySet();
            otherSet = keySet();
        } else {
            iterSet = this.keySet();
            otherSet = other.keySet();
        }

        int counter = 0;
        for (String iterStr : iterSet) {
            if (otherSet.contains(iterStr))
                counter++;
        }
        return counter;
    }

    public int orSize(Map<String, Integer> map) {
        return or(map).size();
    }

    /**
     * Returns unsorted merge of all strings
     */
    public Set<String> or(Map<String, Integer> map) {
        Set<String> res = new LinkedHashSet<String>(keySet());
        for (String str : map.keySet()) {
            res.add(str);
        }

        return res;
    }

    public StringFreqMap addOne2All(Map<String, Integer> map) {
        for (Entry<String, Integer> e : map.entrySet()) {
            inc(e.getKey(), 1);
        }
        return this;
    }

    public StringFreqMap addValue2All(Map<String, Integer> map) {
        for (Entry<String, Integer> e : map.entrySet()) {
            inc(e.getKey(), e.getValue());
        }
        return this;
    }

    public boolean inc(String key, int val) {
        Integer integ = get(key);
        if (integ == null)
            integ = 0;

        put(key, integ + val);
        return true;
    }

    public List<Entry<String, Integer>> getSorted() {
        return Helper.sort(entrySet());
    }

    public List<Entry<String, Integer>> getSortedTermLimited(int termMaxCount) {
        List<Entry<String, Integer>> res = Helper.sort(entrySet());
        int min = Math.min(termMaxCount, res.size());
        return res.subList(0, min);
    }

    /**
     *
     * @param freq specifies the relative limit to the maximal frequency.
     * E.g. you have "a 10", "b 2", "c 1" and specifes percentage=0.2 (means 20%) then you
     * would get only "a 10", "b 2" (freq limit is inclusive)
     */
    public List<Entry<String, Integer>> getSortedFreqLimit(float freq) {
        if (size() == 0)
            return Collections.emptyList();

        List<Entry<String, Integer>> tmp = Helper.sort(entrySet());
        List<Entry<String, Integer>> res = new ArrayList<Entry<String, Integer>>();

        int cmpFreq = Math.round(freq * tmp.get(0).getValue());
        for (Entry<String, Integer> e : tmp) {
            if (e.getValue() >= cmpFreq)
                res.add(e);
        }
        return res;
    }
}
