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

package de.jetwick.tw;

import de.jetwick.data.UrlEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import twitter4j.Annotations;
import twitter4j.GeoLocation;
import twitter4j.Tweet;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class Twitter4JTweet implements Tweet {

    private String text;
    private long twitterId;
    private Date createdAt;
    private int toUserId;
    private String toUser;
    private int fromUserId;
    private String fromUser;
    private String isoLanguageCode;
    private String source;
    private String profileImageUrl;
    private GeoLocation geoLocation;
    private String location;
    // twitter default
    private long inReplyToStatusId = -1;
    private List<UrlEntry> urlEntries = new ArrayList<UrlEntry>();
    private Annotations annotations;

    public Twitter4JTweet(long twitterId, String text, String fromUser) {
        this.fromUser = fromUser;
        this.twitterId = twitterId;
        this.text = text;
        createdAt = new Date();
    }

    public Twitter4JTweet(Tweet tw) {
        this(tw.getId(), tw.getText(), tw.getFromUser());
        createdAt = tw.getCreatedAt();
        fromUserId = tw.getFromUserId();
        geoLocation = tw.getGeoLocation();
        isoLanguageCode = tw.getIsoLanguageCode();
        location = tw.getLocation();
        profileImageUrl = tw.getProfileImageUrl();
        source = tw.getSource();
        toUser = tw.getToUser();
        toUserId = tw.getToUserId();
    }

    @Override
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public int getToUserId() {
        return toUserId;
    }

    @Override
    public String getToUser() {
        return toUser;
    }

    @Override
    public String getFromUser() {
        return fromUser;
    }

    @Override
    public long getId() {
        return twitterId;
    }

    @Override
    public int getFromUserId() {
        return fromUserId;
    }

    @Override
    public String getIsoLanguageCode() {
        return isoLanguageCode;
    }

    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    @Override
    public Date getCreatedAt() {
        return createdAt;
    }

    @Override
    public GeoLocation getGeoLocation() {
        return geoLocation;
    }

    public Twitter4JTweet setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public void setFromUser(String fromUser) {
        this.fromUser = fromUser;
    }

    public void setFromUserId(int fromUserId) {
        this.fromUserId = fromUserId;
    }

    public void setIsoLanguageCode(String isoLanguageCode) {
        this.isoLanguageCode = isoLanguageCode;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setToUser(int toUserId, String userScreenName) {
        this.toUserId = toUserId;
        this.toUser = userScreenName;
    }

    public void setGeoLocation(GeoLocation geoLocation) {
        this.geoLocation = geoLocation;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public String getLocation() {
        return location;
    }

    public long getInReplyToStatusId() {
        return inReplyToStatusId;
    }

    public Twitter4JTweet setInReplyToStatusId(long inReplyToStatusId) {
        this.inReplyToStatusId = inReplyToStatusId;
        return this;
    }

    public String getOrigRetweetedText() {
        if (!isRetweet())
            return "";

        int index1 = getText().indexOf("RT ");
        if (index1 < 0) {
            index1 = getText().indexOf("Rt ");
            if (index1 < 0)
                throw new IllegalStateException("should have found RT string if isRetweet is true!");
        }

        return getText().substring(index1 + 3).trim();
    }

    public boolean isRetweet() {
        return getText().contains("RT ") || getText().contains("Rt ");
    }

    public String getTextToRetweet() {
        return "@" + getFromUser() + ": " + getText().trim();
    }
//
//    public void addUrlEntry(UrlEntry ue) {
//        urlEntries.add(ue);
//    }
//
//    public void addAllUrlEntries(Collection<UrlEntry> es) {
//        for (UrlEntry e : es) {
//            urlEntries.add(e);
//        }
//    }

    public List<UrlEntry> getUrlEntries() {
        return urlEntries;
    }

    @Override
    public String toString() {
        return fromUser + " " + text + " " + createdAt;
    }

    public int compareTo(Tweet o) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

//    public int compareTo(Object o) {
//        throw new UnsupportedOperationException("Not supported yet.");
//    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass())
            return false;
        final Twitter4JTweet other = (Twitter4JTweet) obj;
        return this.twitterId == other.twitterId;
    }

    @Override
    public int hashCode() {
        return 79 * 5 + (int) (this.twitterId ^ (this.twitterId >>> 32));
    }

    @Override
    public Annotations getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Annotations annotations) {
        this.annotations = annotations;
    }
}
