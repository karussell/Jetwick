/*
 * Copyright 2011 Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetwick.es;

import de.jetwick.data.DbObject;
import de.jetwick.util.AnyExecutor;
import de.jetwick.util.Helper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.lucene.search.Explanation;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.admin.indices.optimize.OptimizeRequest;
import org.elasticsearch.action.admin.indices.optimize.OptimizeResponse;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.StopWatch;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class of all data access objects for ElasticSearch
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public abstract class AbstractElasticSearch<T extends DbObject> implements CreateObjectsInterface<T> {

    public static final String BOOST = "_boost";
    private Logger logger = LoggerFactory.getLogger(getClass());
    protected boolean testing = false;
    protected Client client;
    
    AbstractElasticSearch() {
    }

    public AbstractElasticSearch(Client client) {
        this.client = client;
    }

    public AbstractElasticSearch(String url) {
        client = createClient(ElasticNode.CLUSTER, url, ElasticNode.PORT);
    }

    public static Client createClient(String cluster, String url, int port) {
        Settings s = ImmutableSettings.settingsBuilder().put("cluster.name", cluster).build();
        TransportClient tmp = new TransportClient(s);
        tmp.addTransportAddress(new InetSocketTransportAddress(url, port));
        return tmp;
    }

    public void setTesting(boolean testing) {
        this.testing = testing;
    }

    public boolean hasVersionSupport() {
        return false;
    }

    public abstract String getIndexName();

    public abstract void setIndexName(String indexName);

    public abstract String getIndexType();

    public boolean indexExists(String indexName) {
        // make sure node is up to create the index otherwise we get: blocked by: [1/not recovered from gateway];
        // waitForYellow();
        Map map = client.admin().cluster().prepareState().execute().actionGet().
                getState().getMetaData().getIndices();
        return map.containsKey(indexName);
    }

    public void createIndex(String indexName) {
        client.admin().indices().create(new CreateIndexRequest(indexName)).actionGet();
    }

    public void saveCreateIndex() {
        saveCreateIndex(getIndexName(), true);
    }

    public void saveCreateIndex(String name, boolean log) {
        try {
            createIndex(name);
            if (log)
                logger.info("Created index: " + name);
        } catch (Exception ex) {
            if (log)
                logger.info("Index " + getIndexName() + " already exists");
        }
    }

    void waitForYellow() {
        waitForYellow(getIndexName());
    }

    void waitForYellow(String name) {
        client.admin().cluster().health(new ClusterHealthRequest(name).waitForYellowStatus()).actionGet();
    }

    void waitForGreen(String name) {
        client.admin().cluster().health(new ClusterHealthRequest(name).waitForGreenStatus()).actionGet();
    }

    public void executeForAll(AnyExecutor<T> any, int pageSize) {
        long keepTimeInMinutes = 60;
        scanThis(any, QueryBuilders.matchAllQuery(), keepTimeInMinutes, pageSize);
    }

    public void scanThis(AnyExecutor<T> any, QueryBuilder query,
            long keepTimeInMinutes, int pageSize) {
        SearchRequestBuilder srb = client.prepareSearch(getIndexName()).
                setQuery(query).setSize(pageSize).
                setSearchType(SearchType.SCAN).
                setScroll(TimeValue.timeValueMinutes(keepTimeInMinutes));
        SearchResponse rsp = srb.execute().actionGet();

        try {
            int counter = 0;
            while (true) {
                rsp = client.prepareSearchScroll(rsp.scrollId()).
                        setScroll(TimeValue.timeValueMinutes(keepTimeInMinutes)).execute().actionGet();
                long currentResults = rsp.hits().hits().length;
                logger.info("(" + counter++ + ") scanquery with " + pageSize + " page size and " + currentResults + " hits");
                if (currentResults == 0)
                    break;

                for (T t : collectObjects(rsp)) {
                    any.execute(t);
                }
            }
        } catch (Exception ex) {
            logger.error("Cannot run scanThis", ex);
        }
    }

    public void refresh() {
        refresh(getIndexName());
    }

    public void refresh(Collection<String> indices) {
        refresh(Helper.toStringArray(indices));
    }

    public void refresh(String... indices) {
        RefreshResponse rsp = client.admin().indices().refresh(new RefreshRequest(indices)).actionGet();
        //assertEquals(1, rsp.getFailedShards());
    }

    public long countAll() {
        return countAll(getIndexName());
    }

    public long countAll(String... indices) {
        CountResponse response = client.prepareCount(indices).
                setQuery(QueryBuilders.matchAllQuery()).
                execute().actionGet();
        return response.getCount();
    }

    public void deleteById(String id) {
        DeleteResponse response = client.prepareDelete(getIndexName(), getIndexType(), id).
                execute().
                actionGet();
    }

    public void deleteAll() {        
        deleteAll(getIndexName(), getIndexType());
    }

    public void deleteAll(String indexName, String indexType) {
        //client.prepareIndex().setOpType(OpType.)
        //there is an index delete operation
        // http://www.elasticsearch.com/docs/elasticsearch/rest_api/admin/indices/delete_index/

        client.prepareDeleteByQuery(indexName).
                setQuery(QueryBuilders.matchAllQuery()).
                setTypes(indexType).
                execute().actionGet();
        refresh(indexName);
    }

    public OptimizeResponse optimize() {
        return optimize(getIndexName(), 1);
    }

    public OptimizeResponse optimize(String indexName, int optimizeToSegmentsAfterUpdate) {
        return client.admin().indices().optimize(new OptimizeRequest(indexName).maxNumSegments(optimizeToSegmentsAfterUpdate)).actionGet();
    }

    public void deleteIndex(String indexName) {
        client.admin().indices().delete(new DeleteIndexRequest(indexName)).actionGet();
    }

    public void addIndexAlias(String indexName, String alias) {
//        new AliasAction(AliasAction.Type.ADD, index, alias)
        client.admin().indices().aliases(new IndicesAliasesRequest().addAlias(indexName, alias)).actionGet();
    }

    public void nodeInfo() {
        NodesInfoResponse rsp = client.admin().cluster().nodesInfo(new NodesInfoRequest()).actionGet();
        String str = "Cluster:" + rsp.getClusterName() + ". Active nodes:";
        str += rsp.getNodesMap().keySet();
        logger.info(str);
    }
    
    public SearchResponse query(QueryBuilder queryBuilder) {
        SearchRequestBuilder srb = createSearchBuilder();
        srb.setQuery(queryBuilder);
        return srb.execute().actionGet();
    }

    protected SearchRequestBuilder createSearchBuilder() {
        return client.prepareSearch(getIndexName()).setTypes(getIndexType()).setVersion(hasVersionSupport());
    }

    public SearchResponse query(JetwickQuery query) {
        return query(query, false, false);
    }

    public SearchResponse query(JetwickQuery query, boolean log, boolean explain) {
        SearchRequestBuilder srb = createSearchBuilder();
        srb.setExplain(query.isExplain());
        query.initRequestBuilder(srb);
        if (log)
            try {
                logger.info(srb.internalBuilder().toXContent(JsonXContent.contentBuilder(), null).string());
            } catch (Exception ex) {
            }
        return srb.execute().actionGet();
    }

    public List<T> search(JetwickQuery q) {
        return collectObjects(query(q));
    }
    
    @Override
    public List<T> collectObjects(SearchResponse rsp) {
        SearchHits docs = rsp.getHits();
        List<T> list = new ArrayList<T>(docs.hits().length);
        for (SearchHit sd : docs) {
            if (sd.getExplanation() != null) {
                String res = "";
                for (Explanation str : sd.getExplanation().getDetails()) {
                    res += str.toString();
                }
                logger.info(sd.getId() + " " + res);
            }
            T o = readDoc(sd.getId(), sd.getVersion(), sd.getSource());
            if (o != null)
                list.add(o);
        }

        return list;
    }

    public abstract T readDoc(String idAsStr, long version, Map<String, Object> source);

    public abstract XContentBuilder createDoc(T tw) throws IOException;

    /**
     * All indices has to be created before!
     */
    public void mergeIndices(Collection<String> indexList, String intoIndex,
            int hitsPerPage, boolean forceRefresh, CreateObjectsInterface<T> createObj,
            FilterBuilder additionalFilter) {
        if (forceRefresh) {
            refresh(indexList);
            refresh(intoIndex);
        }

        int keepTime = 100;
        for (String fromIndex : indexList) {
            SearchRequestBuilder srb = client.prepareSearch(fromIndex).
                    setVersion(true).
                    setQuery(QueryBuilders.matchAllQuery()).setSize(hitsPerPage).
                    setSearchType(SearchType.SCAN).
                    setScroll(TimeValue.timeValueMinutes(keepTime));
            if (additionalFilter != null)
                srb.setFilter(additionalFilter);
            SearchResponse rsp = srb.execute().actionGet();

            try {
                long total = rsp.hits().totalHits();
                int collectedResults = 0;
                while (true) {
                    StopWatch queryWatch = new StopWatch().start();
                    rsp = client.prepareSearchScroll(rsp.scrollId()).
                            setScroll(TimeValue.timeValueMinutes(keepTime)).execute().actionGet();
                    long currentResults = rsp.hits().hits().length;
                    if (currentResults == 0)
                        break;

                    queryWatch.stop();
                    Collection<T> objs = createObj.collectObjects(rsp);
                    StopWatch updateWatch = new StopWatch().start();
                    int failed = bulkUpdate(objs, intoIndex, false, false).size();
                    // trying to enable flushing to avoid memory issues on the server side?
                    flush(intoIndex);
                    updateWatch.stop();
                    collectedResults += currentResults;
                    logger.info("Progress " + collectedResults + "/" + total + " fromIndex="
                            + fromIndex + " update:" + updateWatch.totalTime().getSeconds() + " query:" + queryWatch.totalTime().getSeconds() + " failed:" + failed);
                }
                logger.info("Finished copying of index:" + fromIndex + ". Total:" + total + " collected:" + collectedResults);
            } catch (Exception ex) {
//                throw new RuntimeException(ex);
                logger.error("Failed to copy data from index " + fromIndex + " into " + intoIndex + ".", ex);
            }
        }

        if (forceRefresh)
            refresh(intoIndex);
    }

    /**
     * Stores the specified object into the index 
     * 
     * @return a string != null if indexing failed
     */
    public Integer store(T obj, boolean refresh) {
        try {
            // normal indexing operation throws VersionConflictEngineException:
//          IndexRequestBuilder irb = client.prepareIndex(getIndexName(), getIndexType(), id).
//                setConsistencyLevel(WriteConsistencyLevel.DEFAULT).
//                setSource(b);
//          irb.execute().actionGet();

            // but we want only one method to handle this failure
            Collection<Integer> ret = bulkUpdate(Collections.singleton(obj), getIndexName(), refresh);
            if (ret.size() > 0)
                return ret.iterator().next();
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Updates the specified objects     
     * @return the id's of the failed objects (e.g. due to versioning)
     */
    public Collection<Integer> bulkUpdate(Collection<T> objects, String indexName) {
        return bulkUpdate(objects, indexName, false);

    }

    public Collection<Integer> bulkUpdate(Collection<T> objects, String indexName, boolean refresh) {
        return bulkUpdate(objects, indexName, refresh, hasVersionSupport());
    }

    /**
     * Updates the specified objects     
     * @return the id's of the failed objects (e.g. due to versioning)
     */
    public Collection<Integer> bulkUpdate(Collection<T> objects, String indexName, boolean refresh, boolean enableVersioning) {
        // now using bulk API instead of feeding each doc separate with feedDoc
        BulkRequestBuilder brb = client.prepareBulk();
        // this works differently then the direct call to refresh!? maybe refresh is not async?
//        brb.setRefresh(refresh);
        for (T o : objects) {
            if (o.getId() == null) {
                logger.warn("Skipped object without id when bulkUpdate:" + o);
                continue;
            }

            try {
                XContentBuilder source = createDoc(o);
                IndexRequest indexReq = Requests.indexRequest(indexName).type(getIndexType()).id(o.getId()).source(source);
                
                if (enableVersioning)
                    indexReq.version(o.getVersion());

                brb.add(indexReq);
            } catch (IOException ex) {
                logger.warn("Cannot add object:" + o + " to bulkIndexing action." + ex.getMessage());
            }
        }
        if (brb.numberOfActions() > 0) {
            BulkResponse rsp = brb.execute().actionGet();
            if (rsp.hasFailures()) {
                List<Integer> list = new ArrayList<Integer>(rsp.items().length);
                for (BulkItemResponse br : rsp.items()) {                    
                    if(br.isFailed()) {
//                        logger.info("Error:" + br.failureMessage());
                        list.add(br.itemId());
                    }
                }
                return list;
            }
            if (refresh)
                refresh(indexName);
        }

        return Collections.emptyList();
    }

    public void flush(String... indices) {
        client.admin().indices().flush(new FlushRequest(indices)).actionGet();
    }

    public void waitUntilAvailable(long wait) throws InterruptedException {
        logger.info("now waiting until node is ok");
        while (true) {
            try {
                nodeInfo();
                logger.info("Node is available now starting");
                break;
            } catch (Exception ex) {
                Thread.sleep(wait);
            }
        }
    }
}
