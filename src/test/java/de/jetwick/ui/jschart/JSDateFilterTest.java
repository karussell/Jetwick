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

import de.jetwick.es.ElasticTweetSearch;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.facet.range.RangeFacet.Entry;
import org.elasticsearch.search.facet.range.RangeFacet;
import de.jetwick.ui.WicketPagesTestClass;
import de.jetwick.ui.util.FacetHelper;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.facet.Facet;
import org.elasticsearch.search.facet.Facets;
import org.elasticsearch.search.facet.range.InternalRangeFacet;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class JSDateFilterTest extends WicketPagesTestClass {

    public JSDateFilterTest() {
    }

    @Test
    public void testDateFacets() throws Exception {
        RangeFacet.Entry entries[] = new RangeFacet.Entry[2];        
        entries[0] = newEntry("-Infinity", "xy");        
        entries[1] = newEntry("xy", "Infinity");
        final RangeFacet rf = new InternalRangeFacet(ElasticTweetSearch.DATE_FACET, entries);

        SearchHits sh = mock(SearchHits.class);
        when(sh.getTotalHits()).thenReturn(10L);

        SearchResponse sr = mock(SearchResponse.class);
        when(sr.hits()).thenReturn(sh);
        when(sr.facets()).thenReturn(new Facets() {

            @Override
            public List<Facet> facets() {
                throw new UnsupportedOperationException("Not supported yet.");
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
            public <T extends Facet> T facet(Class< T> facetType, String name) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public <T extends Facet> T facet(String name) {
                return (T) rf;
            }

            @Override
            public Iterator<Facet> iterator() {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        });
        JSDateFilter panel = (JSDateFilter) tester.startPanel(JSDateFilter.class);
        panel.update(sr);
        List<FacetHelper> dfh = panel.getFacetList();

        assertEquals("Last 8h", dfh.get(0).displayName);
        assertEquals("Older", dfh.get(1).displayName);                
    }

    private Entry newEntry(String from, String to) {
        RangeFacet.Entry e = mock(RangeFacet.Entry.class);
        when(e.getFromAsString()).thenReturn(from);
        when(e.getToAsString()).thenReturn(to);
        return e;
    }
}
