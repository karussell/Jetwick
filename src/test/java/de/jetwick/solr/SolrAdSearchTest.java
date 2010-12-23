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

import de.jetwick.data.AdEntry;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SolrAdSearchTest extends MyAbstractSolrTestCase {

    private SolrAdSearch adSearch;

    public SolrAdSearch getTweetSearch() {
        return adSearch;
    }

    @Override
    public String getSolrHome() {
        return "adindex";
    }

    public SolrAdSearchTest() {
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        EmbeddedSolrServer server = new EmbeddedSolrServer(h.getCoreContainer(), h.getCore().getName());
        adSearch = new SolrAdSearch(server);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testSearch() throws Exception {
        AdEntry pannous = new AdEntry("pannous");
        pannous.addKeyword("pannous");
        pannous.addKeyword("twitter");
        pannous.addQueryUserPair("", "pannous");
        pannous.addQueryUserPair("pannous", "");

        AdEntry jw = new AdEntry("jetwick");
        jw.setDescription("new twitter search");
        jw.addKeyword("twitter");
        jw.addQueryUserPair("", "jetwick");
        jw.addQueryUserPair("jetwick", "");

        AdEntry knh = new AdEntry("kindernothilfe");
        knh.setDescription("kinder in not");
        knh.addKeyword("kinder");

        adSearch.update(Arrays.asList(jw, knh, pannous));
        adSearch.commit();

        List<AdEntry> list = new ArrayList<AdEntry>();
        adSearch.search(list, new SolrQuery("jetwick"));
        assertEquals(1, list.size());
        assertEquals(jw, list.get(0));

        list.clear();
        adSearch.search(list, new SolrQuery("twitter"));
        System.out.println(list);
        assertEquals(2, list.size());
        // prefer jetwick
        assertEquals(jw, list.get(0));
        assertEquals(pannous, list.get(1));
    }

    @Test
    public void testImport() throws Exception {
        Collection<AdEntry> ads = adSearch.importFromFile(new BufferedReader(new StringReader("id\ttitle\tkeywords\tdesc\ticon\tq\tu")));
        assertEquals(1, ads.size());
        AdEntry firstAd = ads.iterator().next();
        assertEquals(1, firstAd.getQueryUserPairs().size());
        assertEquals("q", firstAd.getQueryUserPairs().get(0).getKey());
    }

    @Test
    public void testKeywordSearch() throws Exception {
        adSearch.importFromFile(new BufferedReader(new StringReader("id\ttitle\tkw1; voice action\tdesc\ticon\tq\tu")));
        assertEquals(1, adSearch.search("voice action").size());
        assertEquals(1, adSearch.search("voice actions").size());
    }
}
