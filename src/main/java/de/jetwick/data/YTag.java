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
import java.util.ArrayList;
import java.util.List;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
@Entity
@Table(name = "ytag", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"term"})})
public class YTag implements DbObject, Serializable, Comparable<YTag> {

    private static final long serialVersionUID = 1L;

    public static List<YTag> createList(List<String> terms) {
        List<YTag> list = new ArrayList<YTag>(terms.size());
        for (String term : terms) {
            list.add(new YTag(term));
        }

        return list;
    }
    public static final String TERM = "term";
    public static final long DEFAULT_Q_I = 5 * 1000L;
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Version
    private Integer version;
    private String term;
    private Long lastId = 0L;
    private Long queryInterval = DEFAULT_Q_I;
    private Long lastMillis = 0L;
    private Long searchCounter = 0L;
    private boolean transientFlag;

    public YTag() {
    }

    public YTag(String term) {
        this.term = term.toLowerCase();
    }

    public YTag(String term, long maxId, long queryInterval) {
        this(term);
        this.lastId = maxId;
        this.queryInterval = queryInterval;
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

    public void setLastId(long maxId) {
        this.lastId = maxId;
    }

    public long getLastId() {
        return lastId;
    }

    public long getQueryInterval() {
        return queryInterval;
    }

    public String getTerm() {
        return term;
    }

    public YTag setQueryInterval(long queryInterval) {
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

    void setLastMillis(Long lastMillis) {
        this.lastMillis = lastMillis;
    }

    public void optimizeQueryFrequency(int newTweets) {
        // try to get 100 tweets per search
        long old = queryInterval;
        if (newTweets == 0)
            queryInterval *= 20;
        else
            queryInterval = (long) (20.0 / newTweets) * queryInterval;

        // force at least 5 second
        queryInterval = Math.max(queryInterval, 5 * 1001);

        // force max 20 min
        queryInterval = Math.min(queryInterval, 20 * 60 * 1001);

        // force max 5 min for jetwick
        if ("#jetwick".equalsIgnoreCase(term) || "jetwick".equalsIgnoreCase(term))
            queryInterval = Math.min(queryInterval, 5 * 60 * 1001);

        // force max 5 hours
//        queryInterval = Math.min(queryInterval, 5 * 3600 * 1001);

//        logger.info(newTweets + " hits for " + term + "\t" + lastId
//                + "\t => query interval was: " + Math.round(old / 1000f)
//                + "; adjusted to " + Math.round(queryInterval / 1000f));
    }

    public void update(YTag st) {
        lastId = st.lastId;
        queryInterval = st.queryInterval;
        lastMillis = st.lastMillis;
        searchCounter = st.searchCounter;
    }

    public void incSearchCounter() {
        searchCounter++;
    }

    public Long getSearchCounter() {
        return searchCounter;
    }

    public boolean isTransient() {
        return transientFlag;
    }

    public YTag setTransient(boolean transientFlag) {
        this.transientFlag = transientFlag;
        return this;
    }

    /**
     * @return true if this tag is regularly searched
     */
    public boolean isFrequent() {
        return queryInterval < 3600 * 1000;
    }

    public int getPages() {
        if (isFrequent())
            return 1;

        return 10;
    }

    @Override
    public String toString() {
        return term + " " + getWaitingSeconds();
    }

    @Override
    public int compareTo(YTag o) {
        float tmp1 = o.getWaitingSeconds();
        float tmp2 = getWaitingSeconds();
        if (tmp1 > tmp2)
            return -1;
        else if (tmp1 < tmp2)
            return 1;
        else
            return 0;
    }
}
