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
import java.io.IOException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
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
    public static final int PORT = 9300;

    public static void main(String[] args) throws IOException, InterruptedException {
        ElasticNode node = new ElasticNode().start("es");
        node.waitForYellow();
        node.printInfo();

        // normally we create indices via scripts but a dev can get faster started
        ElasticTweetSearch twSearch = new ElasticTweetSearch(node.client());
        twSearch.saveCreateIndex();

        ElasticUserSearch uSearch = new ElasticUserSearch(node.client());
        uSearch.saveCreateIndex();

        ElasticTagSearch tagSearch = new ElasticTagSearch(node.client());
        tagSearch.saveCreateIndex();
        
        Thread.currentThread().join();        
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

        // increase maxClauseCount for friend search ... not necessary
        // http://wiki.apache.org/lucene-java/LuceneFAQ#Why_am_I_getting_a_TooManyClauses_exception.3F
//        BooleanQuery.setMaxClauseCount(100000);

        Builder settings = ImmutableSettings.settingsBuilder();
//                put("network.host", "127.0.0.1").
//                //                put("network.bindHost", "127.0.0.0").
//                //                put("network.publishHost", "127.0.0.0").
//                put("index.number_of_shards", 16).
//                put("index.number_of_replicas", 1);

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

    public void printInfo() {
        NodesInfoResponse rsp = node.client().admin().cluster().nodesInfo(new NodesInfoRequest()).actionGet();
        String str = "Cluster:" + rsp.getClusterName() + ". Active nodes:";
        str += rsp.getNodesMap().keySet();
        logger.info(str);
    }
}
