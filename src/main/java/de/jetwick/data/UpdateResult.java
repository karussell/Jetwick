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
import java.util.LinkedHashSet;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class UpdateResult {

    private Collection<JTweet> removedTweets;
    private Collection<JTweet> updatedTweets;

    public UpdateResult() {
        updatedTweets = new LinkedHashSet<JTweet>();
        removedTweets = new LinkedHashSet<JTweet>();
    }

    public Collection<JTweet> getUpdatedTweets() {
        return updatedTweets;
    }

    public boolean addUpdatedTweet(JTweet tweet) {
        if (tweet != null && !removedTweets.contains(tweet))
            return updatedTweets.add(tweet);

        return false;
    }

    public void addUpdatedTweets(Collection<JTweet> list) {
        for (JTweet tw : list) {
            addUpdatedTweet(tw);
        }
    }

    public Collection<JTweet> getDeletedTweets() {
        return removedTweets;
    }

    public void addAllDeletedTweets(Collection<JTweet> removedTweets) {
        this.removedTweets.addAll(removedTweets);
    }

    public void addAll(UpdateResult res) {
        addAllDeletedTweets(res.getDeletedTweets());
        for (JTweet tw : res.getUpdatedTweets()) {
            addUpdatedTweet(tw);
        }
    }

    public boolean isEmpty() {
        return removedTweets.isEmpty() && updatedTweets.isEmpty();
    }

    @Override
    public String toString() {
        return "new:" + updatedTweets.size() + " rm:" + removedTweets.size();
    }
}
