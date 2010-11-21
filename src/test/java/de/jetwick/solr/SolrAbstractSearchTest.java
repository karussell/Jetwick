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

package de.jetwick.solr;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SolrAbstractSearchTest {

    SolrAbstractSearchImpl impl = new SolrAbstractSearchImpl();

    public SolrAbstractSearchTest() {
    }

    @Test
    public void testExpandFQ() {
        SolrQuery q = new SolrQuery().addFilterQuery("{!tag=test}test:hi");
        impl.expandFilterQuery(q, "test2:me", true);
        assertEquals(2, q.getFilterQueries().length);
        assertEquals("{!tag=test2}test2:me", q.getFilterQueries()[1]);

        impl.expandFilterQuery(q, "test3:me", false);
        assertEquals(3, q.getFilterQueries().length);
        assertEquals("test3:me", q.getFilterQueries()[2]);
    }

    @Test
    public void testExpandFQ2() {
        SolrQuery q = new SolrQuery().addFilterQuery("{!tag=test}test:hi");
        impl.expandFilterQuery(q, "test:me", true);
        assertEquals(1, q.getFilterQueries().length);
        assertEquals("{!tag=test}test:hi OR test:me", q.getFilterQueries()[0]);
    }
 @Test
    public void testReplaceFQ() {
        SolrQuery q = new SolrQuery().addFilterQuery("{!tag=test}test:hi");
        impl.replaceFilterQuery(q, "test:new", true);
        assertEquals(1, q.getFilterQueries().length);
        assertEquals("{!tag=test}test:new", q.getFilterQueries()[0]);
    }

    @Test
    public void testReduceFQ() {
        SolrQuery q = new SolrQuery().addFilterQuery("{!tag=test}test:hi");
        assertFalse(impl.reduceFilterQuery(q, "test:me"));
        assertTrue(impl.reduceFilterQuery(q, "test:hi"));
        assertNull(q.getFilterQueries());

        q.addFilterQuery("{!tag=test}test:hi OR test:me");
        assertTrue(impl.reduceFilterQuery(q, "test:me"));
        assertEquals("{!tag=test}test:hi", q.getFilterQueries()[0]);
        q = new SolrQuery().addFilterQuery("{!tag=test}test:hi OR test:me");
        assertTrue(impl.reduceFilterQuery(q, "test:hi"));
        assertEquals("{!tag=test}test:me", q.getFilterQueries()[0]);
    }

    @Test
    public void testApplyFacetChange() {
        SolrQuery q = new SolrQuery().addFilterQuery("{!tag=test}test:hi");
        assertEquals(1, q.getFilterQueries().length);
        impl.applyFacetChange(q, "{!tag=test}test:hi", false);
        assertNull(q.getFilterQueries());

        q = new SolrQuery().addFilterQuery("{!tag=test}test:piep");
        assertEquals(1, q.getFilterQueries().length);
        impl.applyFacetChange(q, "{!tag=test}test", true);
        assertNull(q.getFilterQueries());
    }

    public class SolrAbstractSearchImpl extends SolrAbstractSearch {

        public SolrQuery attachFacetibility(SolrQuery q) {
            return null;
        }

        public QueryResponse search(SolrQuery q) throws SolrServerException {
            return null;
        }
    }
}
