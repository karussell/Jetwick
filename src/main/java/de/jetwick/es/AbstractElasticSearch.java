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

import java.io.IOException;
import java.util.Map;
import org.apache.solr.client.solrj.SolrQuery;
import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.action.index.IndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.xcontent.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public abstract class AbstractElasticSearch {

    private Logger logger = LoggerFactory.getLogger(getClass());
    protected Client client;

    AbstractElasticSearch() {
    }

    public AbstractElasticSearch(Client client) {
        this.client = client;
    }

    public AbstractElasticSearch(String url, String login, String pw) {
        Settings s = ImmutableSettings.settingsBuilder().put("cluster.name", ElasticNode.CLUSTER).build();
        TransportClient tmp = new TransportClient(s);
        tmp.addTransportAddress(new InetSocketTransportAddress(url, 9300));
        client = tmp;
    }

    public abstract String getIndexName();

    public abstract String getIndexType();

    public void nodeInfo() {
        NodesInfoResponse rsp = client.admin().cluster().nodesInfo(new NodesInfoRequest()).actionGet();
        String str = "Cluster:" + rsp.getClusterName() + ". Active nodes:";
        str += rsp.getNodesMap().keySet();
        logger.info(str);
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
        if (!indexExists(getIndexName())) {
            logger.info("Try to create index: " + getIndexName());
            createIndex(getIndexName());
            logger.info("Created index: " + getIndexName());
        } else
            logger.info("Index " + getIndexName() + " already exists");
    }

//    void ping() {
//        waitForYellow();
//        client.admin().cluster().nodesInfo(new NodesInfoRequest()).actionGet();
//        System.out.println("health:"+client.admin().cluster().health(new ClusterHealthRequest(getIndexName())).actionGet().getStatus().name());
    // hmmh here we need indexName again ... but in createIndex it does not exist when calling ping ...
//        client.admin().cluster().ping(new SinglePingRequest(getIndexName(), getIndexType(), "1")).actionGet();
//    }
    void waitForYellow() {
        client.admin().cluster().health(new ClusterHealthRequest(getIndexName()).waitForYellowStatus()).actionGet();
    }

    public void refresh() {
        RefreshResponse rsp = client.admin().indices().refresh(new RefreshRequest(getIndexName())).actionGet();

        //assertEquals(1, rsp.getFailedShards());
    }

    public long countAll() {
        CountResponse response = client.prepareCount(getIndexName()).
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

    public void attachPagability(SolrQuery query, int page, int hitsPerPage) {
        query.setStart(page * hitsPerPage).setRows(hitsPerPage);
    }

    public void deleteAll() {
        //client.prepareIndex().setOpType(OpType.)
        //there is an index delete operation
        // http://www.elasticsearch.com/docs/elasticsearch/rest_api/admin/indices/delete_index/

        client.prepareDeleteByQuery(getIndexName()).
                setQuery(QueryBuilders.matchAllQuery()).
                execute().
                actionGet();
        refresh();
    }
}
