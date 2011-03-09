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
public class JTag implements DbObject, Serializable, Comparable<JTag> {

    private static final long serialVersionUID = 1L;
    public static final String TERM = "term";
    public static final long DEFAULT_Q_I = 5 * 1000L;
    private String term;
    /** Save the maximum creation date of the tweet list for the last twitter search */
    private long maxCreateTime = 0L;
    /** 
     * Save when the last twitter search was performed. Then TweetCollector prefer
     * tags with a higher frequency even after interrupted
     */
    private long lastMillis = 0L;
    /** How long to wait until the TweetCollector should perform the new twitter search
     */
    private long queryInterval = DEFAULT_Q_I;
    /**
     * The first search should page 5 times
     */
    private int pages = 5;

    public JTag() {
    }

    public JTag(String term) {
        this.term = toLowerCaseOnlyOnTerms(term);
    }

    public long getLastMillis() {
        return lastMillis;
    }

    public JTag setLastMillis(long lastMillis) {
        this.lastMillis = lastMillis;
        return this;
    }

    public long getMaxCreateTime() {
        return maxCreateTime;
    }

    public JTag setMaxCreateTime(long maxCreateTime) {
        this.maxCreateTime = maxCreateTime;
        return this;
    }

    public long getQueryInterval() {
        return queryInterval;
    }

    public String getTerm() {
        return term;
    }

    public JTag setQueryInterval(long queryInterval) {
        this.queryInterval = queryInterval;
        return this;
    }

    public float getWaitingSeconds() {
        return (queryInterval - (System.currentTimeMillis() - lastMillis)) / 1000.0f;
    }

    public boolean nextQuery() {
        if ((System.currentTimeMillis() - lastMillis) > queryInterval) {
            lastMillis = System.currentTimeMillis();
            return true;
        } else
            return false;
    }

    public void optimizeQueryFrequency(int newTweets) {
        // try to get 100 tweets per search        
        if (newTweets == 0)
            queryInterval *= 20;
        else
            queryInterval = (long) (20.0 / newTweets) * queryInterval;

        // force at least 5 second
        queryInterval = Math.max(queryInterval, 5 * 1001);

        // force max 5 min
        queryInterval = Math.min(queryInterval, 5 * 60 * 1001);
    }

    public int getPages() {
        int tmp = pages;
        pages = 1;
        return tmp;
    }

    public JTag setPages(int p) {
        pages = p;
        return this;
    }

    @Override
    public String toString() {
        return term + " " + getWaitingSeconds();
    }

    @Override
    public String getId() {
        return getTerm();
    }

    /**
     * toLowerCase only on none keywords
     */
    public static String toLowerCaseOnlyOnTerms(String str) {
        StringBuilder sb = new StringBuilder();
        int counter = 0;
        for (String t : str.split(" ")) {
            if (counter > 0)
                sb.append(" ");

            if (t.equals("OR") || t.equals("AND"))
                sb.append(t);
            else
                sb.append(t.toLowerCase());

            counter++;
        }
        return sb.toString();
    }

    @Override
    public int compareTo(JTag o) {
        float tmp1 = o.getWaitingSeconds();
        float tmp2 = getWaitingSeconds();
        if (tmp1 > tmp2)
            return -1;
        else if (tmp1 < tmp2)
            return 1;
        else
            return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final JTag other = (JTag) obj;
        if ((this.term == null) ? (other.term != null) : !this.term.equals(other.term))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + (this.term != null ? this.term.hashCode() : 0);
        return hash;
    }
}
