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
import java.util.Iterator;
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
    public void testAddDoc() throws Exception {
        SolrUser user = new SolrUser("karsten");
        user.addOwnTweet(new SolrTweet(1, "bla bli lbu", user));
        user.addOwnTweet(new SolrTweet(2, "dies ist ein test", user));

        SolrUser user2 = new SolrUser("peter");
        user2.addOwnTweet(new SolrTweet(3, "TDD rocks!", user2));
        userSearch.save(user);
        userSearch.save(user2);

        assertEquals(1, userSearch.search("test").size());
        assertEquals(1, userSearch.search("tdd").size());
        assertEquals("peter", userSearch.search("tdd").iterator().next().getScreenName());
        assertEquals("karsten", userSearch.search("bli").iterator().next().getScreenName());

        assertEquals(1, userSearch.search("rocks\\!").size());
        assertEquals(1, userSearch.search("rocks").size());
        //        assertEquals(1, luc.search("rock").size());
    }

    @Test
    public void testDelete() throws Exception {
        SolrUser user = new SolrUser("karsten");
        user.addOwnTweet(new SolrTweet(1, "bla bli lbu", user));
        user.addOwnTweet(new SolrTweet(2, "dies ist ein test", user));

        userSearch.save(user);
        assertEquals(1, userSearch.search("test").size());

        userSearch.delete(user, true);
        assertEquals(0, userSearch.search("test").size());
    }

    @Test
    public void testUpdate() throws Exception {
        assertEquals(0, userSearch.search("test").size());
        SolrUser user = new SolrUser("karsten");
        user.addOwnTweet(new SolrTweet(1, "bla bli lbu", user));
        user.addOwnTweet(new SolrTweet(2, "dies ist ein test", user));

        userSearch.save(user, true);

        assertEquals(1, userSearch.search("test").size());

        user = new SolrUser("karsten");
        user.addOwnTweet(new SolrTweet(1, "bla bli lbu", user));
        user.addOwnTweet(new SolrTweet(2, "dies ist ein test", user));
        user.addOwnTweet(new SolrTweet(3, "neuer tweet", user));

        userSearch.update(user, true, true);

        assertEquals(1, userSearch.search("test").size());
        assertEquals(3, userSearch.search("test").iterator().next().getOwnTweets().size());

        // update
        user = new SolrUser("karsten");
        new SolrTweet(4, "users without own tweets won't get indexed!", user);
        userSearch.update(user, true, true);

        assertEquals(0, userSearch.search("test").size());
        assertEquals(1, userSearch.search("karsten").size());
        assertEquals(1, userSearch.search("karsten").iterator().next().getOwnTweets().size());
    }

    @Test
    public void testUpdateBatch() throws Exception {
        Set<SolrUser> list = new LinkedHashSet<SolrUser>();
        SolrUser user = new SolrUser("karsten");
        user.addOwnTweet(new SolrTweet(1, "bla bli lbu", user));
        list.add(user);
        SolrUser user2 = new SolrUser("karsten2");
        user2.addOwnTweet(new SolrTweet(1, "this is a test", user2));
        list.add(user2);
        userSearch.update(list, 2);
        userSearch.commit();
        assertEquals(1, userSearch.search("test").size());
        assertEquals(1, userSearch.search("lbu").size());

        userSearch.delete(user, false);
        userSearch.delete(user2, true);
        assertEquals(0, userSearch.search("test").size());
        assertEquals(0, userSearch.search("lbu").size());

        userSearch.update(list, 1);
        userSearch.commit();
        assertEquals(1, userSearch.search("test").size());
        assertEquals(1, userSearch.search("lbu").size());

        userSearch.delete(user, false);
        userSearch.delete(user2, true);

        userSearch.update(list, 3);
        userSearch.commit();
        assertEquals(1, userSearch.search("test").size());
        assertEquals(1, userSearch.search("lbu").size());

        userSearch.delete(user, false);
        userSearch.delete(user2, true);

        SolrUser user3 = new SolrUser("karsten3");
        list.add(user3);
        userSearch.update(list, 1);
        userSearch.commit();
        assertEquals(1, userSearch.search("test").size());
    }

    @Test
    public void testCorrectTweetIdToContentConnection() throws Exception {
        SolrUser user = new SolrUser("karsten");
        user.addOwnTweet(new SolrTweet(1, "TDD is shit", user));
        user.addOwnTweet(new SolrTweet(2, "TDD is bli", user));

        userSearch.save(user);
        Collection<SolrUser> list = new LinkedHashSet<SolrUser>();
        userSearch.search(list, new UserQuery("tdd"));
        assertEquals(1, list.size());

        Iterator<SolrTweet> iter = list.iterator().next().getOwnTweets().iterator();
        SolrTweet tw = iter.next();
        assertEquals(1, (long) tw.getTwitterId());
        assertEquals("TDD is shit", tw.getText());
        tw = iter.next();
        assertEquals(2, (long) tw.getTwitterId());
        assertEquals("TDD is bli", tw.getText());
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
        karsten.addOwnTweet(new SolrTweet(2, "hooping hooping", karsten));
        karsten.addOwnTweet(new SolrTweet(3, "nice solr", karsten));
        userSearch.save(karsten);

        SolrUser pet = new SolrUser("peter");
        pet.addOwnTweet(new SolrTweet(4, "hooping hooping", pet));
        pet.addOwnTweet(new SolrTweet(5, "solr nice", pet));
        userSearch.save(pet);

        SolrUser joh = new SolrUser("johannes");
        joh.addOwnTweet(new SolrTweet(6, "windows rocks!", joh));
        userSearch.save(joh);
        userSearch.commit();

        Collection<SolrUser> list = new LinkedHashSet<SolrUser>();
        userSearch.searchMoreLikeThis(list, "peter", 10, 0, true);
        assertEquals(1, list.size());
        assertEquals(karsten, list.iterator().next());
    }

    @Test
    public void testMltPaging() throws Exception {
        SolrUser karsten = new SolrUser("karsten");
        karsten.addOwnTweet(new SolrTweet(2, "hooping hooping", karsten));
        karsten.addOwnTweet(new SolrTweet(3, "nice is solr", karsten));
        userSearch.save(karsten);

        SolrUser pet = new SolrUser("peter");
        pet.addOwnTweet(new SolrTweet(4, "hooping hooping", pet));
        pet.addOwnTweet(new SolrTweet(5, "solr is nice rocks", pet));
        pet.addOwnTweet(new SolrTweet(5, "what do you need?", pet));
        userSearch.save(pet);

        SolrUser joh = new SolrUser("johannes");
        joh.addOwnTweet(new SolrTweet(6, "hooping hooping", joh));
        joh.addOwnTweet(new SolrTweet(7, "solr is nice rocks", joh));
        joh.addOwnTweet(new SolrTweet(5, "what do you need?", joh));
        userSearch.save(joh);
        userSearch.commit();

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
        karsten.addOwnTweet(new SolrTweet(2, "hooping hooping", karsten));
        karsten.addOwnTweet(new SolrTweet(3, "nice solr", karsten));
        userSearch.save(karsten);

        SolrUser korland = new SolrUser("g_korland");
        korland.addOwnTweet(new SolrTweet(4, "hooping hooping", korland));
        korland.addOwnTweet(new SolrTweet(5, "solr nice", korland));
        userSearch.save(korland);
        userSearch.commit();

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
            karsten.addOwnTweet(new SolrTweet(i * 2, "hooping hooping", karsten));
            karsten.addOwnTweet(new SolrTweet(i * 2 + 1, "nice solr", karsten));
            userSearch.save(karsten);
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
        userSearch.save(karsten);
        userSearch.commit();

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
        userSearch.save(karsten);

        SolrUser peter = new SolrUser("peter");
        new SolrTweet(3L, "test test", peter);
        new SolrTweet(4L, "bla bli java", peter);
        userSearch.save(peter);

        // now createTags: test, java, ...
        userSearch.commit();

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

    // fixed only if https://issues.apache.org/jira/browse/SOLR-1624
    // applied
    @Test
    public void testHighlighting() throws SolrServerException {
        SolrUser karsten = new SolrUser("karsten");
        new SolrTweet(2, "test test", karsten);
        new SolrTweet(1, "help help java", karsten);
        userSearch.save(karsten);

        SolrUser peter = new SolrUser("peter");
        new SolrTweet(4, "test pest Java", peter);
        new SolrTweet(3, "bla bli java", peter);
        new SolrTweet(2, "ignore this old (smallest id) snippet Java", peter);
        userSearch.save(peter);
        SolrUser javaUser = new SolrUser("java");
        new SolrTweet(12, "nothing said", javaUser);
        new SolrTweet(11, "help help you if you can", javaUser);
        userSearch.save(javaUser);

        SolrUser emptyMatch = new SolrUser("emptyMatch");
        new SolrTweet(10, "help help me me me me me me help help me me me me me me "
                + "help help me me me me me me help help me me me me me me java", emptyMatch);

        new SolrTweet(7, "help help2 java", emptyMatch);
        userSearch.save(emptyMatch);

        userSearch.commit();

        SolrQuery query = new UserQuery("java");
        userSearch.attachHighlighting(query, 2);
        // make that emptyMatch has no highlighted matches
        query.set("hl.maxAnalyzedChars", 100);

        Set<SolrUser> set = new LinkedHashSet<SolrUser>();
        userSearch.search(set, query);
        Iterator<SolrUser> iter = set.iterator();

        // the user matches => highest relevance + no highlightings!
        javaUser = iter.next();
        Iterator<SolrTweet> twIter = javaUser.getOwnTweets().iterator();
        // ... but alternativly use the normal tweets field:
        assertEquals("nothing said", twIter.next().getText());
        assertEquals("help help you if you can", twIter.next().getText());
        assertFalse(twIter.hasNext());

        peter = iter.next();
        twIter = peter.getOwnTweets().iterator();
        // latest == greatest comes first
        assertEquals("test pest <b>Java</b>", twIter.next().getText());
        assertEquals("bla bli <b>java</b>", twIter.next().getText());
        // ignore the third snippet (hl.snippets=2)
        assertFalse(twIter.hasNext());

        karsten = iter.next();
        twIter = karsten.getOwnTweets().iterator();
        assertEquals("help help <b>java</b>", twIter.next().getText());

        emptyMatch = iter.next();
        twIter = emptyMatch.getOwnTweets().iterator();
        // TODO only the first tweet is skipped because the maxAnalyzedChars
        // applies for EACH tweet field separate
        assertEquals("help help2 <b>java</b>", twIter.next().getText());
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
}
