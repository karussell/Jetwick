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
package de.jetwick.es;

import de.jetwick.config.Configuration;
import de.jetwick.data.JTag;
import de.jetwick.util.Helper;
import de.jetwick.util.MyDate;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.action.deletebyquery.DeleteByQueryRequestBuilder;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.NumericRangeFilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class ElasticTagSearch extends AbstractElasticSearch<JTag> {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private String indexName = "tagindex";
    private String indexType = "tag";
    public static final String Q_INTERVAL = "queryInterval";
    public static final String LAST_REQ = "lastRequest";
    public static final String TERM = "term";
    public static final String USER = "user";
    public static final String TWEETS_SEC = "tweetsPerSec";

    public ElasticTagSearch(Configuration config) {
        this(config.getTweetSearchUrl());
    }

    public ElasticTagSearch(String url) {
        super(url);
    }

    public ElasticTagSearch(Client client) {
        super(client);
    }

    @Override
    public String getIndexName() {
        return indexName;
    }

    @Override
    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public void setIndexType(String indexType) {
        this.indexType = indexType;
    }

    @Override
    public String getIndexType() {
        return indexType;
    }

    public void addAll(Collection<String> tagStringList, boolean refresh, boolean ignoreSearchError) throws IOException {
        Map<String, JTag> tags = Collections.emptyMap();
        try {
            tags = findByTerms(tagStringList);
        } catch (Exception ex) {
            if (!ignoreSearchError)
                throw new RuntimeException(ex);
        }
        Set<JTag> newTags = new LinkedHashSet<JTag>();

        for (String requestedTag : tagStringList) {
            if (!tags.containsKey(requestedTag))
                newTags.add(new JTag(requestedTag));
        }

        bulkUpdate(newTags, getIndexName());
        if (refresh)
            refresh();
    }

    @Override
    public XContentBuilder createDoc(JTag tag) throws IOException {
        XContentBuilder b = JsonXContent.unCachedContentBuilder().startObject();
        b.field(TERM, tag.getTerm());
        b.field("lastMillis", tag.getLastMillis());
        b.field("maxCreateTime", tag.getMaxCreateTime());
        b.field(Q_INTERVAL, tag.getQueryInterval());
        b.field("pages", tag.getPages());
        b.field(LAST_REQ, tag.getLastRequest());
        b.field("requestCount", tag.getRequestCount());
        b.field(TWEETS_SEC, tag.getTweetsPerSec());
        if (!Helper.isEmpty(tag.getUser()))
            b.field(USER, tag.getUser().toLowerCase());

        return b;
    }

    @Override
    public JTag readDoc(String idAsStr, long version, Map<String, Object> doc) {
        JTag tag = new JTag();
        tag.setTerm((String) doc.get(TERM));
        tag.setLastMillis(((Number) doc.get("lastMillis")).longValue());
        tag.setMaxCreateTime(((Number) doc.get("maxCreateTime")).longValue());
        tag.setQueryInterval(((Number) doc.get(Q_INTERVAL)).longValue());
        tag.setPages(((Number) doc.get("pages")).intValue());
        tag.setLastRequest(Helper.toDateNoNPE(((String) doc.get(LAST_REQ))));
        tag.setRequestCount(((Number) doc.get("requestCount")).intValue());
        Number num = (Number) doc.get(TWEETS_SEC);
        if (num != null)
            tag.setTweetsPerSec(num.doubleValue());

        String user = (String) doc.get(USER);
        if (!Helper.isEmpty(user))
            tag.setUser(user);
        return tag;
    }

    private Map<String, JTag> findByTerms(Collection<String> tagStringList) {
        List<JTag> res = collectObjects(query(
                QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
                FilterBuilders.termsFilter(TERM, Helper.toStringArray(tagStringList)))));
        Map<String, JTag> set = new LinkedHashMap<String, JTag>();
        for (JTag t : res) {
            set.put(t.getTerm(), t);
        }

        return set;
    }

    public Collection<JTag> findLowFrequent(int from, int size, double limitOfTweetsPerSec) {
        SearchRequestBuilder srb = createSearchBuilder();
        srb.addSort(Q_INTERVAL, SortOrder.ASC);
        NumericRangeFilterBuilder rb = FilterBuilders.numericRangeFilter(TWEETS_SEC).lt(limitOfTweetsPerSec).includeUpper(true);
        srb.setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), rb));
        srb.setFrom(from);
        srb.setSize(size);
        return collectObjects(srb.execute().actionGet());
    }

    public Collection<JTag> findSorted(int from, int size) {
        SearchRequestBuilder srb = createSearchBuilder();
        srb.addSort(LAST_REQ, SortOrder.DESC);
        srb.setQuery(QueryBuilders.matchAllQuery());
        srb.setFrom(from);
        srb.setSize(size);
        return collectObjects(srb.execute().actionGet());
    }

    public Collection<JTag> findAll(int from, int size) {
        SearchRequestBuilder srb = createSearchBuilder();
        srb.setQuery(QueryBuilders.matchAllQuery());
        srb.setFrom(from);
        srb.setSize(size);
        return collectObjects(srb.execute().actionGet());
    }

    public JTag findByTerm(String term) {
        term = JTag.toLowerCaseOnlyOnTerms(term);
        SearchRequestBuilder b = createSearchBuilder().setQuery(
                QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
                FilterBuilders.andFilter(FilterBuilders.termFilter(TERM, term),
                FilterBuilders.missingFilter(USER))));
        return getOne(b);
    }

    public JTag findByTermAndUser(String term, String user) {
        if (Helper.isEmpty(user))
            return findByTerm(term);

        term = JTag.toLowerCaseOnlyOnTerms(term);
        user = JTag.toLowerCaseOnlyOnTerms(user);
        SearchRequestBuilder b = createSearchBuilder().setQuery(
                QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
                FilterBuilders.andFilter(
                FilterBuilders.termFilter(USER, user),
                FilterBuilders.termFilter(TERM, term))));
        return getOne(b);
    }

    private JTag getOne(SearchRequestBuilder b) {
        List<JTag> tags = collectObjects(b.execute().actionGet());
        if (tags.isEmpty())
            return null;
        else if (tags.size() > 1)
            throw new IllegalStateException("It should be only one tag but was:" + tags);
        else
            return tags.get(0);
    }

    public void deleteByName(String term) {
        DeleteByQueryRequestBuilder qb = client.prepareDeleteByQuery(getIndexName()).setTypes(getIndexType());
        qb.setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), FilterBuilders.termFilter(TERM, term)));
        client.deleteByQuery(qb.request());
    }

    public void deleteOlderThan(int hours) {
        RangeQueryBuilder sqb = QueryBuilders.rangeQuery(LAST_REQ).lt(new MyDate().minusHours(hours).toDate());
        DeleteByQueryRequestBuilder dbq = client.prepareDeleteByQuery(getIndexName()).setQuery(sqb);
        client.deleteByQuery(dbq.request());
    }

    /**
     * Stores the specified tag and increase the request counter
     * @param tag
     */
    public void queueObject(JTag tag) {
        queueObject(tag, false);
    }

    public void queueObject(JTag tag, boolean withUpdateCounter) {
        if (withUpdateCounter)
            updateRequestCounter(tag);

        todoTags.add(tag);
        if (!todoTagsThread.isAlive() && !todoTagsThread.isInterrupted())
            todoTagsThread.start();
    }

    /**
     * Do not use directly. Use queueObject instead
     * @param tag 
     */
    void update(JTag tag) {
        String term = tag.getTerm();
        if (Helper.isEmpty(term) || JetwickQuery.containsForbiddenChars(term)) {
            logger.warn("Did not add term: " + tag);
            return;
        }

        if (term.contains(" OR ")) {
            for (String tmpTerm : term.split(" OR ")) {
                if (!tmpTerm.isEmpty()) {
                    // do not use default lastRequest (which is new Date())
                    JTag tmp = new JTag(tmpTerm).setLastRequest(tag.getLastRequest());
                    tmp.updateFrom(tag);
                    queueObject(tmp);
                }
            }
            return;
        }

        JTag existing = findByTermAndUser(tag.getTerm(), tag.getUser());
        if (existing != null) {
            existing.updateFrom(tag);
            tag = existing;
        }

        store(tag, false);
    }

    public void updateRequestCounter(JTag tag) {
        JTag existing = findByTermAndUser(tag.getTerm(), tag.getUser());
        if (existing != null)
            tag.setRequestCount(existing.getRequestCount() + 1);
        else
            tag.setRequestCount(1);

        tag.setLastRequest(new Date());
    }
    private final BlockingQueue<JTag> todoTags = new LinkedBlockingQueue<JTag>();
    private Thread todoTagsThread = new Thread("TagQueue") {

        @Override
        public void run() {
            while (!isInterrupted()) {
                try {
                    JTag tag = todoTags.take();
                    update(tag);
                    if (todoTags.isEmpty()) {
                        synchronized (todoTags) {
                            todoTags.notifyAll();
                        }
                    }
                } catch (Exception ex) {
                    logger.error(getName() + " was interrupted!!", ex);
                    break;
                }
            }
        }
    };

    /**
     * Blocks until queue gets empty. Use only for tests!
     * 
     * @return true if queue was really empty. At least for some microseconds ;)
     */
    public boolean forceCleanTagQueueAndRefresh() {
        synchronized (todoTags) {
            try {
                todoTags.wait(1000);
                refresh();
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}
