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

import java.io.Serializable;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class UrlEntry implements Serializable {

    private static final long serialVersionUID = 1L;
    private int index = -1;
    private int lastIndex = -1;
    private String resolvedUrl;
    private String resolvedDomain;
    private String resolvedTitle;
    private String resolvedSnippet;

    public UrlEntry() {
    }

    public UrlEntry(int index, int lastIndex, String resolvedUrl) {
        this.index = index;
        this.lastIndex = lastIndex;
        this.resolvedUrl = resolvedUrl;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void setLastIndex(int lastIndex) {
        this.lastIndex = lastIndex;
    }

    public void setResolvedUrl(String resolvedUrl) {
        this.resolvedUrl = resolvedUrl;
    }

    public void setResolvedDomain(String resolvedDomain) {
        this.resolvedDomain = resolvedDomain;
    }

    public UrlEntry setResolvedTitle(String resolvedTitle) {
        this.resolvedTitle = resolvedTitle;
        return this;
    }

    public void setResolvedSnippet(String resolvedSnippet) {
        this.resolvedSnippet = resolvedSnippet;
    }

    public String getResolvedSnippet() {
        return resolvedSnippet;
    }

    public int getIndex() {
        return index;
    }

    public int getLastIndex() {
        return lastIndex;
    }

    public String getResolvedDomain() {
        return resolvedDomain;
    }

    public String getResolvedTitle() {
        return resolvedTitle;
    }

    public String getResolvedUrl() {
        return resolvedUrl;
    }

    @Override
    public String toString() {
        return "title:" + getResolvedTitle() + " url:" + getResolvedUrl();
    }
}
