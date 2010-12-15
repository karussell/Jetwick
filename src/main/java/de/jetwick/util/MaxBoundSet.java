/*
 *  Copyright 2010 Peter Karich jetwick_@_pannous_._info
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetwick.util;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A set which ignores element with an age older than maxAge when calling
 * contains or returning from add method.
 * At the same time it will ensure that the specified maxSize is not exceeded.
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class MaxBoundSet<T> {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private int maxSize;
    private int minSize;
    private long maxAge;
    private Map<T, Long> objMap = new LinkedHashMap<T, Long>();
    private final ReentrantLock lock = new ReentrantLock(true);

    public MaxBoundSet(int minSize, int maxSize) {
        this.maxSize = maxSize;
        this.minSize = minSize;
    }

    public MaxBoundSet<T> setMinSize(int minSize) {
        this.minSize = minSize;
        return this;
    }

    public MaxBoundSet<T> setMaxSize(int maxSize) {
        this.maxSize = maxSize;
        return this;
    }

    /**
     * @param maxAge is in seconds
     */
    public MaxBoundSet<T> setMaxAge(long maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    /**
     * @return true if no existing element exists or if existing element
     * is older than maxAge (and is ignored).
     */
    public boolean add(T t) {
        if (maxSize == 0)
            return true;

        if (objMap.size() + 1 > maxSize)
            clean();

        long now = System.currentTimeMillis();
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Long lastTime = objMap.put(t, now);
            // return true if previous element was null or if previous element was too old
            if (lastTime != null && now - lastTime <= maxAge)
                return false;
            else
                return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reduces the current size of the set to minSize
     */
    public void clean() {
        logger.info("clean! " + objMap.size());
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int size = objMap.size();
            List<Entry<T, Long>> res = Helper.sortLong(objMap.entrySet());
            Iterator<Entry<T, Long>> iter = res.iterator();
            for (int i = size; i >= minSize; i--) {
                objMap.remove(iter.next().getKey());
            }
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        return objMap.size();
    }

    public int getSize() {
        return objMap.size();
    }

    @Override
    public String toString() {
        return objMap.toString();
    }
}
