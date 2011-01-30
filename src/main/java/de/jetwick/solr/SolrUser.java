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
import de.jetwick.tw.TwitterSearch;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import twitter4j.Tweet;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SolrUser extends YUser {

    private static final long serialVersionUID = 1L;
    private Collection<SolrTweet> ownTweets = new LinkedHashSet<SolrTweet>();
    private Map<Long, SavedSearch> savedSearches = new LinkedHashMap<Long, SavedSearch>();
    private Date lastFriendsUpdate;
    private Collection<String> friends;

    /**
     * You'll need to call setTwitter4JInstance after this
     */
    public SolrUser(String name) {
        super(name);
    }

    public SolrUser init(Tweet tw) {
        setProfileImageUrl(tw.getProfileImageUrl());
        setLocation(TwitterSearch.toStandardLocation(tw.getLocation()));
        return this;
    }

    public void addSavedSearch(SavedSearch ss) {
        savedSearches.put(ss.getId(), ss);
    }

    public boolean removeSavedSearch(long ssId) {
        return savedSearches.remove(ssId) != null;
    }

    public SavedSearch getSavedSearch(long id) {
        return savedSearches.get(id);
    }

    public Collection<SavedSearch> getSavedSearches() {
        return savedSearches.values();
    }

    public void addOwnTweet(SolrTweet tw) {
        addOwnTweet(tw, true);
    }

    public void addOwnTweet(SolrTweet tw, boolean reverse) {
        ownTweets.add(tw);
//        dirtyOwnTweets = true;

        if (reverse)
            tw.setFromUser(this, false);
    }

    public void deleteOwnTweet(SolrTweet tw) {
        ownTweets.remove(tw);
    }

    public Collection<SolrTweet> getOwnTweets() {
//        if (dirtyOwnTweets) {
//            SolrTweet.deduplicate(ownTweets);
//            dirtyOwnTweets = false;
//        }
        return Collections.unmodifiableCollection(ownTweets);
    }

    public Collection<String> getFriends() {
        if(friends == null)
            return Collections.EMPTY_LIST;
        return Collections.unmodifiableCollection(friends);
    }

    public SolrUser setFriends(Collection<String> friends) {
        this.friends = friends;
        return this;
    }

    public Date getLastFriendsUpdate() {
        return lastFriendsUpdate;
    }

    public void setLastFriendsUpdate(Date lastFriendsUpdate) {
        this.lastFriendsUpdate = lastFriendsUpdate;
    }
}
