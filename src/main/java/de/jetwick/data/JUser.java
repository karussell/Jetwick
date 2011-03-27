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

import de.jetwick.es.SavedSearch;
import de.jetwick.tw.TwitterSearch;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import twitter4j.Status;
import twitter4j.Tweet;
import twitter4j.User;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class JUser implements DbObject, Serializable {

    private static final long serialVersionUID = 1L;
    public static int OUT_OF_DATE_DAYS = 7;
    public static int DISPLAY_TWEETS = 2;
    public static final String UPDATE_AT = "updateAt";
    public static final String OWN_TWEETS = "ownTweets";
    public static final String SCREEN_NAME = "screenName";
    private Collection<JTweet> ownTweets = new LinkedHashSet<JTweet>();
    private Map<Long, SavedSearch> savedSearches = new LinkedHashMap<Long, SavedSearch>();
    private Date lastFriendsUpdate;
    private Collection<String> friends;
    private String realName;
    // we don't need an index here -> already via uniqueconstraint
    private String screenName;
    private Long twitterId;
    private String profileImageUrl;
    private String webUrl;
    private String location;
    private Set<String> langs = new LinkedHashSet<String>();
    private String description;
    private Date createdAt;
    private Date twitterCreatedAt;
    private Date updateAt;
    private Set<String> tags = new LinkedHashSet<String>();
    private String twitterTokenSecret;
    private String twitterToken;
    public static String ROLE_ADMIN = "admin";
    public static String ROLE_USER = "user";
    private String role = ROLE_USER;
    private String email;

    public JUser() {
        setCreatedAt(new Date());
    }

    public JUser(String name) {
        this();
        this.screenName = name.toLowerCase();
        if (screenName.trim().length() == 0)
            throw new IllegalArgumentException("Screenname must not be empty!");
    }

    @Override
    public String getId() {
        return getScreenName();
    }

    public Collection<String> getTags() {
        return tags;
    }

    public void addTag(String tag) {
        tags.add(tag);
    }

    public boolean isOutOfDate() {
        if (updateAt == null)
            return true;
        else {
            long diff = new Date().getTime() - updateAt.getTime();
            diff = Math.round(diff / (24f * 3600f * 1000f));
            return diff > OUT_OF_DATE_DAYS;
        }
    }

    public String getTwitterToken() {
        return twitterToken;
    }

    public JUser setTwitterToken(String twitterToken) {
        this.twitterToken = twitterToken;
        return this;
    }

    public String getTwitterTokenSecret() {
        return twitterTokenSecret;
    }

    public JUser setTwitterTokenSecret(String twitterTokenSecret) {
        this.twitterTokenSecret = twitterTokenSecret;
        return this;
    }

    /**
     * This method refreshes the properties of this user by the specified
     * Twitter4j user
     * @param user
     */
    public Status updateFieldsBy(User user) {
        twitterId = user.getId();
        setTwitterCreatedAt(user.getCreatedAt());
        setDescription(user.getDescription());
        addLanguage(user.getLang());
        setLocation(TwitterSearch.toStandardLocation(user.getLocation()));
        setRealName(user.getName());

        // user.getFollowersCount();
        // user.getFriendsCount();
        // user.getTimeZone()

        if (user.getProfileImageURL() != null)
            setProfileImageUrl(user.getProfileImageURL().toString());

        if (user.getURL() != null)
            setWebUrl(user.getURL().toString());

        return user.getStatus();
    }

    public void setTwitterCreatedAt(Date twitterCreatedAt) {
        this.twitterCreatedAt = twitterCreatedAt;
    }

    public Date getTwitterCreatedAt() {
        return twitterCreatedAt;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getEmail() {
        return email;
    }

    public JUser setEmail(String email) {
        this.email = email;
        return this;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getRole() {
        return role;
    }

    public void addLanguage(String lang) {
        langs.add(lang);
    }

    public Collection<String> getLanguages() {
        return langs;
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public Long getTwitterId() {
        return twitterId;
    }

    public void setTwitterId(long id) {
        this.twitterId = id;
    }

    public Date getUpdateAt() {
        return updateAt;
    }

    public void setUpdateAt(Date updateAt) {
        this.updateAt = updateAt;
    }

    public String getScreenName() {
        return screenName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public JUser setLocation(String location) {
        this.location = location;
        return this;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public void setWebUrl(String webUrl) {
        this.webUrl = webUrl;
    }

    @Override
    public String toString() {
        return screenName;
    }

    public JUser init(Tweet tw) {
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

    public void addOwnTweet(JTweet tw) {
        addOwnTweet(tw, true);
    }

    public void addOwnTweet(JTweet tw, boolean reverse) {
        ownTweets.add(tw);
//        dirtyOwnTweets = true;

        if (reverse)
            tw.setFromUser(this, false);
    }

    public Collection<JTweet> getOwnTweets() {
//        if (dirtyOwnTweets) {
//            SolrTweet.deduplicate(ownTweets);
//            dirtyOwnTweets = false;
//        }
        return Collections.unmodifiableCollection(ownTweets);
    }

    public Collection<String> getFriends() {
        if (friends == null)
            return Collections.EMPTY_LIST;
        return Collections.unmodifiableCollection(friends);
    }

    public JUser setFriends(Collection<String> friends) {
        this.friends = friends;
        return this;
    }

    public Date getLastFriendsUpdate() {
        return lastFriendsUpdate;
    }

    public void setLastFriendsUpdate(Date lastFriendsUpdate) {
        this.lastFriendsUpdate = lastFriendsUpdate;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final JUser other = (JUser) obj;
        if ((this.screenName == null) ? (other.screenName != null) : !this.screenName.equals(other.screenName))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash + (this.screenName != null ? this.screenName.hashCode() : 0);
        return hash;
    }
}
