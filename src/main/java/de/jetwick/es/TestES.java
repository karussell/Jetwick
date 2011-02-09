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

import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import java.util.Collection;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.common.unit.TimeValue;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Arrays;
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
        String resindex = "resindex";
        createIndex(index1);
        createIndex(resindex);
        waitForYellow(resindex);

        boolean showStrangeBehaviour = true;
        boolean test1 = true;
        if (showStrangeBehaviour) {
            if (test1)
                // will result in "collected:185" BUT should be 200
                feedDoc(index1, createDoc(2), "0");
            else
                // collected:169 BUT should be 200
                feedDoc(index1, createDoc(2), "199");
        } else {
            if (test1)
                // collected:201 is okay
                feedDoc(index1, createDoc(2), "-1");
            else
                // collected:201 is okay
                feedDoc(index1, createDoc(2), "200");
        }
//        Thread.sleep(10000);
        // create some equal content from 0..199
        Map<String, XContentBuilder> map = new LinkedHashMap<String, XContentBuilder>();
        for (int i = 0; i < 200; i++) {
            map.put("" + i, createDoc(i));
        }
        bulkUpdate(map, index1);

//        Thread.sleep(10000);
        System.out.println("index1:" + countAll(index1) + " resindex:" + countAll(resindex));
        mergeIndices(Arrays.asList(index1), resindex, 2);

        logger.info("200? " + countAll(resindex));

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

    public XContentBuilder createDoc(int num) throws IOException {
        XContentBuilder docBuilder = JsonXContent.unCachedContentBuilder().startObject();
        docBuilder.field(NUM, num);
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

    public void mergeIndices(Collection<String> indexList, String intoIndex, int hitsPerPage) {
        refresh(indexList.toArray(new String[0]));
        refresh(intoIndex);

        for (String fromIndex : indexList) {        
            SearchResponse rsp = client.prepareSearch(fromIndex).
                    setQuery(QueryBuilders.matchAllQuery()).setSize(hitsPerPage).
                    addSort("_id", SortOrder.ASC).
                    // important to use QUERY_THEN_FETCH !
                    setSearchType(SearchType.QUERY_THEN_FETCH).
                    setScroll(TimeValue.timeValueMinutes(30)).execute().actionGet();
            try {
                long total = rsp.hits().totalHits();
                int collectedResults = 0;
                
                do {
//                while ((currentResults = rsp.hits().hits().length) > 0) {
                    Map<String, XContentBuilder> docs = collectDocs(rsp);
                    bulkUpdate(docs, intoIndex);
                    collectedResults += rsp.hits().hits().length;;
                    rsp = client.prepareSearchScroll(rsp.scrollId()).
                            setScroll(TimeValue.timeValueMinutes(30)).execute().actionGet();
                    logger.info("Progress " + collectedResults + "/" + total + " fromIndex=" + fromIndex);
                } while (rsp.hits().hits().length > 0);
                logger.info("Finished copying of index " + fromIndex + ". Total:" + total + " collected:" + collectedResults);
            } catch (Exception ex) {
                logger.error("Failed to copy data from index " + fromIndex + " into " + intoIndex + ".", ex);
            }
        }

        refresh(intoIndex);
    }

    public Map<String, XContentBuilder> collectDocs(SearchResponse rsp) throws IOException {
        SearchHits docs = rsp.getHits();
        Map<String, XContentBuilder> list = new LinkedHashMap<String, XContentBuilder>();

        for (SearchHit sd : docs) {
            int num = (Integer) sd.getSource().get(NUM);
            list.put(sd.getId(), createDoc(num));
        }

        return list;
    }

    public void feedDoc(String indexName, XContentBuilder b, String id) {
        IndexRequestBuilder irb = client.prepareIndex(indexName, indexType, id).
                setConsistencyLevel(WriteConsistencyLevel.DEFAULT).setRefresh(true).
                setSource(b);
        irb.execute().actionGet();
    }
}
