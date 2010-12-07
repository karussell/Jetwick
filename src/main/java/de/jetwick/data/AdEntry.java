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

import de.jetwick.util.MapEntry;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class AdEntry implements Serializable {

    private static final long serialVersionUID = 1L;
    private List<Entry<String, String>> queryUser = new ArrayList<Entry<String, String>>();
    private String id;
    private String iconUrl;
    private String title;
    private String description;
    private List<String> keywords = new ArrayList<String>();

    /**
     * @param id can something like the company behind the ad etc
     */
    public AdEntry(String id) {
        this.id = id;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public AdEntry setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
        return this;
    }

    /**
     * @return string which should be displayed as popup
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getId() {
        return id;
    }

    /**
     * @return on which keywords the ad should be displayed
     */
    public List<String> getKeywords() {
        return keywords;
    }

    public void addKeyword(String kw) {
        keywords.add(kw);
    }

    /**
     * @return string which should be displayed as title of the link
     */
    public String getTitle() {
        return title;
    }

    public AdEntry setTitle(String title) {
        this.title = title;
        return this;
    }

    /**
     * @return which query or/and user should be queried for
     */
    public AdEntry addQueryUserPair(String q, String u) {
        if (!q.isEmpty() || !u.isEmpty())
            queryUser.add(new MapEntry<String, String>(q, u));
        return this;
    }

    public List<Entry<String, String>> getQueryUserPairs() {
        return queryUser;
    }

    @Override
    public String toString() {
        return id + " " + title;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final AdEntry other = (AdEntry) obj;
        if ((this.id == null) ? (other.id != null) : !this.id.equals(other.id))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }
}
