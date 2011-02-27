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
package de.jetwick.es;

import java.util.Collections;
import java.util.Arrays;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.Before;
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class ElasticUserSearchTest extends AbstractElasticSearchTester {

    private static ElasticUserSearch userSearch;
//    private Logger logger = LoggerFactory.getLogger(getClass());

    public ElasticUserSearch getSearch() {
        return userSearch;
    }

    @Before
    public void setUp() throws Exception {
        userSearch = new ElasticUserSearch(getClient());
        super.setUp(userSearch);
    }   

    @Test
    public void testDelete() throws Exception {
        JUser user = new JUser("karsten");

        userSearch.save(user, true);
        assertEquals(1, userSearch.search("karsten").size());

        userSearch.delete(user, true);
        assertEquals(0, userSearch.search("karsten").size());
    }

    @Test
    public void testUpdate() throws Exception {
        assertEquals(0, userSearch.search("karsten").size());
        JUser user = new JUser("karsten");

        userSearch.save(user, true);
        assertEquals(1, userSearch.search("karsten").size());

        user = new JUser("karsten");
        user.setDescription("test");
        userSearch.save(user, true);

        assertEquals(1, userSearch.search("test").size());

        user = new JUser("peter");
        new JTweet(4, "users without a tweet get indexed!", user);
        userSearch.update(user, true, true);

        assertEquals(1, userSearch.search("peter").size());
    }

    @Test
    public void testUpdate2() throws Exception {
        JUser user = new JUser("karsten");
        user.addSavedSearch(new SavedSearch(1, new UserQuery("test")));
        user.addSavedSearch(new SavedSearch(2, new UserQuery("test2")));
        userSearch.save(user, true);
        assertEquals(1, userSearch.search("karsten").size());
        user = userSearch.search("karsten").iterator().next();
        assertEquals(2, user.getSavedSearches().size());
    }

    @Test
    public void testUpdateBatch() throws Exception {
        Set<JUser> list = new LinkedHashSet<JUser>();
        JUser user = new JUser("karsten");
        list.add(user);
        JUser user2 = new JUser("peter");
        list.add(user2);
        userSearch.update(list, 2);
        userSearch.refresh();
        assertEquals(1, userSearch.search("karsten").size());
        assertEquals(1, userSearch.search("peter").size());

        userSearch.delete(user, false);
        userSearch.delete(user2, true);
        assertEquals(0, userSearch.search("karsten").size());
        assertEquals(0, userSearch.search("peter").size());

        userSearch.update(list, 1);
        userSearch.refresh();
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

//    @Test
//    public void testMoreLikeThis() throws Exception {
//        SolrUser karsten = new SolrUser("karsten");
//        karsten.setDescription("hooping hooping nice solr");
//        userSearch.save(karsten, false);
//
//        SolrUser pet = new SolrUser("peter");
//        pet.setDescription("hooping hooping nice solr");
//        userSearch.save(pet, false);
//
//        SolrUser joh = new SolrUser("johannes");
//        karsten.setDescription("windows rocks!");
//        userSearch.save(joh, true);
//
//        Collection<SolrUser> list = new LinkedHashSet<SolrUser>();
//        userSearch.searchMoreLikeThis(list, "peter", 10, 0, true);
//        assertEquals(1, list.size());
//        assertEquals(karsten, list.iterator().next());
//    }
//
//    @Test
//    public void testMltPaging() throws Exception {
//        SolrUser karsten = new SolrUser("karsten");
//        karsten.setDescription("hooping hooping; nice is solr");
//        userSearch.save(karsten, false);
//
//        SolrUser pet = new SolrUser("peter");
//        pet.setDescription("hooping hooping; solr is nice rocks; what do you need?");
//        userSearch.save(pet, false);
//
//        SolrUser joh = new SolrUser("johannes");
//        joh.setDescription("hooping hooping; solr is nice rocks; what do you need?");
//        userSearch.save(joh, true);
//
//        Collection<SolrUser> list = new LinkedHashSet<SolrUser>();
//        long ret = userSearch.searchMoreLikeThis(list, "peter", 2, 0, true);
//        assertEquals(2, ret);
//        assertEquals(2, list.size());
//        list.clear();
//        ret = userSearch.searchMoreLikeThis(list, "peter", 1, 0, true);
//        assertEquals(2, ret);
//        assertEquals(karsten, list.iterator().next());
//
//        list.clear();
//        ret = userSearch.searchMoreLikeThis(list, "peter", 1, 1, true);
//        assertEquals(2, ret);
//        assertEquals(joh, list.iterator().next());
//    }
//
//    @Test
//    public void testIsMlt() {
//        UserQuery query = userSearch.createMltQuery("peter");
//        assertTrue(userSearch.isMlt(query));
//    }

    @Test
    public void testUnderscoreInName() throws Exception {
        JUser karsten = new JUser("karsten");
        karsten.setDescription("hooping hooping nice solr");
        userSearch.save(karsten, false);

        JUser korland = new JUser("g_korland");
        korland.setDescription("hooping hooping solr nice");
        userSearch.save(korland, true);

        Collection<JUser> list = new LinkedHashSet<JUser>();        
        userSearch.search(list, "g_korland", 10, 0);
        assertEquals(1, list.size());
        
        list = new LinkedHashSet<JUser>();
        userSearch.search(list, "hooping", 10, 0);
        assertEquals(2, list.size());       
    }

    @Test
    public void testPaging()  {
        for (int i = 0; i < 5; i++) {
            JUser karsten = new JUser("karsten" + i);
            karsten.setDescription("hooping hooping nice solr");
            userSearch.save(karsten, false);
        }
        userSearch.refresh();

        Collection<JUser> list = new LinkedHashSet<JUser>();
        assertEquals(5, userSearch.search(list, "hooping", 3, 0));
        assertEquals(3, list.size());
        list.clear();
        assertEquals(5, userSearch.search(list, "hooping", 3, 1));
        assertEquals(2, list.size());
    }

    @Test
    public void testUserFind()  {
        JUser karsten = new JUser("karsten");
        karsten.addOwnTweet(new JTweet(1, "hooping hooping", karsten));
        karsten.addOwnTweet(new JTweet(2, "nice solr", karsten));
        userSearch.save(karsten, true);

        Collection<JUser> list = new LinkedHashSet<JUser>();
        assertEquals(1, userSearch.search(list, "karsten", 3, 0));
        assertEquals(1, list.size());

        list = new LinkedHashSet<JUser>();
        assertEquals(0, userSearch.search(list, "kasten", 3, 0));
        assertEquals(0, list.size());
    }
    
    @Test
    public void testFindByScreenname()  {
        JUser karsten = new JUser("karsten");
        karsten.addOwnTweet(new JTweet(1, "hooping hooping", karsten));
        karsten.addOwnTweet(new JTweet(2, "nice solr", karsten));
        userSearch.save(karsten, true);
        
        assertNotNull(userSearch.findByScreenName("karsten"));
        assertNotNull(userSearch.findByScreenName("Karsten"));       
        assertNull(userSearch.findByScreenName("hooping"));
    }

    @Test
    public void testFacetSearch()  {
        userSearch.setTermMinFrequency(0);

        JUser karsten = new JUser("karsten");
        new JTweet(1L, "test test", karsten);
        new JTweet(2L, "help help java", karsten);
        userSearch.save(karsten, false);

        JUser peter = new JUser("peter");
        new JTweet(3L, "test test", peter);
        new JTweet(4L, "bla bli java", peter);
        userSearch.save(peter, true);

        // now createTags: test, java, ...        

        // one can even use UserQuery.set("f.myField.facet.limit",10)
        UserQuery query = new UserQuery("java");
        query.addFilterQuery("tag", "test");

        Collection<JUser> list = new LinkedHashSet<JUser>();
        SearchResponse rsp = userSearch.search(list, query);
        
        // found 2 users which have java as tags
        assertEquals(2, list.size());       
//        TermsStatsFacet.Entry cnt = ((TermsStatsFacet) rsp.getFacets().facet("tag")).entries().get(1);                
//        assertEquals("java", cnt.getTerm());
//        assertEquals(2, cnt.getCount());

        // more filter queries
        list.clear();
        query.addFilterQuery("tag", "help");
        userSearch.search(list, query);
        assertEquals(1, list.size());

        list.clear();
        query.addFilterQuery("tag", "z");
        userSearch.search(list, query);
        assertEquals(0, list.size());
    }

    @Test
    public void testGetQueryTerms() throws Exception {
        JUser user = new JUser("karsten");
        user.addSavedSearch(new SavedSearch(1, new UserQuery("peter test")));
        user.addSavedSearch(new SavedSearch(2, new UserQuery("peter tester")));
        userSearch.save(user, false);
        user = new JUser("peter");
        user.addSavedSearch(new SavedSearch(3, new UserQuery("peter test")));
        user.addSavedSearch(new SavedSearch(4, new UserQuery("karsten tester")));
        user.addSavedSearch(new SavedSearch(5, new UserQuery("karsten OR tester")));
        user.addSavedSearch(new SavedSearch(6, new UserQuery("karsten Tester")));
        user.addSavedSearch(new SavedSearch(7, new UserQuery("a1")));
        user.addSavedSearch(new SavedSearch(8, new UserQuery("a2")));
        user.addSavedSearch(new SavedSearch(9, new UserQuery("a3")));
        user.addSavedSearch(new SavedSearch(10, new UserQuery("a4")));
        user.addSavedSearch(new SavedSearch(11, new UserQuery("a5")));
        user.addSavedSearch(new SavedSearch(12, new UserQuery("a6")));
        user.addSavedSearch(new SavedSearch(13, new UserQuery("a7")));
        userSearch.save(user, true);

        Collection<String> coll = userSearch.getQueryTerms();
        assertEquals(11, coll.size());
        assertTrue(coll.contains("peter test"));
        assertTrue(coll.contains("peter tester"));
        assertTrue(coll.contains("karsten tester"));
        assertTrue(coll.contains("karsten OR tester"));
    }    
    
     @Test
    public void testFriends() throws Exception {
         JUser user = new JUser("peter").setFriends(Arrays.asList("test", "tester"));
         JUser user2 = new JUser("karsten").setFriends(Collections.EMPTY_LIST);
         JUser user3 = new JUser("johannes").setFriends(null);
         userSearch.save(user, false);
         userSearch.save(user2, false);
         userSearch.save(user3, true);
         assertEquals(2, userSearch.findByScreenName("peter").getFriends().size());
         assertEquals(0, userSearch.findByScreenName("karsten").getFriends().size());
         assertEquals(0, userSearch.findByScreenName("johannes").getFriends().size());
     }
}