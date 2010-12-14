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

import java.util.Set;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SolrUserSearchTest extends MyAbstractSolrTestCase {

    private SolrUserSearch userSearch;

    public SolrUserSearch getUserSearch() {
        return userSearch;
    }

    @Override
    public String getSolrHome() {
        return "uindex";
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        EmbeddedSolrServer server = new EmbeddedSolrServer(h.getCoreContainer(), h.getCore().getName());
        userSearch = new SolrUserSearch(server);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testDelete() throws Exception {
        SolrUser user = new SolrUser("karsten");

        userSearch.save(user, true);
        assertEquals(1, userSearch.search("karsten").size());

        userSearch.delete(user, true);
        assertEquals(0, userSearch.search("karsten").size());
    }

    @Test
    public void testUpdate() throws Exception {
        assertEquals(0, userSearch.search("karsten").size());
        SolrUser user = new SolrUser("karsten");

        userSearch.save(user, true);
        assertEquals(1, userSearch.search("karsten").size());

        user = new SolrUser("karsten");
        user.setDescription("test");
        userSearch.save(user, true);

        assertEquals(1, userSearch.search("test").size());

        user = new SolrUser("peter");
        new SolrTweet(4, "users without a tweet get indexed!", user);
        userSearch.update(user, true, true);

        assertEquals(1, userSearch.search("peter").size());
    }

    @Test
    public void testUpdate2() throws Exception {
        SolrUser user = new SolrUser("karsten");
        user.addSavedSearch(new SavedSearch(1, new SolrQuery("test")));
        user.addSavedSearch(new SavedSearch(2, new SolrQuery("test2")));
        userSearch.save(user, true);
        assertEquals(1, userSearch.search("karsten").size());
        user = userSearch.search("karsten").iterator().next();
        assertEquals(2, user.getSavedSearches().size());
    }

    @Test
    public void testUpdateBatch() throws Exception {
        Set<SolrUser> list = new LinkedHashSet<SolrUser>();
        SolrUser user = new SolrUser("karsten");
        list.add(user);
        SolrUser user2 = new SolrUser("peter");
        list.add(user2);
        userSearch.update(list, 2);
        userSearch.commit();
        assertEquals(1, userSearch.search("karsten").size());
        assertEquals(1, userSearch.search("peter").size());

        userSearch.delete(user, false);
        userSearch.delete(user2, true);
        assertEquals(0, userSearch.search("karsten").size());
        assertEquals(0, userSearch.search("peter").size());

        userSearch.update(list, 1);
        userSearch.commit();
        assertEquals(1, userSearch.search("karsten").size());
        assertEquals(1, userSearch.search("peter").size());
    }

    @Test
    public void testMltNoErrorIfUserNotFound() throws Exception {
        // see https://issues.apache.org/jira/browse/SOLR-2005
//        Collection<SolrUser> list = new LinkedHashSet<SolrUser>();
//        userSearch.searchMoreLikeThis(list, "peter", 10, 0, true);
//        assertEquals(1, list.size());
    }

    @Test
    public void testMoreLikeThis() throws Exception {
        SolrUser karsten = new SolrUser("karsten");
        karsten.setDescription("hooping hooping nice solr");
        userSearch.save(karsten, false);

        SolrUser pet = new SolrUser("peter");
        pet.setDescription("hooping hooping nice solr");
        userSearch.save(pet, false);

        SolrUser joh = new SolrUser("johannes");
        karsten.setDescription("windows rocks!");
        userSearch.save(joh, true);

        Collection<SolrUser> list = new LinkedHashSet<SolrUser>();
        userSearch.searchMoreLikeThis(list, "peter", 10, 0, true);
        assertEquals(1, list.size());
        assertEquals(karsten, list.iterator().next());
    }

    @Test
    public void testMltPaging() throws Exception {
        SolrUser karsten = new SolrUser("karsten");
        karsten.setDescription("hooping hooping; nice is solr");
        userSearch.save(karsten, false);

        SolrUser pet = new SolrUser("peter");
        pet.setDescription("hooping hooping; solr is nice rocks; what do you need?");
        userSearch.save(pet, false);

        SolrUser joh = new SolrUser("johannes");
        joh.setDescription("hooping hooping; solr is nice rocks; what do you need?");
        userSearch.save(joh, true);

        Collection<SolrUser> list = new LinkedHashSet<SolrUser>();
        long ret = userSearch.searchMoreLikeThis(list, "peter", 2, 0, true);
        assertEquals(2, ret);
        assertEquals(2, list.size());
        list.clear();
        ret = userSearch.searchMoreLikeThis(list, "peter", 1, 0, true);
        assertEquals(2, ret);
        assertEquals(karsten, list.iterator().next());

        list.clear();
        ret = userSearch.searchMoreLikeThis(list, "peter", 1, 1, true);
        assertEquals(2, ret);
        assertEquals(joh, list.iterator().next());
    }

    @Test
    public void testUnderscoreInName() throws Exception {
        SolrUser karsten = new SolrUser("karsten");
        karsten.setDescription("hooping hooping nice solr");
        userSearch.save(karsten, false);

        SolrUser korland = new SolrUser("g_korland");
        korland.setDescription("hooping hooping solr nice");
        userSearch.save(korland, true);

        Collection<SolrUser> list = new LinkedHashSet<SolrUser>();
        userSearch.search(list, "hooping", 10, 0);
        assertEquals(2, list.size());

        list = new LinkedHashSet<SolrUser>();
        userSearch.search(list, "g_korland", 10, 0);
        assertEquals(1, list.size());
    }

    @Test
    public void testPaging() throws SolrServerException {
        for (int i = 0; i < 5; i++) {
            SolrUser karsten = new SolrUser("karsten" + i);
            karsten.setDescription("hooping hooping nice solr");
            userSearch.save(karsten, false);
        }
        userSearch.commit();

        Collection<SolrUser> list = new LinkedHashSet<SolrUser>();
        assertEquals(5, userSearch.search(list, "hooping", 3, 0));
        assertEquals(3, list.size());
        list.clear();
        assertEquals(5, userSearch.search(list, "hooping", 3, 1));
        assertEquals(2, list.size());
    }

    @Test
    public void testUserFind() throws SolrServerException {
        SolrUser karsten = new SolrUser("karsten");
        karsten.addOwnTweet(new SolrTweet(1, "hooping hooping", karsten));
        karsten.addOwnTweet(new SolrTweet(2, "nice solr", karsten));
        userSearch.save(karsten, true);

        Collection<SolrUser> list = new LinkedHashSet<SolrUser>();
        assertEquals(1, userSearch.search(list, "karsten", 3, 0));
        assertEquals(1, list.size());

        list = new LinkedHashSet<SolrUser>();
        assertEquals(0, userSearch.search(list, "kasten", 3, 0));
        assertEquals(0, list.size());
    }

    @Test
    public void testFacetSearch() throws SolrServerException {
        userSearch.setTermMinFrequency(0);

        SolrUser karsten = new SolrUser("karsten");
        new SolrTweet(1L, "test test", karsten);
        new SolrTweet(2L, "help help java", karsten);
        userSearch.save(karsten, false);

        SolrUser peter = new SolrUser("peter");
        new SolrTweet(3L, "test test", peter);
        new SolrTweet(4L, "bla bli java", peter);
        userSearch.save(peter, true);

        // now createTags: test, java, ...        

        // one can even use solrQuery.set("f.myField.facet.limit",10)
        SolrQuery query = new UserQuery("java");
        query.addFilterQuery("tag:test");

        Collection<SolrUser> list = new LinkedHashSet<SolrUser>();
        QueryResponse rsp = userSearch.search(list, query);
        Count cnt = rsp.getFacetField("tag").getValues().get(0);

        // found 2 docs
        assertEquals(2, list.size());

        // found 2 docs which have java as tags
        assertEquals("java", cnt.getName());
        assertEquals(2, cnt.getCount());

        // more filter queries
        list.clear();
        query.addFilterQuery("tag:help");
        userSearch.search(list, query);
        assertEquals(1, list.size());

        list.clear();
        query.addFilterQuery("tag:z");
        userSearch.search(list, query);
        assertEquals(0, list.size());
    }

    @Test
    public void testIsMlt() {
        SolrQuery query = userSearch.createMltQuery("peter");
        assertTrue(userSearch.isMlt(query));
    }

    @Test
    public void testFilterLang() throws Exception {
        Map<String, Integer> langs = new HashMap<String, Integer>();
        langs.put("en", 100);
        langs.put("de", 100);
        assertEquals(2, SolrUserSearch.filterLanguages(langs).size());

        langs = new HashMap<String, Integer>();
        langs.put("en", 100);
        langs.put("de", 3);
        assertEquals(1, SolrUserSearch.filterLanguages(langs).size());

        langs = new HashMap<String, Integer>();
        langs.put("en", 100);
        langs.put("de", 4);
        assertEquals(2, SolrUserSearch.filterLanguages(langs).size());
    }

    @Test
    public void testGetQueryTerms() throws Exception {
        SolrUser user = new SolrUser("karsten");
        user.addSavedSearch(new SavedSearch(1, new SolrQuery("peter test")));
        user.addSavedSearch(new SavedSearch(2, new SolrQuery("peter tester")));
        userSearch.save(user, false);
        user = new SolrUser("peter");
        user.addSavedSearch(new SavedSearch(1, new SolrQuery("peter test")));
        user.addSavedSearch(new SavedSearch(2, new SolrQuery("karsten tester")));
        userSearch.save(user, true);

        Collection<String> coll = userSearch.getQueryTerms();
        assertEquals(3, coll.size());
        assertTrue(coll.contains("peter test"));
        assertTrue(coll.contains("peter tester"));
        assertTrue(coll.contains("karsten tester"));
    }
    
//    @Test
//    public void testGetLastQuery() throws Exception {
//        SolrUser user = new SolrUser("karsten");
//        user.addSavedSearch(new SavedSearch(0, new SolrQuery("test")));
//        userSearch.save(user, true);
//        assertEquals("test", userSearch.findByScreenName("karsten").getSavedSearch(0).getQueryTerm());
//    }
}
