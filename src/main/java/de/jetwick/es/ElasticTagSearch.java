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
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.action.deletebyquery.DeleteByQueryRequestBuilder;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.xcontent.FilterBuilders;
import org.elasticsearch.index.query.xcontent.QueryBuilders;
import org.elasticsearch.index.query.xcontent.XContentQueryBuilder;
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
    public static final String Q_INTERVAL = "queryInterval";
    public static final String TERM = "_id";

    public ElasticTagSearch(Configuration config) {
        this(config.getTweetSearchUrl(), config.getTweetSearchLogin(), config.getTweetSearchPassword());
    }

    public ElasticTagSearch(String url, String login, String pw) {
        super(url, login, pw);
    }

    public ElasticTagSearch(Client client) {
        super(client);
    }

    public void addAll(Collection<String> tagStringList, boolean refresh) throws IOException {
        Map<String, JTag> tags = findByNames(tagStringList);
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
    public String getIndexName() {
        return indexName;
    }

    @Override
    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    @Override
    public String getIndexType() {
        return "tag";
    }

    @Override
    public XContentBuilder createDoc(JTag tag) throws IOException {
        XContentBuilder b = JsonXContent.unCachedContentBuilder().startObject();                
        b.field("lastId", tag.getLastId());
        b.field(Q_INTERVAL, tag.getQueryInterval());
        return b;
    }

    @Override
    public JTag readDoc(Map<String, Object> doc, String idAsStr) {
        String term = idAsStr;
        JTag tag = new JTag(term);
        tag.setLastId(((Number) doc.get("lastId")).longValue());
        tag.setQueryInterval(((Number) doc.get(Q_INTERVAL)).longValue());        
        return tag;
    }

    private Map<String, JTag> findByNames(Collection<String> tagStringList) {
        List<JTag> res = collectObjects(query(
                QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
                FilterBuilders.termsFilter("_id", Helper.toStringArray(tagStringList)))));
        Map<String, JTag> set = new LinkedHashMap<String, JTag>();
        for (JTag t : res) {
            set.put(t.getTerm(), t);
        }

        return set;
    }

    public SearchResponse query(XContentQueryBuilder queryBuilder) {
        SearchRequestBuilder srb = client.prepareSearch(getIndexName());
        srb.setQuery(queryBuilder);
        return srb.execute().actionGet();
    }
    
    SearchRequestBuilder createSearchBuilder() {
        return client.prepareSearch(getIndexName());
    }

    public Collection<JTag> findSorted(int from, int size) {        
        SearchRequestBuilder srb = createSearchBuilder();
        srb.addSort(Q_INTERVAL, SortOrder.ASC);
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
    
    public void update(JTag tag) {
        update(tag, true);
    }

    public JTag findByName(String term) {
        term = JTag.toLowerCaseOnlyOnTerms(term);
        List<JTag> res = collectObjects(query(
                QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
                FilterBuilders.termFilter("_id", term))));
        if(res.isEmpty())
            return null;
        else if(res.size() == 1)
            return res.get(0);
        else
            throw new IllegalStateException("Found more than one tag for " + term);
    }

    public void deleteByName(String term) {
        DeleteByQueryRequestBuilder qb = client.prepareDeleteByQuery(getIndexName());
        qb.setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), FilterBuilders.termFilter("_id", term)));
        client.deleteByQuery(qb.request());
    }
}
