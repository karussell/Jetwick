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

package de.jetwick.ui.jschart;

import de.jetwick.ui.util.FacetHelper;
import de.jetwick.ui.WicketPagesTestClass;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class JSDateFilterTest extends WicketPagesTestClass {

    public JSDateFilterTest() {
    }

    @Test
    public void testDateFacets() {
        FacetField ff = new FacetField("dt", "+1DAY", new Date());
        String key1 = "2010-08-13T00:00:00Z TO 2010-08-13T00:00:00Z/DAY+1DAY";
        ff.add(key1, 2);
        final Map<String, Integer> facetQueries = new HashMap<String, Integer>();
        
        // TODO ES
//        facetQueries.put(ElasticTweetSearch.FILTER_ENTRY_LATEST_DT, 123);

        final List<FacetField> df = new ArrayList<FacetField>();
        df.add(ff);
        
        // TODO ES
//        QueryResponse qr = new QueryResponse() {
//
//            @Override
//            public SolrDocumentList getResults() {
//                SolrDocumentList list = new SolrDocumentList();
//                list.add(new SolrDocument());
//                return list;
//            }
//
//            @Override
//            public List<FacetField> getFacetDates() {
//                return df;
//            }
//
//            @Override
//            public Map<String, Integer> getFacetQuery() {
//                return facetQueries;
//            }
//        };
//
//        JSDateFilter panel = (JSDateFilter) tester.startPanel(JSDateFilter.class);
//        panel.update(qr);
//        List<FacetHelper> dfh = panel.getFacetList();
//        assertEquals("older", dfh.get(2).displayName);
//        assertEquals("08-13", dfh.get(1).displayName);
//        assertEquals("[" + key1 + " TO " + key1 + "/DAY+1DAY]", dfh.get(1).value);
//
//        assertEquals("last 8h", dfh.get(0).displayName);                
        //assertEquals(ElasticTweetSearch.FILTER_VALUE_LATEST_DT, dfh.get(0).value);
    }
}
