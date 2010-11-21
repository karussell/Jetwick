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

package de.jetwick.solr;

import de.jetwick.data.YUser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import twitter4j.Tweet;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SolrUser extends YUser {

    private float rep;
    private boolean dirtyOwnTweets = true;
    private List<SolrTweet> ownTweets = new ArrayList<SolrTweet>();

    public SolrUser(String name) {
        super(name);
    }

    public SolrUser(Tweet tw) {
        super(tw);
    }

    public void addOwnTweet(SolrTweet tw) {
        addOwnTweet(tw, true);
    }

    public void addOwnTweet(SolrTweet tw, boolean reverse) {
        ownTweets.add(tw);
        dirtyOwnTweets = true;

        if (reverse)
            tw.setFromUser(this, false);
    }

    public void deleteOwnTweet(SolrTweet tw) {
        ownTweets.remove(tw);
    }

    public Collection<SolrTweet> getOwnTweets() {
        if (dirtyOwnTweets) {
            SolrTweet.sortAndDeduplicate(ownTweets);
            dirtyOwnTweets = false;
        }
        return Collections.unmodifiableCollection(ownTweets);
    }

    public float getReputation() {
        return rep;
    }

    public void setReputation(float rep) {
        this.rep = rep;
    }
}
