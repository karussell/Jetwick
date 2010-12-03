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

import de.jetwick.tw.TwitterSearch;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import org.hibernate.annotations.BatchSize;
import twitter4j.Status;
import twitter4j.User;

/**
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
@Entity
@BatchSize(size = 50)
@Table(name = "yuser", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"screenName"})})
public class YUser implements DbObject, Serializable {

    public static int OUT_OF_DATE_DAYS = 7;
    public static int DISPLAY_TWEETS = 2;
    public static final String UPDATE_AT = "updateAt";
    public static final String OWN_TWEETS = "ownTweets";
    public static final String SCREEN_NAME = "screenName";
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Version
    private Integer version;
    private String realName;
    // we don't need an index here -> already via uniqueconstraint
    private String screenName;
    private Integer twitterId;
    private String profileImageUrl;
    private String webUrl;
    private String location;
    @ElementCollection
    @CollectionTable(name = "langs", joinColumns =
    @JoinColumn(name = "id"))
    @Column(name = "lang")
    private Set<String> langs = new LinkedHashSet<String>();
    private String description;
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date createdAt;
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date updateAt;
    // mappedBy in this case avoids a third USER_ID column in the join table YUSER_YUSER
    @ManyToMany(mappedBy = "followers")
    private Set<YUser> following = new LinkedHashSet<YUser>();
    @ManyToMany
    private Set<YUser> followers = new LinkedHashSet<YUser>();
    @ElementCollection
    @CollectionTable(name = "tags", joinColumns =
    @JoinColumn(name = "id"))
    @Column(name = "tag")
//    @Lob
//    @Column(name = "tags")
    private Set<String> tags = new LinkedHashSet<String>();
    private String tokenSecret;
    private String secret;

    public YUser() {
    }

    public YUser(String name) {
        this.screenName = name.toLowerCase();
        if (screenName.trim().length() == 0)
            throw new IllegalArgumentException("Screenname must not be empty!");
    }

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
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

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getTokenSecret() {
        return tokenSecret;
    }

    public void setTokenSecret(String tokenSecret) {
        this.tokenSecret = tokenSecret;
    }

    /**
     * This method refreshes the properties of this user by the specified
     * Twitter4j user
     * @param user
     */
    public Status updateFieldsBy(User user) {
        twitterId = user.getId();
        setCreatedAt(user.getCreatedAt());
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

    public void addLanguage(String lang) {
        langs.add(lang);
    }

    public Collection<String> getLanguages() {
        return langs;
    }

    public void addFollower(YUser u) {
        addFollower(u, true);
    }

    public void addFollower(YUser u, boolean reverse) {
        followers.add(u);
        if (reverse)
            u.addFollowing(this, false);
    }

    public Collection<YUser> getFollowers() {
        return Collections.unmodifiableCollection(followers);
    }

    public void addFollowing(YUser u) {
        addFollowing(u, true);
    }

    public void addFollowing(YUser u, boolean reverse) {
        following.add(u);
        if (reverse)
            u.addFollower(this, false);
    }

    public Collection<YUser> getFollowing() {
        return Collections.unmodifiableCollection(following);
    }

    public Integer getTwitterId() {
        return twitterId;
    }

    public void setTwitterId(int id) {
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

    // Should be used only for screen name fixing, because it acts as id!
    public void setsCREENnAME(String sn) {
        screenName = sn;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
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

    public YUser setLocation(String location) {
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

    @Override
    public boolean equals(Object obj) {
        if (obj == null && !(obj instanceof YUser))
            return false;
        final YUser other = (YUser) obj;
        if ((this.screenName == null) ? (other.screenName != null) : !this.screenName.equals(other.screenName))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 83 * hash + (this.screenName != null ? this.screenName.hashCode() : 0);
        return hash;
    }
}
