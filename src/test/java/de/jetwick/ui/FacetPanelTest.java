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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.ArrayList;
import de.jetwick.ui.util.FacetHelper;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.List;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.facet.Facet;
import org.elasticsearch.search.facet.Facets;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.elasticsearch.search.facet.terms.strings.InternalStringTermsFacet;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

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
        FacetPanel panel = (FacetPanel) tester.startPanel(FacetPanel.class);               
        SearchResponse sr = mock(SearchResponse.class);                
        when(sr.facets()).thenReturn(new Facets() {

            @Override
            public List<Facet> facets() {
                Set<InternalStringTermsFacet.StringEntry> entries = new LinkedHashSet();
                entries.add(new InternalStringTermsFacet.StringEntry("de", 3));
                entries.add(new InternalStringTermsFacet.StringEntry("en", 2));
                TermsFacet tf = new InternalStringTermsFacet("lang", null, 1, entries, 0, 0);                
                Set<InternalStringTermsFacet.StringEntry> entries2 = new LinkedHashSet();
                entries2.add(new InternalStringTermsFacet.StringEntry("peter", 3));
                entries2.add(new InternalStringTermsFacet.StringEntry("karsten", 2));
                TermsFacet tf2 = new InternalStringTermsFacet("user", null, 1, entries2, 0, 0);                
                List<Facet> res = new ArrayList<Facet>();
                res.add(tf);
                res.add(tf2);
                return res;
            }

            @Override
            public Map<String, Facet> getFacets() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public Map<String, Facet> facetsAsMap() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public <T extends Facet> T facet(Class<T> facetType, String name) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public <T extends Facet> T facet(String name) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public Iterator<Facet> iterator() {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        });
        
        List<Entry<String, List<FacetHelper>>> ret = panel.createFacetsFields(sr);
        assertEquals(8, ret.size());

        // get language
        List<FacetHelper> dfh = ret.get(1).getValue();
        assertEquals(2, dfh.size());
        assertEquals(3, dfh.get(0).count);
        assertEquals(2, dfh.get(1).count);
    }
}
