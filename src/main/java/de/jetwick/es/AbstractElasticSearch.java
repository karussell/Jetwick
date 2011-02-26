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
import de.jetwick.util.Helper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.optimize.OptimizeRequest;
import org.elasticsearch.action.admin.indices.optimize.OptimizeResponse;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.action.index.IndexRequestBuilder;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.StopWatch;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.xcontent.QueryBuilders;
import org.elasticsearch.index.query.xcontent.XContentFilterBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public abstract class AbstractElasticSearch<T extends DbObject> {

    private Logger logger = LoggerFactory.getLogger(getClass());
    protected Client client;

    AbstractElasticSearch() {
    }

    public AbstractElasticSearch(Client client) {
        this.client = client;
    }

    public AbstractElasticSearch(String url, String login, String pw) {
        client = createClient(ElasticNode.CLUSTER, url, ElasticNode.PORT);
    }

    public static Client createClient(String cluster, String url, int port) {
        Settings s = ImmutableSettings.settingsBuilder().put("cluster.name", cluster).build();
        TransportClient tmp = new TransportClient(s);
        tmp.addTransportAddress(new InetSocketTransportAddress(url, port));
        return tmp;
    }

    public abstract String getIndexName();

    public abstract void setIndexName(String indexName);

    public abstract String getIndexType();

    public void update(T obj, boolean refresh) {
        try {
            XContentBuilder b = createDoc(obj);
            if (b != null)
                feedDoc(obj.getId(), b);

            if (refresh)
                refresh();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean indexExists(String indexName) {
        // make sure node is up to create the index otherwise we get: blocked by: [1/not recovered from gateway];
        // waitForYellow();

//        Map map = client.admin().cluster().health(new ClusterHealthRequest(indexName)).actionGet().getIndices();
        Map map = client.admin().cluster().prepareState().execute().actionGet().getState().getMetaData().getIndices();
//        System.out.println("Index info:" + map);
        return map.containsKey(indexName);
    }

    public void createIndex(String indexName) {
        // no need for the following because of _default mapping under config
        // String fileAsString = Helper.readInputStream(getClass().getResourceAsStream("tweet.json"));
        // new CreateIndexRequest(indexName).mapping(indexType, fileAsString)
        client.admin().indices().create(new CreateIndexRequest(indexName)).actionGet();
//        waitForYellow();
    }

    public void saveCreateIndex() {
        saveCreateIndex(getIndexName(), true);
    }

    public void saveCreateIndex(String name, boolean log) {
//         if (!indexExists(name)) {
        try {
            createIndex(name);
            if (log)
                logger.info("Created index: " + name);
        } catch (Exception ex) {
//        } else {
            if (log)
                logger.info("Index " + getIndexName() + " already exists");
        }
    }

//    void ping() {
//        waitForYellow();
//        client.admin().cluster().nodesInfo(new NodesInfoRequest()).actionGet();
//        System.out.println("health:"+client.admin().cluster().health(new ClusterHealthRequest(getIndexName())).actionGet().getStatus().name());
    // hmmh here we need indexName again ... but in createIndex it does not exist when calling ping ...
//        client.admin().cluster().ping(new SinglePingRequest(getIndexName(), getIndexType(), "1")).actionGet();
//    }
    void waitForYellow() {
        waitForYellow(getIndexName());
    }

    void waitForYellow(String name) {
        client.admin().cluster().health(new ClusterHealthRequest(name).waitForYellowStatus()).actionGet();
    }

    void waitForGreen(String name) {
        client.admin().cluster().health(new ClusterHealthRequest(name).waitForGreenStatus()).actionGet();
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

    public void feedDoc(String twitterId, XContentBuilder b) {
//        String getIndexName() = new SimpleDateFormat("yyyyMMdd").format(tw.getCreatedAt());
        IndexRequestBuilder irb = client.prepareIndex(getIndexName(), getIndexType(), twitterId).
                setConsistencyLevel(WriteConsistencyLevel.DEFAULT).
                setSource(b);
        irb.execute().actionGet();
    }

    public void deleteById(String id) {
        DeleteResponse response = client.prepareDelete(getIndexName(), getIndexType(), id).
                execute().
                actionGet();
    }

    public void deleteAll() {
        deleteAll(getIndexName());
    }

    public void deleteAll(String indexName) {
        //client.prepareIndex().setOpType(OpType.)
        //there is an index delete operation
        // http://www.elasticsearch.com/docs/elasticsearch/rest_api/admin/indices/delete_index/

        client.prepareDeleteByQuery(indexName).
                setQuery(QueryBuilders.matchAllQuery()).
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

    public List<T> collectObjects(SearchResponse rsp) {
        SearchHits docs = rsp.getHits();
        List<T> list = new ArrayList<T>();

        for (SearchHit sd : docs) {
            list.add(readDoc(sd.getSource(), sd.getId()));
        }

        return list;
    }

    public abstract T readDoc(Map<String, Object> source, String idAsStr);

    public abstract XContentBuilder createDoc(T tw) throws IOException;

    /**
     * All indices has to be created before!
     */
    public void mergeIndices(Collection<String> indexList, String intoIndex, int hitsPerPage, boolean forceRefresh,
            XContentFilterBuilder additionalFilter) {
        if (forceRefresh) {
            refresh(indexList);
            refresh(intoIndex);
        }

        for (String fromIndex : indexList) {
            SearchRequestBuilder srb = client.prepareSearch(fromIndex).
                    setQuery(QueryBuilders.matchAllQuery()).setSize(hitsPerPage).
                    setSearchType(SearchType.SCAN).
                    setScroll(TimeValue.timeValueMinutes(30));
            if (additionalFilter != null)
                srb.setFilter(additionalFilter);
            SearchResponse rsp = srb.execute().actionGet();

            try {
                long total = rsp.hits().totalHits();
                String scrollId = rsp.scrollId();
                int collectedResults = 0;
                while (true) {
                    StopWatch queryWatch = new StopWatch().start();
//                    System.out.println("NOW!!!");
                    rsp = client.prepareSearchScroll(scrollId).
                            setScroll(TimeValue.timeValueMinutes(30)).execute().actionGet();
                    long currentResults = rsp.hits().totalHits();
                    if (currentResults == 0)
                        break;

                    queryWatch.stop();
                    Collection tweets = collectObjects(rsp);
                    StopWatch updateWatch = new StopWatch().start();
                    bulkUpdate(tweets, intoIndex);
                    updateWatch.stop();
                    collectedResults += currentResults;
                    logger.info("Progress " + collectedResults + "/" + total + " fromIndex="
                            + fromIndex + " update:" + updateWatch.totalTime().getSeconds() + " query:" + queryWatch.totalTime().getSeconds());
                }
                logger.info("Finished copying of index:" + fromIndex + ". Total:" + total + " collected:" + collectedResults);
            } catch (Exception ex) {
                logger.error("Failed to copy data from index " + fromIndex + " into " + intoIndex + ".", ex);
            }
        }

        if (forceRefresh)
            refresh(intoIndex);
    }

    public void bulkUpdate(Collection<T> objects, String indexName) throws IOException {
        bulkUpdate(objects, indexName, false);
    }

    public void bulkUpdate(Collection<T> objects, String indexName, boolean refresh) throws IOException {
        // now using bulk API instead of feeding each doc separate with feedDoc
        BulkRequestBuilder brb = client.prepareBulk();
        for (T o : objects) {
            if (o.getId() == null) {
                logger.warn("Skipped tweet without twitterid when bulkUpdate:" + o);
                continue;
            }

            XContentBuilder source = createDoc(o);
            brb.add(Requests.indexRequest(indexName).type(getIndexType()).id(o.getId()).source(source));
            brb.setRefresh(refresh);
        }
        if (brb.numberOfActions() > 0) {
//            System.out.println("actions:" + brb.numberOfActions());
            BulkResponse rsp = brb.execute().actionGet();
            if (rsp.hasFailures())
                logger.error("Error while bulkUpdate:" + rsp.buildFailureMessage());
        }
    }
}
