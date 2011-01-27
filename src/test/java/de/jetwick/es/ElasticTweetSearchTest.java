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
package de.jetwick.es;

import java.util.LinkedHashSet;
import java.util.Set;
import java.io.StringReader;
import de.jetwick.data.UrlEntry;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
import de.jetwick.solr.TweetQuery;
import de.jetwick.util.MyDate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.WhitespaceTokenizer;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class ElasticTweetSearchTest extends AbstractElasticSearchTester {

//    private Logger logger = LoggerFactory.getLogger(getClass());
    private static ElasticTweetSearch twSearch;

    public ElasticTweetSearch getTweetSearch() {
        return twSearch;
    }

    @Before
    public void setUp() throws Exception {
        twSearch = new ElasticTweetSearch(getClient());
        super.setUp(twSearch);
    }

    @Test
    public void testSearch() throws Exception {
        SolrUser fromUser = new SolrUser("peter");
        SolrTweet tw1 = new SolrTweet(1L, "this is a test!", fromUser);

        SolrUser otherUser = new SolrUser("otherUser");
        SolrTweet tw2 = new SolrTweet(2L, "Java is cool and stable!", otherUser);
        twSearch.update(tw1, false);
        twSearch.update(tw2, true);

        assertEquals(1, twSearch.search("java").size());
        assertEquals(1, twSearch.search("test").size());
//        assertEquals(1, twSearch.search("java stable").size());                
    }

    @Test
    public void testHashtags() throws Exception {
        // # is handled as digit so that we can search for java to get java and #java results (the same applies to @)
        twSearch.update(createTweet(1L, "is cool and stable! #java", "peter2"));
        assertEquals(1, twSearch.search("java").size());
        assertEquals(1, twSearch.search("#java").size());

        twSearch.deleteAll();

        assertEquals(0, twSearch.search("java").size());
        assertEquals(0, twSearch.search("#java").size());
        twSearch.update(createTweet(1L, "is cool and stable! java", "peter2"));
        assertEquals(1, twSearch.search("java").size());
        assertEquals(0, twSearch.search("#java").size());
    }

    @Test
    public void testSearchAnchors() throws Exception {
        SolrUser peter = new SolrUser("peter");
        SolrTweet tw1 = new SolrTweet(1L, "peter #java is cool!", peter);
        SolrUser peter2 = new SolrUser("peter2");
        SolrTweet tw2 = new SolrTweet(2L, "@peter java is cool!", peter2);
        twSearch.update(tw1, false);
        twSearch.update(tw2, true);

        assertEquals(1, twSearch.search("#java").size());
        assertEquals(1, twSearch.search("@peter").size());
    }

    @Test
    public void testCamelCase() throws Exception {
        SolrTweet tw1 = new SolrTweet(1L, "peter iBood is cool!", new SolrUser("peter1"));
        SolrTweet tw2 = new SolrTweet(2L, "ibood is cool!", new SolrUser("peter2"));
        SolrTweet tw3 = new SolrTweet(3L, "peter iBOOD is cool!", new SolrUser("peter3"));
        SolrTweet tw4 = new SolrTweet(4L, "Ibood is cool!", new SolrUser("peter4"));
        SolrTweet tw5 = new SolrTweet(5L, "iBOOD.com", new SolrUser("peter5"));

        twSearch.update(tw1, false);
        twSearch.update(tw2, false);
        twSearch.update(tw3, false);
        twSearch.update(tw5, false);
        twSearch.update(tw4, true);

        assertEquals(5, twSearch.search("ibood").size());//1,2,3,4,5
        assertEquals(5, twSearch.search("iBood").size());//1,2,3,4,5
        assertEquals(0, twSearch.search("bood").size()); //-> ok
        assertEquals(1, twSearch.search("iBood.com").size()); //1  -> ok        
        assertEquals(1, twSearch.search("ibood.com").size()); //5 -> ok
        assertEquals(1, twSearch.search("ibood.com*").size()); //missing 5
    }

    @Test
    public void testSearchJavaScript() throws Exception {
        // keepwords.txt
        SolrUser peter = new SolrUser("peter1");
        SolrTweet tw1 = new SolrTweet(1L, "peter JavaScript is cool!", peter);
        SolrUser peter2 = new SolrUser("peter2");
        SolrTweet tw2 = new SolrTweet(2L, "java is cool!", peter2);
        SolrTweet tw3 = new SolrTweet(3L, "peter javascript is cool!", new SolrUser("peter3"));
        twSearch.update(tw1, false);
        twSearch.update(tw2, false);
        twSearch.update(tw3, true);

        assertEquals(1, twSearch.search("java").size());
        assertEquals("peter2", twSearch.search("java").iterator().next().getScreenName());
        assertEquals(2, twSearch.search("javascript").size());
        Iterator<SolrUser> iter = twSearch.search("javascript").iterator();
        assertEquals("peter1", iter.next().getScreenName());
        assertEquals("peter3", iter.next().getScreenName());
    }

    @Test
    public void testSorting() throws SolrServerException {
        MyDate day = new MyDate();
        MyDate day2 = day.clone().plusDays(1);
        twSearch.update(createSolrTweet(day, "java is a test!", "peter"), false);
        twSearch.update(createSolrTweet(day2, "java is cool and stable!", "peter2"), true);
        SolrQuery q = new TweetQuery("java").setSort("dt desc");
        List<SolrUser> res = new ArrayList<SolrUser>();
        twSearch.search(res, q);
        assertEquals(2, res.size());
        assertEquals(day2.getTime(), (long) res.get(0).getOwnTweets().iterator().next().getTwitterId());

        q = new TweetQuery("java").setSort("dt asc");
        res.clear();
        twSearch.search(res, q);
        assertEquals(day.getTime(), (long) res.get(0).getOwnTweets().iterator().next().getTwitterId());
    }

    @Test
    public void testLoc() throws SolrServerException {
        SolrUser user = new SolrUser("peter");
        user.setLocation("TEST");
        SolrTweet tw;
        tw = new SolrTweet(1L, "test tweet text", user);
        twSearch.update(tw, false);
        tw = new SolrTweet(2L, "test tweet text2", user);
        twSearch.update(tw, true);
        List<SolrUser> res = new ArrayList<SolrUser>();
        twSearch.search(res, new SolrQuery().setFilterQueries("loc:TEST"));
        assertEquals(1, res.size());
        assertEquals(2, res.get(0).getOwnTweets().size());

        user = new SolrUser("peter");
        tw = new SolrTweet(3L, "test tweet text", user);
        tw.setLocation("TEST3");
        twSearch.update(tw, false);

        tw = new SolrTweet(4L, "test tweet text", user);
        tw.setLocation("TEST4");
        twSearch.update(tw, true);
        res = new ArrayList<SolrUser>();
        twSearch.search(res, new SolrQuery().setFilterQueries("loc:TEST3"));
        assertEquals(1, res.size());
        assertEquals(1, res.get(0).getOwnTweets().size());
    }

    @Test
    public void testDelete() throws Exception {
        // do not throw exception
        twSearch.delete(Collections.EMPTY_LIST);

        SolrUser otherUser = new SolrUser("otherUser");
        SolrTweet tw2 = new SolrTweet(2L, "java is cool and stable!", otherUser);
        twSearch.update(tw2, false);
        twSearch.refresh();
        assertEquals(1, twSearch.search("java").size());

        twSearch.delete(Arrays.asList(tw2));
        twSearch.refresh();
        assertEquals(0, twSearch.search("java").size());
    }

    @Test
    public void testGetReplies() {
        SolrUser usera = new SolrUser("usera");
        SolrTweet tw = new SolrTweet(1L, "this is a Test ", usera);
        SolrUser userb = new SolrUser("userb");
        SolrTweet tw2 = new SolrTweet(2L, "this is a Test ", userb);
        tw2.addReply(tw);
        twSearch.update(tw, true);
        twSearch.update(tw2, true);

        assertEquals(0, twSearch.searchReplies(1L, true).size());
        assertEquals(0, twSearch.searchReplies(2L, true).size());
        assertEquals(0, twSearch.searchReplies(1L, false).size());
        assertEquals(1, twSearch.searchReplies(2L, false).size());
        tw = twSearch.searchReplies(2L, false).iterator().next();
        assertEquals(1L, (long) tw.getTwitterId());
    }

    @Test
    public void testGetRetweets() {
        SolrUser usera = new SolrUser("usera");
        SolrTweet tw = new SolrTweet(1L, "this is a Test ", usera);
        SolrUser userb = new SolrUser("userb");
        SolrTweet tw2 = new SolrTweet(2L, "rt @usera: this is a Test ", userb);
        tw.addReply(tw2);
        twSearch.update(tw, false);
        twSearch.update(tw2, true);

        assertEquals(1, twSearch.searchReplies(1L, true).size());
        assertEquals(0, twSearch.searchReplies(2L, true).size());
        assertEquals(0, twSearch.searchReplies(1L, false).size());
        assertEquals(0, twSearch.searchReplies(2L, false).size());
        assertEquals(2L, (long) twSearch.searchReplies(1L, true).iterator().next().getTwitterId());
    }

    @Test
    public void testFindDuplicates() throws SolrServerException {
        twSearch.update(new SolrTweet(1L, "wikileaks is not a wtf", new SolrUser("userA")), false);
        twSearch.update(new SolrTweet(2L, "news about wikileaks", new SolrUser("userB")), false);

        // find dup is restricted to the last hour so use a current date
        MyDate dt = new MyDate();
        SolrTweet tw3 = new SolrTweet(3L, "wtf means wikileaks task force", new SolrUser("userC")).setCreatedAt(dt.toDate());
        SolrTweet tw4 = new SolrTweet(4L, "wtf wikileaks task force", new SolrUser("userD")).setCreatedAt(dt.plusMinutes(1).toDate());
        SolrTweet tw5 = new SolrTweet(5L, "RT @userC: wtf means wikileaks task force", new SolrUser("userE")).setCreatedAt(dt.plusMinutes(1).toDate());
        twSearch.update(Arrays.asList(tw3, tw4, tw5), new Date(0));
        assertEquals("should be empty. should NOT find tweet 4 because it is younger", 0, tw3.getDuplicates().size());
        assertEquals("should find tweet 3", 1, tw4.getDuplicates().size());

        Map<Long, SolrTweet> map = new LinkedHashMap<Long, SolrTweet>();
        SolrTweet tw = new SolrTweet(10L, "wtf wikileaks task force", new SolrUser("peter")).setCreatedAt(dt.plusMinutes(1).toDate());
        map.put(10L, tw);
        twSearch.findDuplicates(map);
        assertEquals("should find tweets 3 and 4", 2, tw.getDuplicates().size());
    }

    @Test
    public void testSpamDuplicates() throws SolrServerException {
        MyDate dt = new MyDate();
        SolrTweet tw1 = new SolrTweet(1L, "2488334. Increase your twitter followers now! Buy Twitter Followers", new SolrUser("userA")).setCreatedAt(dt.plusMinutes(1).toDate());
        SolrTweet tw2 = new SolrTweet(2L, "349366. Increase your twitter followers now! Buy Twitter Followers", new SolrUser("userB")).setCreatedAt(dt.plusMinutes(1).toDate());
        SolrTweet tw3 = new SolrTweet(31L, "2040312. Increase your twitter followers now! Buy Twitter Followers", new SolrUser("userC")).setCreatedAt(dt.plusMinutes(1).toDate());
        twSearch.update(Arrays.asList(tw1, tw2, tw3), new Date(0));

        assertEquals(0, tw1.getDuplicates().size());
        assertEquals(1, tw2.getDuplicates().size());
        assertEquals(2, tw3.getDuplicates().size());
    }

    @Test
    public void testBatchUpdate() {
        List<SolrTweet> list = new ArrayList<SolrTweet>();

        list.add(createTweet(1L, "text", "usera"));
        list.add(createTweet(2L, "RT @usera: text", "userb"));

        list.add(createTweet(3L, "text2", "usera"));
        list.add(createTweet(4L, "hey I read your text", "userb").setInReplyTwitterId(3L));

        Collection<SolrTweet> res = twSearch.update(list, new Date(0));
        assertEquals(4, res.size());

        assertEquals(1, twSearch.findByTwitterId(1L).getReplyCount());
        assertEquals(1, twSearch.findByTwitterId(1L).getRetweetCount());

        assertEquals(0, twSearch.findByTwitterId(2L).getReplyCount());
        assertEquals(0, twSearch.findByTwitterId(2L).getRetweetCount());

        assertEquals(1, twSearch.findByTwitterId(3L).getReplyCount());
        assertEquals(0, twSearch.findByTwitterId(3L).getRetweetCount());

        assertEquals(0, twSearch.findByTwitterId(4L).getReplyCount());
        assertEquals(0, twSearch.findByTwitterId(4L).getRetweetCount());
    }

    @Test
    public void testConnectTwitterId() throws Exception {
        // A has replies B and C
        // C has replies D

        // store A and D
        twSearch.privateUpdate(Arrays.asList(createTweet(1L, "A", "u1"),
                createTweet(4L, "D", "u4").setInReplyTwitterId(3L)));

        twSearch.update(createTweet(3L, "C", "u3").setInReplyTwitterId(1L));

        // now check if C was properly connected with A and D
        SolrTweet twC = twSearch.findByTwitterId(3L);
        assertEquals(1, twC.getReplyCount());

        // A should have C as reply
        SolrTweet twA = twSearch.findByTwitterId(1L);
        assertEquals(1, twA.getReplyCount());

        // now check if B was properly connected with A
        twSearch.update(createTweet(2L, "B", "u2").setInReplyTwitterId(1L));

        twA = twSearch.findByTwitterId(1L);
        assertEquals(2, twA.getReplyCount());
    }

    @Test
    public void testAttach() throws Exception {
        twSearch.update(createTweet(1, "test", "peter"));
        twSearch.update(createTweet(2, "test2", "peter"));

        assertEquals(2, twSearch.findByUserName("peter").getOwnTweets().size());
    }

    @Test
    public void testDoNotSaveSecondUser() {
        SolrTweet fTweet = createTweet(5, "@peter @karsten bla bli", "peter");
        twSearch.update(fTweet);

        assertNull(twSearch.findByUserName("karsten"));
        assertNotNull(twSearch.findByUserName("peter"));
    }

    @Test
    public void testDoSaveDuplicate() {
        twSearch.update(createTweet(4, "@peter bla bli", "peter"));
        twSearch.update(createTweet(5, "@peter bla bli", "karsten"));

        assertNotNull(twSearch.findByUserName("karsten"));
        assertNotNull(twSearch.findByUserName("peter"));
    }

    @Test
    public void testIdVsName() {
        SolrTweet fTweet = createTweet(5, "@karsten bla bli", "peter");
        twSearch.update(fTweet);

        fTweet = createTweet(6, "@peter bla bli", "karsten");
        twSearch.update(fTweet);
        assertNotNull(twSearch.findByUserName("karsten"));
    }

    @Test
    public void testNoDuplicateUser2() {
        SolrTweet fTweet = createTweet(1, "@karsten bla bli", "peter");
        twSearch.update(fTweet);

        fTweet = createTweet(2, "@Karsten bla bli", "Peter");
        twSearch.update(fTweet);
    }

    @Test
    public void testNoDuplicateTweet() {
        SolrTweet fTweet = createTweet(123, "@karsten bla bli", "peter");
        twSearch.update(fTweet);
        twSearch.update(fTweet);

        assertEquals(1, twSearch.countAll());
        assertEquals(1, twSearch.findByUserName("peter").getOwnTweets().size());
    }

    @Test
    public void testUpdateTweetsWhichIsInfluencedFromActivationDepth() throws Exception {
        SolrTweet tw1 = createTweet(1L, "tweet1", "peter");
        SolrTweet tw2 = createTweet(2L, "tweet2", "peter");

        twSearch.update(tw1);
        twSearch.update(tw2);

        assertEquals(2, twSearch.findByUserName("peter").getOwnTweets().size());

        tw1 = createTweet(1L, "tweet1", "peter");
        twSearch.update(tw1);

        assertEquals(2, twSearch.findByUserName("peter").getOwnTweets().size());
    }

    @Test
    public void testUpdateAndRemove() throws Exception {
        SolrTweet tw1 = createTweet(1L, "@karsten hajo", "peter");
        tw1.setCreatedAt(new MyDate().minusDays(2).toDate());

        twSearch.update(tw1);
        assertEquals(1, twSearch.countAll());
        assertEquals("@karsten hajo", twSearch.search("hajo").iterator().next().getOwnTweets().iterator().next().getText());
        assertEquals(1, twSearch.findByUserName("peter").getOwnTweets().size());

        SolrTweet tw = createTweet(2L, "test", "peter");
        tw.setCreatedAt(new Date());
        Collection<SolrTweet> res = twSearch.update(Arrays.asList(tw),
                new MyDate().minusDays(1).toDate());
        assertEquals(1, res.size());
        assertEquals(1, twSearch.countAll());
        assertEquals(1, twSearch.search("test").size());
        assertEquals(0, twSearch.search("hajo").size());
        assertEquals(1, twSearch.findByUserName("peter").getOwnTweets().size());
    }

    @Test
    public void testDoubleUpdateShouldIncreaseReplies() throws Exception {
        twSearch.privateUpdate(Arrays.asList(createTweet(1L, "bla bli blu", "userA"),
                createTweet(2L, "RT @userA: bla bli blu", "userC")));

        assertEquals(1, twSearch.findByTwitterId(1L).getReplyCount());
        assertEquals(1, twSearch.findByTwitterId(1L).getRetweetCount());

        twSearch.privateUpdate(Arrays.asList(
                createTweet(3L, "RT @userA: bla bli blu", "userC"),
                createTweet(4L, "RT @userA: bla bli blu", "userD")));

        assertEquals(2, twSearch.findByTwitterId(1L).getReplyCount());
        assertEquals(2, twSearch.findByTwitterId(1L).getRetweetCount());

        assertEquals(0, twSearch.findByTwitterId(2L).getReplyCount());
        assertEquals(0, twSearch.findByTwitterId(3L).getReplyCount());
        assertEquals(0, twSearch.findByTwitterId(4L).getReplyCount());

        twSearch.privateUpdate(Arrays.asList(
                createTweet(5L, "RT @userA: bla bli blu", "userE")));

        assertEquals(3, twSearch.findByTwitterId(1L).getReplyCount());
        assertEquals(3, twSearch.findByTwitterId(1L).getRetweetCount());
    }

    @Test
    public void testConnectTweets() throws Exception {
        // A has reply B        
        twSearch.privateUpdate(Arrays.asList(createTweet(1L, "bla bli blu", "userA"),
                createTweet(2L, "RT @userA: bla bli blu", "userC")));
        assertEquals(1, twSearch.findByTwitterId(1L).getReplyCount());

        twSearch.update(createTweet(3L, "@userXY see this nice fact: RT @userA: bla bli blu", "userB"));

        assertEquals(2, twSearch.findByTwitterId(1L).getReplyCount());
    }

    @Test
    public void testProcessToUser() throws Exception {
        twSearch.update(createTweet(1L, "@userA bla bli blu", "userB"));
        twSearch.update(createTweet(2L, "RT @userB: @userA bla bli blu", "userA"));
        assertEquals(2, twSearch.countAll());
        assertEquals(1, twSearch.findByTwitterId(1L).getReplyCount());
        assertEquals(1, twSearch.findByTwitterId(1L).getRetweetCount());
        assertEquals(0, twSearch.findByTwitterId(2L).getReplyCount());
        assertEquals(0, twSearch.findByTwitterId(2L).getRetweetCount());
    }

    @Test
    public void testDoNotAllowSelfRetweets() throws Exception {
        twSearch.update(createTweet(1L, "bla bli blu", "userA"));
        twSearch.update(createTweet(2L, "RT @userA: bla bli blu", "userA"));

        assertEquals(0, twSearch.findByTwitterId(1L).getReplyCount());
    }

    @Test
    public void testDoNotAddDuplicateRetweets() throws Exception {
        twSearch.update(createTweet(1L, "bla bli blu", "userA"));
        assertEquals(0, twSearch.findByTwitterId(1L).getReplyCount());

        twSearch.update(createTweet(2L, "RT @userA: bla bli blu", "userB"));
        assertEquals(1, twSearch.findByTwitterId(1L).getRetweetCount());

        twSearch.update(createTweet(3L, "RT @userA: bla bli blu", "userB"));
        assertEquals(1, twSearch.findByTwitterId(1L).getRetweetCount());
    }

    @Test
    public void testDoNotAddOldTweets() {
        SolrTweet tw = createTweet(2L, "RT @userA: bla bli blu", "userB");
        tw.setCreatedAt(new MyDate().minusDays(2).toDate());
        assertEquals(0, twSearch.update(Arrays.asList(tw),
                new MyDate().minusDays(1).toDate()).size());
    }

    @Test
    public void testAddOldTweetsIfPersistent() throws SolrServerException {
        SolrTweet tw = createTweet(2L, "RT @userA: bla bli blu", "userB");
        Date dt = new MyDate().minusDays(2).toDate();
        tw.setUpdatedAt(dt);
        tw.setCreatedAt(dt);
        assertEquals(1, twSearch.update(tw).size());

        // testOverwriteTweetsIfPersistent
        tw = createTweet(2L, "totally new", "userB");
        dt = new MyDate().minusDays(2).toDate();
        tw.setUpdatedAt(dt);
        tw.setCreatedAt(dt);
        assertEquals(1, twSearch.update(tw).size());
        assertEquals(0, twSearch.search("bla").size());
        assertEquals(1, twSearch.search("new").size());
    }

    @Test
    public void testDontRemoveOldIfPersistent() throws Exception {
        SolrTweet tw2 = createTweet(2L, "RT @userA: bla bli blu", "userB");
        Date dt = new MyDate().minusDays(2).toDate();
        tw2.setUpdatedAt(dt);
        tw2.setCreatedAt(dt);
        assertEquals(1, twSearch.update(tw2).size());
        assertNotNull(twSearch.findByTwitterId(2L).getUpdatedAt());

        SolrTweet tw3 = createTweet(3L, "another tweet grabbed from search", "userB");
        tw3.setCreatedAt(new Date());
        Collection<SolrTweet> res = twSearch.update(Arrays.asList(tw3), new MyDate().minusDays(1).toDate());
        assertEquals(1, res.size());
    }

    @Test
    public void testComplexUpdate() throws Exception {
        SolrTweet tw1 = createTweet(1L, "bla bli blu", "userA");
        tw1.setCreatedAt(new MyDate().minusDays(2).toDate());

        SolrTweet tw2 = createTweet(2L, "rt @usera: bla bli blu", "userB");
        tw2.setCreatedAt(new MyDate().minusDays(2).plusMinutes(1).toDate());

        SolrTweet tw3 = createTweet(3L, "rt @usera: bla bli blu", "userC");
        tw3.setCreatedAt(new MyDate().minusDays(2).plusMinutes(1).toDate());

        SolrTweet tw4 = createTweet(4L, "rt @usera: bla bli blu", "userD");
        tw4.setCreatedAt(new MyDate().minusDays(2).plusMinutes(1).toDate());

        Collection<SolrTweet> res = twSearch.privateUpdate(Arrays.asList(tw1, tw2, tw3, tw4));
        assertEquals(1, twSearch.findByUserName("usera").getOwnTweets().size());
        assertEquals(3, twSearch.findByTwitterId(1L).getReplyCount());
        assertEquals(4, res.size());

        // we do not sort the tweets anylonger so that 104 could be also a retweet of:
//        SolrTweet tw100 = createTweet(100L, "newtext", "usera");
        SolrTweet tw101 = createTweet(101L, "newtext two", "usera");
        tw101.setCreatedAt(new Date());
        SolrTweet tw102 = createTweet(102L, "newbla one", "userd");
        tw102.setCreatedAt(new Date());
        SolrTweet tw103 = createTweet(103L, "newbla two", "userd");
        tw103.setCreatedAt(new Date());
        SolrTweet tw104 = createTweet(104L, "rt @usera: newtext two", "userc");
        tw104.setCreatedAt(new MyDate(tw101.getCreatedAt()).plusMinutes(1).toDate());

        res = twSearch.update(Arrays.asList(tw101, tw102, tw103, tw104), new MyDate().minusDays(1).toDate());
        assertEquals(4, twSearch.countAll());
        assertEquals(1, twSearch.findByTwitterId(101L).getRetweetCount());
        assertEquals(1, twSearch.findByTwitterId(101L).getReplyCount());
        assertEquals(4, res.size());
        assertEquals(4, twSearch.countAll());

        // no tweet exists with that string
        assertEquals(0, twSearch.search("bla bli blu").size());
    }

    @Test
    public void testDoNotThrowQueryParserException() {
        SolrTweet tw = createTweet(1L, "rt @jenny2s: -- Earth, Wind & Fire - September  (From \"Live In Japan\")"
                + " http://www.youtube.com/watch?v=hy-huQAMPQA via @youtube --- HAPPY SEPTEMBER !!", "usera");
        twSearch.update(tw);
    }

    @Test
    public void testUpdateList() {
        assertEquals(1, twSearch.privateUpdate(Arrays.asList(createTweet(1L, "test", "peter"),
                createTweet(1L, "test", "peter"))).size());
        assertNotNull(twSearch.findByTwitterId(1L));
    }

    @Test
    public void testQueryChoices() throws SolrServerException {
        twSearch.privateUpdate(Arrays.asList(createTweet(1L, "obama obama", "usera"),
                createTweet(2L, "bluest obama obama", "usera"),
                createTweet(3L, "bluest bluest obama", "usera"),
                createTweet(4L, "obama bluest again and again", "usera")));

        assertEquals(3L, twSearch.search(new SolrQuery().addFilterQuery("tag:bluest")).getHits().getTotalHits());

        Collection<String> coll = twSearch.getQueryChoices(null, "obama");
        assertEquals(0, coll.size());

        coll = twSearch.getQueryChoices(null, "obama ");
        assertEquals(1, coll.size());
        assertTrue(coll.contains("obama bluest"));
    }

    @Test
    public void testQueryChoicesWithoutDateRestrictions() throws SolrServerException {
        twSearch.privateUpdate(Arrays.asList(createTweet(new MyDate().minusDays(1).minusMinutes(3), "obama obama", "usera"),
                createTweet(new MyDate().minusDays(1).minusMinutes(2), "bluest obama obama", "usera"),
                createTweet(new MyDate().minusDays(1).minusMinutes(1), "bluest bluest obama", "usera"),
                createTweet(new MyDate().minusDays(1), "obama bluest again and again", "usera")));

        assertEquals(3L, twSearch.search(new SolrQuery().addFilterQuery("tag:bluest")).getHits().getTotalHits());

        // TODO ES
//        Collection<String> coll = twSearch.getQueryChoices(new TweetQuery("").addFilterQuery(ElasticTweetSearch.FILTER_ENTRY_LATEST_DT), "obama ");
//        assertEquals(1, coll.size());
//        assertTrue(coll.contains("obama bluest"));
    }

    @Test
    public void testFindOrigin() {
        twSearch.privateUpdate(Arrays.asList(createTweet(1L, "text", "usera"),
                createTweet(2L, "RT @usera: text", "userb"),
                createTweet(3L, "RT @usera: text", "userc"),
                createTweet(4L, "new text", "userd")));

        SolrQuery q = twSearch.createFindOriginQuery(null, "text", 1);
        assertEquals(2, q.getFilterQueries().length);
        assertEquals(ElasticTweetSearch.RT_COUNT + ":[1 TO *]", q.getFilterQueries()[1]);

        // too high minResults
        int minResults = 3;
        q = twSearch.createFindOriginQuery(null, "text", minResults);
        assertEquals(1, q.getFilterQueries().length);

        // no retweets for 'new text'
        q = twSearch.createFindOriginQuery(null, "new text", 2);
        assertEquals(1, q.getFilterQueries().length);
    }

    @Test
    public void testFacets() throws SolrServerException {
        twSearch.privateUpdate(Arrays.asList(createTweet(1L, "Beitrag atom. atom again", "userA"),
                createTweet(2L, "atom gruene", "userA"),
                createTweet(3L, "third tweet", "userA")));

        SearchResponse rsp = twSearch.search(new TweetQuery());
        assertEquals(3, rsp.hits().getTotalHits());
        // only the second tweet will contain a tag with atom!
        assertEquals(1, ((TermsFacet) rsp.facets().facet("tag")).getEntries().size());

        rsp = twSearch.search(new SolrQuery().addFilterQuery("tag:atom"));
        assertEquals(2, twSearch.collectTweets(rsp).size());
    }

    @Test
    public void testReadUrlEntries() throws IOException {
        SolrTweet tw = new SolrTweet(1L, "text", new SolrUser("peter"));
        List<UrlEntry> entries = new ArrayList<UrlEntry>();

        UrlEntry urlEntry = new UrlEntry(0, 20, "http://test.de/bla");
        urlEntry.setResolvedDomain("");
        urlEntry.setResolvedTitle("");
        entries.add(urlEntry);

        urlEntry = new UrlEntry(5, 17, "");
        urlEntry.setResolvedDomain("");
        urlEntry.setResolvedTitle("");
        entries.add(urlEntry);

        urlEntry = new UrlEntry(2, 18, "http://fulltest.de/bla");
        urlEntry.setResolvedDomain("resolved-domain.de");
        urlEntry.setResolvedTitle("ResolvedTitel");
        entries.add(urlEntry);

        tw.setUrlEntries(entries);
        //TODO ES
//        SolrInputDocument iDoc = twSearch.createDoc(tw);
//        SolrDocument doc = new SolrDocument();
//        for (Entry<String, SolrInputField> entry : iDoc.entrySet()) {
//            doc.put(entry.getKey(), entry.getValue().getValue());
//        }
//
//        SolrTweet tw2 = twSearch.readDoc(doc);
//        assertEquals(1, tw2.getUrlEntries().size());
//        Iterator<UrlEntry> iter = tw2.getUrlEntries().iterator();
//        urlEntry = iter.next();
//        assertEquals("http://fulltest.de/bla", urlEntry.getResolvedUrl());
//        assertEquals("resolved-domain.de", urlEntry.getResolvedDomain());
//        assertEquals("ResolvedTitel", urlEntry.getResolvedTitle());
//        assertEquals(2, urlEntry.getIndex());
//        assertEquals(18, urlEntry.getLastIndex());
    }

    @Test
    public void testSameUrlTitleButDifferentUrl() throws IOException {
        SolrTweet tw1 = new SolrTweet(1L, "text", new SolrUser("peter"));
        List<UrlEntry> entries = new ArrayList<UrlEntry>();
        UrlEntry urlEntry = new UrlEntry(2, 18, "http://fulltest.de/url2");
        urlEntry.setResolvedDomain("resolved-domain.de");
        urlEntry.setResolvedTitle("ResolvedTitel");
        entries.add(urlEntry);
        tw1.setUrlEntries(entries);

        SolrTweet tw2 = new SolrTweet(1L, "text2", new SolrUser("peter2"));
        entries = new ArrayList<UrlEntry>();
        urlEntry = new UrlEntry(2, 18, "http://fulltest.de/urlNext");
        urlEntry.setResolvedDomain("resolved-domain.de");
        urlEntry.setResolvedTitle("ResolvedTitel");
        entries.add(urlEntry);
        tw2.setUrlEntries(entries);

        twSearch.update(Arrays.asList(tw1, tw2));

    }

    @Test
    public void testAdSearch() throws Exception {
        SolrTweet tw = createNowTweet(1L, "text jetwick @jetwick", "peter");
        tw.setRt(1);
        tw.setQuality(100);
        twSearch.update(Arrays.asList(tw));
        twSearch.refresh();
        assertEquals(1, twSearch.search("text").size());
        assertEquals(0, twSearch.searchAds("text").size());

        // at the moment no retweets are required
//        tw = createNowTweet(1L, "text #jetwick", "peter");
//        tw.setQuality(100);
//        twSearch.update(Arrays.asList(tw));
//        twSearch.commit();
//        assertEquals(0, twSearch.searchAds("text").size());

        tw = createNowTweet(1L, "RT @karsten: text #jetwick", "peter");
        tw.setRt(1);
        tw.setQuality(100);
        twSearch.update(Arrays.asList(tw));
        twSearch.refresh();
        assertEquals(0, twSearch.searchAds("text").size());

        tw = createNowTweet(1L, "text #jetwick", "peter");
        tw.setRt(1);
        tw.setQuality(90);
        twSearch.update(Arrays.asList(tw));
        twSearch.refresh();
        assertEquals(1, twSearch.searchAds("text").size());
        assertEquals(0, twSearch.searchAds(" ").size());

        tw = createNowTweet(1L, "text #jetwick", "peter");
        tw.setQuality(89);
        tw.setRt(1);
        twSearch.update(Arrays.asList(tw));
        twSearch.refresh();
        assertEquals(0, twSearch.searchAds("text").size());
    }

    @Test
    public void testGetMoreTweets() throws IOException {
        // fill index with 2 tweets and 1 user
        SolrTweet tw2;
        twSearch.update(Arrays.asList(
                createTweet(1L, "test", "peter"),
                tw2 = createTweet(2L, "text", "peter")));
        twSearch.refresh();

        Map<Long, SolrTweet> alreadyExistingTw = new LinkedHashMap<Long, SolrTweet>();
        alreadyExistingTw.put(2L, tw2);
        Map<String, SolrUser> users = new LinkedHashMap<String, SolrUser>();
        SolrUser u = new SolrUser("peter");
        users.put("peter", u);

        // return the tweet (1L) which is not already in the map!
        twSearch.fetchMoreTweets(alreadyExistingTw, users);
        assertEquals(1, u.getOwnTweets().size());
        assertEquals(1L, (long) u.getOwnTweets().iterator().next().getTwitterId());
    }

    @Test
    public void testSnowballStemming() throws IOException {
        twSearch.update(Arrays.asList(createTweet(1L, "duplication", "peter"),
                createTweet(2L, "testing", "peter")));
        twSearch.refresh();

        assertEquals(1, twSearch.searchTweets(new SolrQuery("duplicate")).size());
        assertEquals(1, twSearch.searchTweets(new SolrQuery("test")).size());

        Set<String> stopWords = new LinkedHashSet<String>();
        stopWords.add("duplicate");


        Set<String> set = new TweetESQuery().doSnowballStemming(
                new WhitespaceTokenizer(new StringReader("duplication tester")));
        assertEquals(2, set.size());
        assertTrue(set.contains("tester"));
        assertTrue(set.contains("duplic"));
    }

    @Test
    public void testIndexMerge() throws IOException, InterruptedException {
        String index1 = "index1";
        String index2 = "index2";
        String resindex = "resindex";
//        System.out.println("NOW1");
        twSearch.createIndex(index1);
        twSearch.createIndex(index2);
        twSearch.createIndex(resindex);
//        System.out.println("NOW2");
//        twSearch.waitForYellow(resindex);
    
        twSearch.bulkUpdate(Arrays.asList(
                new SolrTweet(1L, "hey cool one", new SolrUser("peter")),
                new SolrTweet(2L, "two! another one", new SolrUser("test"))), index1);

        twSearch.bulkUpdate(Arrays.asList(
                new SolrTweet(3L, "second index. one", new SolrUser("people")),
                new SolrTweet(4L, "snd index! two", new SolrUser("k")),
                new SolrTweet(5L, "snd index! third", new SolrUser("k"))), index2);        
                
        twSearch.mergeIndices(Arrays.asList(index1, index2), resindex, true);        
        
        assertEquals(5, twSearch.collectTweets(twSearch.query(twSearch.createQuery(resindex).matchAll())).size());
    }

    SolrTweet createSolrTweet(MyDate dt, String twText, String user) {
        return new SolrTweet(dt.getTime(), twText, new SolrUser(user)).setCreatedAt(dt.toDate());
    }

    SolrTweet createTweet(long id, String twText, String user) {
        return new SolrTweet(id, twText, new SolrUser(user)).setCreatedAt(new Date(id));
    }

    SolrTweet createNowTweet(long id, String twText, String user) {
        return new SolrTweet(id, twText, new SolrUser(user)).setCreatedAt(new Date());
    }

    SolrTweet createOldTweet(long id, String twText, String user) {
        return createTweet(id, twText, user).setCreatedAt(new Date(id));
    }

    SolrTweet createTweet(MyDate dt, String twText, String user) {
        return createTweet(dt.getTime(), twText, user).setCreatedAt(dt.toDate());
    }
}