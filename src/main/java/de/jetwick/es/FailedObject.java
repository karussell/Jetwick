/*
 * Copyright 2011 Peter Karich, jetwick_@_pannous_._info.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetwick.es;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class FailedObject<T> {

    private long queuedTime;
    private T object;

    public FailedObject(long queuedTime, T o) {
        this.queuedTime = queuedTime;
        this.object = o;
    }

    public T getObject() {
        return object;
    }

    public long getQueuedTime() {
        return queuedTime;
    }

    @Override
    public String toString() {
        return getQueuedTime() / 1000f + " " + object;
    }
}
