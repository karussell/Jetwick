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

import de.jetwick.util.Helper;
import java.io.Serializable;
import java.util.Date;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class JTag implements ElasticObject<JTag>, Serializable, Comparable<JTag> {

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
    private int pages = 1;
    private Date lastRequest = new Date();
    private int requestCount = 0;
    private double tweetsPerSec = 1;
    private String user;

    public JTag() {
    }

    public JTag(String term) {
        setTerm(term);
    }

    public JTag(String term, String user) {
        setTerm(term);
        setUser(user);
    }

    public JTag clearProperties() {
        setPages(5).setMaxCreateTime(0L).setLastMillis(0).
                setQueryInterval(1000).setTweetsPerSec(1);
        return this;
    }

    public double getTweetsPerSec() {
        return tweetsPerSec;
    }

    public JTag setTweetsPerSec(double tweetsPerSec) {
        this.tweetsPerSec = tweetsPerSec;
        return this;
    }

    public void setTerm(String term) {
        this.term = toLowerCaseOnlyOnTerms(term);
    }

    public void setUser(String user) {
        this.user = toLowerCaseOnlyOnTerms(user);
    }

    public String getUser() {
        return user;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(int requestCount) {
        this.requestCount = requestCount;
    }

    public Date getLastRequest() {
        if (lastRequest == null)
            return new Date();
        return lastRequest;
    }

    public JTag setLastRequest(Date lastRequest) {
        this.lastRequest = lastRequest;
        return this;
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

        // force at least 10 second
        queryInterval = Math.max(queryInterval, 10 * 1001);

        // force max 60 min
        queryInterval = Math.min(queryInterval, 60 * 60 * 1001);
    }

    public int getPages() {
        return pages;
    }

    public JTag setPages(int p) {
        pages = p;
        return this;
    }

    @Override
    public String toString() {
        String str = term + " ";
        if (!Helper.isEmpty(user))
            str += user + " ";
        return str + (float) getTweetsPerSec();
    }

    @Override
    public String getId() {
        if (Helper.isEmpty(user))
            return term;

        return term + "_" + user;
    }

    @Override
    public JTag setVersion(long v) {
//        this.version = v;
        return this;
    }

    @Override
    public long getVersion() {
        return 0;
    }

    /**
     * toLowerCase only on none keywords
     */
    public static String toLowerCaseOnlyOnTerms(String str) {
        if (Helper.isEmpty(str))
            return str;

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
        if ((this.user == null) ? (other.user != null) : !this.user.equals(other.user))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + (this.term != null ? this.term.hashCode() : 0);
        hash = 97 * hash + (this.user != null ? this.user.hashCode() : 0);
        return hash;
    }

    @Override
    public JTag updateFrom(JTag other) {
        if (other.getLastRequest().getTime() > getLastRequest().getTime())
            setLastRequest(other.getLastRequest());

        if (other.getRequestCount() > getRequestCount()) {
            setRequestCount(other.getRequestCount());
            setLastRequest(other.getLastRequest());
        }
        
        tweetsPerSec = other.tweetsPerSec;
        lastMillis = other.lastMillis;
        queryInterval = other.queryInterval;
        maxCreateTime = other.maxCreateTime;
        return this;
    }
}
