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
package de.jetwick.tw;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import twitter4j.RateLimitStatus;
import twitter4j.Status;
import twitter4j.User;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class Twitter4JUser implements User {

    private static final long serialVersionUID = 1L;
    private long id;
    private String name;
    private String screenName;
    private String location;
    private String description;
    private boolean contributorsEnabled;
    private String profileImageURL;
    private String url;
    private boolean isProtected;
    private int followersCount;
    private Status status;
    private String profileBackgroundColor;
    private String profileTextColor;
    private String profileLinkColor;
    private String profileSidebarFillColor;
    private String profileSidebarBorderColor;
    private int friendsCount;
    private Date createdAt;
    private int favouritesCount;
    private int utcOffset;
    private String timeZone;
    private String profileBackgroundImageUrl;
    private boolean profileBackgroundTiled;
    private String lang;
    private int statusesCount;
    private boolean geoEnabled;
    private boolean verified;
    private int listedCount;
    private boolean followRequestSent;
    private transient RateLimitStatus rateLimitStatus = null;
    private boolean translator = false;
    private boolean showAllInlineMedia = true;
    private boolean profileUseBackgroundImage = true;

    public Twitter4JUser(String screenName) {
        this.screenName = screenName;
    }

    public boolean isContributorsEnabled() {
        return contributorsEnabled;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public String getDescription() {
        return description;
    }

    public int getFavouritesCount() {
        return favouritesCount;
    }

    public boolean isFollowRequestSent() {
        return followRequestSent;
    }

    public int getFollowersCount() {
        return followersCount;
    }

    public int getFriendsCount() {
        return friendsCount;
    }

    public boolean isGeoEnabled() {
        return geoEnabled;
    }

    public long getId() {
        return id;
    }

    public String getLang() {
        return lang;
    }

    public int getListedCount() {
        return listedCount;
    }

    public String getLocation() {
        return location;
    }

    public String getName() {
        return name;
    }

    public String getProfileBackgroundColor() {
        return profileBackgroundColor;
    }

    public String getProfileBackgroundImageUrl() {
        return profileBackgroundImageUrl;
    }

    public boolean isProfileBackgroundTiled() {
        return profileBackgroundTiled;
    }

    public URL getProfileImageURL() {
        try {
            return new URL(profileImageURL);
        } catch (MalformedURLException ex) {
            return null;
        }
    }

    public String getProfileLinkColor() {
        return profileLinkColor;
    }

    public String getProfileSidebarBorderColor() {
        return profileSidebarBorderColor;
    }

    public String getProfileSidebarFillColor() {
        return profileSidebarFillColor;
    }

    public String getProfileTextColor() {
        return profileTextColor;
    }

    public String getScreenName() {
        return screenName;
    }

    public static long getSerialVersionUID() {
        return serialVersionUID;
    }

    public Status getStatus() {
        return status;
    }

    public int getStatusesCount() {
        return statusesCount;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public int getUtcOffset() {
        return utcOffset;
    }

    public boolean isVerified() {
        return verified;
    }

    @Override
    public URL getURL() {
        try {
            return new URL(url);
        } catch (MalformedURLException ex) {
            return null;
        }
    }

    @Override
    public boolean isProtected() {
        return isProtected;
    }

    @Override
    public int compareTo(User o) {
        if(id < o.getId())
            return -1;
        else if(id > o.getId())
            return 1;
        return 0;
    }

    @Override
    public RateLimitStatus getRateLimitStatus() {
        return rateLimitStatus;
    }

    @Override
    public boolean isProfileUseBackgroundImage() {
        return profileUseBackgroundImage;
    }

    @Override
    public boolean isShowAllInlineMedia() {
        return showAllInlineMedia;
    }

    @Override
    public boolean isTranslator() {        
        return translator;
    }
}
