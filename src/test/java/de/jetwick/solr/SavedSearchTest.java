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
package de.jetwick.solr;

import org.apache.solr.client.solrj.SolrQuery;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class SavedSearchTest {

    public SavedSearchTest() {
    }

    @Test
    public void testCalcQueryTerms() {
        assertEquals(0, new SavedSearch(new SolrQuery()).calcQueryTerms().size());
        assertEquals(0, new SavedSearch(new SolrQuery("")).calcQueryTerms().size());
        assertEquals(0, new SavedSearch(new SolrQuery(" ")).calcQueryTerms().size());
        assertEquals(2, new SavedSearch(new SolrQuery(" test pest")).calcQueryTerms().size());
        assertEquals(1, new SavedSearch(new SolrQuery(" test test")).calcQueryTerms().size());
    }

    @Test
    public void testGetQuery() {
        assertNull(new SavedSearch(new SolrQuery().addFilterQuery("dt:[1 TO 2]")).getQueryWithoutDate().getFilterQueries());
        assertEquals(1, new SavedSearch(new SolrQuery().addFilterQuery("dt:[1 TO 2]").addFilterQuery("xy:ab")).getQueryWithoutDate().getFilterQueries().length);
    }
}
