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
package de.jetwick.ui;

import de.jetwick.ui.util.FacetHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class FacetPanelTest extends WicketPagesTestClass {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testUpdateFacetLinks() {
        final List<FacetField> fFields = new ArrayList<FacetField>();
        FacetField ff = new FacetField("lang");
        ff.add("de", 3);
        ff.add("other", 2);
        fFields.add(ff);
        fFields.add(new FacetField("tag"));
        fFields.add(new FacetField("user"));

        final List<FacetField> df = new ArrayList<FacetField>();
        df.add(ff);

        FacetPanel panel = (FacetPanel) tester.startPanel(FacetPanel.class);
        // TODO ES
//        QueryResponse qr = new QueryResponse() {
//
//            @Override
//            public List<FacetField> getFacetDates() {
//                return df;
//            }
//
//            @Override
//            public List<FacetField> getFacetFields() {
//                return fFields;
//            }
//        };
//
//        List<Entry<String, List<FacetHelper>>> ret = panel.createFacetsFields(qr);
//
//        assertEquals(7, ret.size());
//
//        // get language
//        List<FacetHelper> dfh = ret.get(1).getValue();
//        assertEquals(2, dfh.size());
//        assertEquals(3, dfh.get(0).count);
//        assertEquals(2, dfh.get(1).count);
    }
}
