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

import java.util.TreeSet;
import org.elasticsearch.search.facet.termsstats.TermsStatsFacet.ComparatorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.elasticsearch.action.search.SearchResponse;
import java.util.Map.Entry;
import java.util.Map;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.count.CountResponse;
import java.io.IOException;
import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.action.index.IndexRequestBuilder;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.xcontent.QueryBuilders;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.node.Node;
import org.elasticsearch.search.facet.AbstractFacetBuilder;
import org.elasticsearch.search.facet.FacetBuilders;
import org.elasticsearch.search.facet.termsstats.TermsStatsFacet;
import static org.elasticsearch.node.NodeBuilder.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TestES {

    public static void main(String[] args) throws Exception {
        new TestES().start();
    }
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Client client;
    private String indexType = "tweet";
    private String NUM = "num";
    private String TITLE = "title";

    public void start() throws IOException, InterruptedException {
        Node node = nodeBuilder().
                //                local(true).
                settings(ImmutableSettings.settingsBuilder().
                put("index.number_of_shards", 2).
                put("index.number_of_replicas", 0).
                put("gateway.type", "none").
                build()).
                build().
                start();

        client = node.client();

// create indices
        String index1 = "index1";
        createIndex(index1);
        waitForYellow(index1);

        for (int i = 0; i < 20; i++) {
            feedDoc(index1, createDoc(i%10, "test this again"), "" + i);
        }

        String facetField = NUM;
        AbstractFacetBuilder fb = FacetBuilders.termsStats(facetField).keyField(facetField).valueScript("doc.score").order(ComparatorType.TOTAL);
//        XContentQueryBuilder qb = QueryBuilders.queryString(rand)constantScoreQuery();
        SearchResponse rsp = client.prepareSearch(index1).
                setQuery(QueryBuilders.matchAllQuery()).
                addFacet(fb).
                execute().actionGet();

        TermsStatsFacet f = (TermsStatsFacet) rsp.facets().facet(facetField);

        for (TermsStatsFacet.Entry e : f.entries()) {
            System.out.println("term:" + e.term() + "\t count:" + e.count() + "\t total:" + e.total());// always 1? + "\t" + e.mean());
        }

        System.out.println("there should be 10 terms. there were:" + f.entries().size());
        
        node.stop();
    }

    public void createIndex(String indexName) {
        try {
            client.admin().indices().create(new CreateIndexRequest(indexName)).actionGet();
        } catch (IndexAlreadyExistsException ex) {
            logger.info(indexName + " already exists!", ex);
        }
    }

    public void waitForYellow(String indexName) {
        client.admin().cluster().health(new ClusterHealthRequest(indexName).waitForYellowStatus()).actionGet();
    }

    public void refresh(String indexName) {
        client.admin().indices().refresh(new RefreshRequest(indexName)).actionGet();
    }

    public void refresh(String... indices) {
        client.admin().indices().refresh(new RefreshRequest(indices)).actionGet();
    }

    public long countAll(String indexName) {
        CountResponse response = client.prepareCount(indexName).
                setQuery(QueryBuilders.matchAllQuery()).
                execute().actionGet();
        return response.getCount();
    }

    public XContentBuilder createDoc(int num, String title) throws IOException {
        XContentBuilder docBuilder = JsonXContent.unCachedContentBuilder().startObject();
        docBuilder.field(NUM, num);
        docBuilder.field(TITLE, title);
        docBuilder.endObject();
        return docBuilder;
    }

    public void bulkUpdate(Map<String, XContentBuilder> docs, String indexName) {
        // now using bulk API instead of feeding each doc separate with feedDoc
        BulkRequestBuilder brb = client.prepareBulk();
        for (Entry<String, XContentBuilder> e : docs.entrySet()) {
            brb.add(Requests.indexRequest(indexName).type(indexType).id(e.getKey()).source(e.getValue()));
            brb.setRefresh(true);
        }
        if (brb.numberOfActions() > 0) {
            BulkResponse rsp = brb.execute().actionGet();
            System.out.println(rsp.items().length);
            if (rsp.hasFailures())
                System.out.println("Error while bulkUpdate:" + rsp.buildFailureMessage());
        }
    }

    public void feedDoc(String indexName, XContentBuilder b, String id) {
        IndexRequestBuilder irb = client.prepareIndex(indexName, indexType, id).
                setConsistencyLevel(WriteConsistencyLevel.DEFAULT).setRefresh(true).
                setSource(b);
        irb.execute().actionGet();
    }
}
