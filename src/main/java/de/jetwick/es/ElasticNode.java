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

import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.common.settings.ImmutableSettings.Builder;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.elasticsearch.index.query.xcontent.XContentQueryBuilder;
import org.elasticsearch.index.query.xcontent.TermFilterBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.elasticsearch.index.query.xcontent.FilterBuilders;
import org.elasticsearch.index.query.xcontent.QueryBuilders;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.client.action.index.IndexRequestBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentBuilder;
import java.io.IOException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.client.Client;
import org.elasticsearch.node.Node;
import static org.elasticsearch.node.NodeBuilder.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class ElasticNode {

    private static Logger logger = LoggerFactory.getLogger(ElasticNode.class);
    public static final String CLUSTER = "jetwickcluster";

    public static void main(String[] args) throws IOException, InterruptedException {
        ElasticNode node = new ElasticNode().start("es");
        node.waitForYellow();

        ElasticTweetSearch twSearch = new ElasticTweetSearch(node.client());
        twSearch.saveCreateIndex();
        
        ElasticUserSearch uSearch = new ElasticUserSearch(node.client());
        uSearch.saveCreateIndex();

        Thread.currentThread().join();
    }

    public static void testLong(String[] args) throws IOException {
        Node node = nodeBuilder().
                local(true).
                settings(ImmutableSettings.settingsBuilder().
                put("index.number_of_shards", 3).
                put("index.number_of_replicas", 1).
                put("gateway.type", "none").
                build()).
                build().
                start();

        String indexName = "tweetindex";
        String indexType = "tweet";
        String fileAsString = "{"
                + "\"tweet\" : {"
                + "    \"properties\" : {"
                + "         \"longval\" : { \"type\" : \"long\", \"null_value\" : -1}"
                + "}}}";

        Client client = node.client();

        // create index
        client.admin().indices().
                create(new CreateIndexRequest(indexName).mapping(indexType, fileAsString)).
                actionGet();
        client.admin().cluster().health(new ClusterHealthRequest(indexName).waitForYellowStatus()).actionGet();

        XContentBuilder docBuilder = XContentFactory.jsonBuilder().startObject();
        docBuilder.field("longval", 124L);
        docBuilder.endObject();

        // feed previously created doc
        IndexRequestBuilder irb = client.prepareIndex(indexName, indexType, "1").
                setConsistencyLevel(WriteConsistencyLevel.DEFAULT).
                setSource(docBuilder);
        irb.execute().actionGet();

        // make doc available for sure
        client.admin().indices().refresh(new RefreshRequest(indexName)).actionGet();

        // query for this doc
        XContentQueryBuilder qb = QueryBuilders.matchAllQuery();
        TermFilterBuilder fb = FilterBuilders.termFilter("longval", 124L);
        SearchRequestBuilder srb = client.prepareSearch(indexName).
                setSearchType(SearchType.QUERY_AND_FETCH).
                setQuery(QueryBuilders.filteredQuery(qb, fb));
        SearchResponse response = srb.execute().actionGet();
        System.out.println("failed shards:" + response.getFailedShards());
        Object num = response.getHits().hits()[0].getSource().get("longval");
        System.out.println("longval:" + num);
        System.out.println("longval.getClass:" + num.getClass());
        node.stop();
    }
    private Node node;
    private boolean started = false;

    public ElasticNode start(String dataHome) {
        return start(dataHome, dataHome + "/config", false);
    }

    public ElasticNode start(String home, String conf, boolean testing) {
        // see
        // http://www.elasticsearch.com/docs/elasticsearch/setup/installation/
        // http://www.elasticsearch.com/docs/elasticsearch/setup/dirlayout/
        File homeDir = new File(home);
        System.setProperty("es.path.home", homeDir.getAbsolutePath());
        System.setProperty("es.path.conf", conf);

        Builder settings = ImmutableSettings.settingsBuilder().
                put("network.host", "127.0.0.1").
                //                put("network.bindHost", "127.0.0.0").
                //                put("network.publishHost", "127.0.0.0").
                put("index.number_of_shards", 3).
                put("index.number_of_replicas", 1);

        if (testing) {
            settings.put("gateway.type", "none");
            // default is local
            // none means no data after node restart!
            // does not work when transportclient connects:
//                put("gateway.type", "fs").
//                put("gateway.fs.location", homeDir.getAbsolutePath()).
        }

        settings.build();
        NodeBuilder nBuilder = nodeBuilder().settings(settings);
        if (!testing) {
            nBuilder.clusterName(CLUSTER);
        } else {
            nBuilder.local(true);
        }

        node = nBuilder.build().start();
        started = true;
        logger.info("Started Node in cluster " + CLUSTER + ". Home folder: " + homeDir.getAbsolutePath());
        return this;
    }

    public void stop() {
        if (node == null)
            throw new RuntimeException("Node not started");
        
        started = false;
        node.close();
    }

    public boolean isStarted() {
        return started;
    }        

    public Client client() {
        if (node == null)
            throw new RuntimeException("Node not started");

        return node.client();
    }

    /**
     * Warning: Can take several 10 seconds!
     */
    public void waitForYellow() {
        node.client().admin().cluster().health(new ClusterHealthRequest("twindex").waitForYellowStatus()).actionGet();
        logger.info("Now node status is 'yellow'!");
    }
    
    public void waitForOneActiveShard() {
        node.client().admin().cluster().health(new ClusterHealthRequest("twindex").waitForActiveShards(1)).actionGet();
        logger.info("Now node has at least one active shard!");
    }
}
