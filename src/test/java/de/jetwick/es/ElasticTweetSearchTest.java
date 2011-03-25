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

import org.elasticsearch.index.query.xcontent.QueryBuilders;
import java.util.LinkedHashSet;
import java.util.Set;
import java.io.StringReader;
import de.jetwick.data.UrlEntry;
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
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
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.indices.IndexMissingException;
import org.elasticsearch.search.facet.termsstats.TermsStatsFacet;
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

    public ElasticTweetSearch getSearch() {
        return twSearch;
    }

    @Before
    public void setUp() throws Exception {
        twSearch = new ElasticTweetSearch(getClient());
        super.setUp(twSearch);
    }

    @Test
    public void testSearch() throws Exception {
        JUser fromUser = new JUser("peter");
        JTweet tw1 = new JTweet(1L, "this is a test!", fromUser);

        JUser otherUser = new JUser("otherUser");
        JTweet tw2 = new JTweet(2L, "Java is cool and stable!", otherUser);
        JTweet tw3 = new JTweet(3L, "Java is stable!", otherUser);
        twSearch.store(tw1, false);
        twSearch.store(tw2, false);
        twSearch.store(tw3, true);

        assertEquals(1, twSearch.search("java").size());
        assertEquals(1, twSearch.search("test").size());
        assertEquals(1, twSearch.searchTweets(new TweetQuery("this test")).size());
        assertEquals(2, twSearch.searchTweets(new TweetQuery("java stable")).size());
        assertEquals(1, twSearch.searchTweets(new TweetQuery("java cool stable")).size());
        assertEquals(2, twSearch.searchTweets(new TweetQuery("java")).size());
        assertEquals(3, twSearch.searchTweets(new TweetQuery("java OR test")).size());
        assertEquals(1, twSearch.searchTweets(new TweetQuery("java -cool")).size());

        try {
            // throw error if contains unescaped lucene chars
            twSearch.searchTweets(new TweetQuery("stable!"));
            assertTrue(false);
        } catch (Exception ex) {
            assertTrue(true);
        }
    }

    @Test
    public void testSmartEscapedSearch() throws Exception {
        JUser fromUser = new JUser("peter");
        JTweet tw1 = new JTweet(1L, "this is a test!", fromUser);

        JUser otherUser = new JUser("otherUser");
        JTweet tw2 = new JTweet(2L, "Java is cool and stable!", otherUser);
        JTweet tw3 = new JTweet(3L, "Java is stable!", otherUser);
        twSearch.store(tw1, false);
        twSearch.store(tw2, false);
        twSearch.store(tw3, true);

        assertEquals(1, twSearch.search("java").size());
        assertEquals(1, twSearch.search("test").size());
        assertEquals(1, twSearch.searchTweets(new TweetQuery("this test").setEscape(true)).size());
        assertEquals(2, twSearch.searchTweets(new TweetQuery("java stable").setEscape(true)).size());
        assertEquals(1, twSearch.searchTweets(new TweetQuery("java cool stable").setEscape(true)).size());
        assertEquals(2, twSearch.searchTweets(new TweetQuery("java").setEscape(true)).size());
        assertEquals(3, twSearch.searchTweets(new TweetQuery("java OR test").setEscape(true)).size());
        assertEquals(1, twSearch.searchTweets(new TweetQuery("java -cool").setEscape(true)).size());
        assertEquals(2, twSearch.searchTweets(new TweetQuery("stable!").setEscape(true)).size());
    }

    @Test
    public void testHashtags() {
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
    public void testHashtags2() {
        twSearch.privateUpdate(Arrays.asList(createTweet(1L, "egypt germany", "peter"),
                createTweet(2L, "egypt #germany", "peter2"),
                createTweet(3L, "egypt #Germany", "peter3"),
                createTweet(4L, "egypt #GERMANY", "peter4")));

        assertEquals(4, twSearch.search("egypt germany").size());
        assertEquals(3, twSearch.search("egypt #germany").size());
    }

    @Test
    public void testSearchAnchors() throws Exception {
        JUser peter = new JUser("peter");
        JTweet tw1 = new JTweet(1L, "peter #java is cool!", peter);
        JUser peter2 = new JUser("peter2");
        JTweet tw2 = new JTweet(2L, "@peter java is cool!", peter2);
        twSearch.store(tw1, false);
        twSearch.store(tw2, true);

        assertEquals(1, twSearch.search("#java").size());
        assertEquals(1, twSearch.search("@peter").size());
    }

    @Test
    public void testCamelCase() throws Exception {
        JTweet tw1 = new JTweet(1L, "peter iBood is cool!", new JUser("peter1"));
        JTweet tw2 = new JTweet(2L, "ibood is cool!", new JUser("peter2"));
        JTweet tw3 = new JTweet(3L, "peter iBOOD is cool!", new JUser("peter3"));
        JTweet tw4 = new JTweet(4L, "Ibood is cool!", new JUser("peter4"));
        JTweet tw5 = new JTweet(5L, "iBOOD.com", new JUser("peter5"));

        twSearch.store(tw1, false);
        twSearch.store(tw2, false);
        twSearch.store(tw3, false);
        twSearch.store(tw5, false);
        twSearch.store(tw4, true);

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
        JUser peter = new JUser("peter1");
        JTweet tw1 = new JTweet(1L, "peter JavaScript is cool!", peter);
        JUser peter2 = new JUser("peter2");
        JTweet tw2 = new JTweet(2L, "java is cool!", peter2);
        JTweet tw3 = new JTweet(3L, "peter javascript is cool!", new JUser("peter3"));
        twSearch.store(tw1, false);
        twSearch.store(tw2, false);
        twSearch.store(tw3, true);

        assertEquals(1, twSearch.search("java").size());
        assertEquals("peter2", twSearch.search("java").iterator().next().getScreenName());
        assertEquals(2, twSearch.search("javascript").size());
        Iterator<JUser> iter = twSearch.search("javascript").iterator();
        assertEquals("peter1", iter.next().getScreenName());
        assertEquals("peter3", iter.next().getScreenName());
    }

    @Test
    public void testSorting() {
        MyDate day = new MyDate();
        MyDate day2 = day.clone().plusDays(1);
        twSearch.store(createSolrTweet(day, "java is a test!", "peter"), false);
        twSearch.store(createSolrTweet(day2, "java is cool and stable!", "peter2"), true);
        JetwickQuery q = new TweetQuery("java").setSort("dt", "desc");
        List<JUser> res = new ArrayList<JUser>();
        twSearch.search(res, q);
        assertEquals(2, res.size());
        assertEquals(day2.getTime(), (long) res.get(0).getOwnTweets().iterator().next().getTwitterId());

        q = new TweetQuery("java").setSort("dt", "asc");
        res.clear();
        twSearch.search(res, q);
        assertEquals(day.getTime(), (long) res.get(0).getOwnTweets().iterator().next().getTwitterId());
    }

    @Test
    public void testLoc() {
        JUser user = new JUser("peter");
        user.setLocation("TEST");
        JTweet tw;
        tw = new JTweet(1L, "test tweet text", user);
        twSearch.store(tw, false);
        tw = new JTweet(2L, "test tweet text2", user);
        twSearch.store(tw, true);
        List<JUser> res = new ArrayList<JUser>();
        twSearch.search(res, new TweetQuery().addFilterQuery("loc", "TEST"));
        assertEquals(1, res.size());
        assertEquals(2, res.get(0).getOwnTweets().size());

        user = new JUser("peter");
        tw = new JTweet(3L, "test tweet text", user);
        tw.setLocation("TEST3");
        twSearch.store(tw, false);

        tw = new JTweet(4L, "test tweet text", user);
        tw.setLocation("TEST4");
        twSearch.store(tw, true);
        res = new ArrayList<JUser>();
        twSearch.search(res, new TweetQuery().addFilterQuery("loc", "TEST3"));
        assertEquals(1, res.size());
        assertEquals(1, res.get(0).getOwnTweets().size());
    }

    @Test
    public void testDelete() throws Exception {
        // do not throw exception
        twSearch.delete(Collections.EMPTY_LIST);

        JUser otherUser = new JUser("otherUser");
        JTweet tw2 = new JTweet(2L, "java is cool and stable!", otherUser);
        twSearch.store(tw2, false);
        twSearch.refresh();
        assertEquals(1, twSearch.search("java").size());

        twSearch.delete(Arrays.asList(tw2));
        twSearch.refresh();
        assertEquals(0, twSearch.search("java").size());
    }

    @Test
    public void testGetReplies() {
        JUser usera = new JUser("usera");
        JTweet tw = new JTweet(1L, "this is a Test ", usera);
        JUser userb = new JUser("userb");
        JTweet tw2 = new JTweet(2L, "this is a Test ", userb);
        tw2.addReply(tw);
        twSearch.store(tw, true);
        twSearch.store(tw2, true);

        assertEquals(0, twSearch.searchReplies(1L, true).size());
        assertEquals(0, twSearch.searchReplies(2L, true).size());
        assertEquals(0, twSearch.searchReplies(1L, false).size());
        assertEquals(1, twSearch.searchReplies(2L, false).size());
        tw = twSearch.searchReplies(2L, false).iterator().next();
        assertEquals(1L, (long) tw.getTwitterId());
    }

    @Test
    public void testGetRetweets() {
        JUser usera = new JUser("usera");
        JTweet tw = new JTweet(1L, "this is a Test ", usera);
        JUser userb = new JUser("userb");
        JTweet tw2 = new JTweet(2L, "rt @usera: this is a Test ", userb);
        tw.addReply(tw2);
        twSearch.store(tw, false);
        twSearch.store(tw2, true);

        assertEquals(1, twSearch.searchReplies(1L, true).size());
        assertEquals(0, twSearch.searchReplies(2L, true).size());
        assertEquals(0, twSearch.searchReplies(1L, false).size());
        assertEquals(0, twSearch.searchReplies(2L, false).size());
        assertEquals(2L, (long) twSearch.searchReplies(1L, true).iterator().next().getTwitterId());
    }

    @Test
    public void testFindDuplicates() {
        twSearch.store(new JTweet(1L, "wikileaks is not a wtf", new JUser("userA")), false);
        twSearch.store(new JTweet(2L, "news about wikileaks", new JUser("userB")), false);

        // find dup is restricted to the last hour so use a current date
        MyDate dt = new MyDate();
        JTweet tw3 = new JTweet(3L, "wtf means wikileaks task force", new JUser("userC")).setCreatedAt(dt.toDate());
        JTweet tw4 = new JTweet(4L, "wtf wikileaks task force", new JUser("userD")).setCreatedAt(dt.plusMinutes(1).toDate());
        JTweet tw5 = new JTweet(5L, "RT @userC: wtf means wikileaks task force", new JUser("userE")).setCreatedAt(dt.plusMinutes(1).toDate());
        twSearch.update(Arrays.asList(tw3, tw4, tw5), new Date(0));
        assertEquals("should be empty. should NOT find tweet 4 because it is younger", 0, tw3.getDuplicates().size());
        assertEquals("should find tweet 3", 1, tw4.getDuplicates().size());

        Map<Long, JTweet> map = new LinkedHashMap<Long, JTweet>();
        JTweet tw = new JTweet(10L, "wtf wikileaks task force", new JUser("peter")).setCreatedAt(dt.plusMinutes(1).toDate());
        map.put(10L, tw);
        twSearch.findDuplicates(map);
        assertEquals("should find tweets 3 and 4", 2, tw.getDuplicates().size());
    }

    @Test
    public void testSpamDuplicates() {
        MyDate dt = new MyDate();
        JTweet tw1 = new JTweet(1L, "2488334. Increase your twitter followers now! Buy Twitter Followers", new JUser("userA")).setCreatedAt(dt.plusMinutes(1).toDate());
        JTweet tw2 = new JTweet(2L, "349366. Increase your twitter followers now! Buy Twitter Followers", new JUser("userB")).setCreatedAt(dt.plusMinutes(1).toDate());
        JTweet tw3 = new JTweet(31L, "2040312. Increase your twitter followers now! Buy Twitter Followers", new JUser("userC")).setCreatedAt(dt.plusMinutes(1).toDate());
        twSearch.update(Arrays.asList(tw1, tw2, tw3), new Date(0));

        assertEquals(0, tw1.getDuplicates().size());
        assertEquals(1, tw2.getDuplicates().size());
        assertEquals(2, tw3.getDuplicates().size());
    }

    @Test
    public void testBatchUpdate() {
        List<JTweet> list = new ArrayList<JTweet>();

        list.add(createTweet(1L, "text", "usera"));
        list.add(createTweet(2L, "RT @usera: text", "userb"));

        list.add(createTweet(3L, "text2", "usera"));
        list.add(createTweet(4L, "hey I read your text", "userb").setInReplyTwitterId(3L));

        Collection<JTweet> res = twSearch.update(list, new Date(0));
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
        JTweet twC = twSearch.findByTwitterId(3L);
        assertEquals(1, twC.getReplyCount());

        // A should have C as reply
        JTweet twA = twSearch.findByTwitterId(1L);
        assertEquals(1, twA.getReplyCount());

        // now check if B was properly connected with A
        twSearch.update(createTweet(2L, "B", "u2").setInReplyTwitterId(1L));

        twA = twSearch.findByTwitterId(1L);
        assertEquals(2, twA.getReplyCount());

        // return null when not found
        assertNull(twSearch.findByTwitterId(23L));
    }

    @Test
    public void testAttach() throws Exception {
        twSearch.update(createTweet(1, "test", "peter"));
        twSearch.update(createTweet(2, "test2", "peter"));

        assertEquals(2, twSearch.findByUserName("peter").getOwnTweets().size());
    }

    @Test
    public void testDoNotSaveSecondUser() {
        JTweet fTweet = createTweet(5, "@peter @karsten bla bli", "peter");
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
        JTweet fTweet = createTweet(5, "@karsten bla bli", "peter");
        twSearch.update(fTweet);

        fTweet = createTweet(6, "@peter bla bli", "karsten");
        twSearch.update(fTweet);
        assertNotNull(twSearch.findByUserName("karsten"));
    }

    @Test
    public void testNoDuplicateUser2() {
        JTweet fTweet = createTweet(1, "@karsten bla bli", "peter");
        twSearch.update(fTweet);

        fTweet = createTweet(2, "@Karsten bla bli", "Peter");
        twSearch.update(fTweet);
    }

    @Test
    public void testNoDuplicateTweet() {
        JTweet fTweet = createTweet(123, "@karsten bla bli", "peter");
        twSearch.update(fTweet);
        twSearch.update(fTweet);

        assertEquals(1, twSearch.countAll());
        assertEquals(1, twSearch.findByUserName("peter").getOwnTweets().size());
    }

    @Test
    public void testUpdateTweetsWhichIsInfluencedFromActivationDepth() throws Exception {
        JTweet tw1 = createTweet(1L, "tweet1", "peter");
        JTweet tw2 = createTweet(2L, "tweet2", "peter");

        twSearch.update(tw1);
        twSearch.update(tw2);

        assertEquals(2, twSearch.findByUserName("peter").getOwnTweets().size());

        tw1 = createTweet(1L, "tweet1", "peter");
        twSearch.update(tw1);

        assertEquals(2, twSearch.findByUserName("peter").getOwnTweets().size());
    }

    @Test
    public void testUpdateAndRemove() throws Exception {
        JTweet tw1 = createTweet(1L, "@karsten hajo", "peter");
        tw1.setCreatedAt(new MyDate().minusDays(2).toDate());

        twSearch.update(tw1);
        assertEquals(1, twSearch.countAll());
        assertEquals("@karsten hajo", twSearch.search("hajo").iterator().next().getOwnTweets().iterator().next().getText());
        assertEquals(1, twSearch.findByUserName("peter").getOwnTweets().size());

        JTweet tw = createTweet(2L, "test", "peter");
        tw.setCreatedAt(new Date());
        Collection<JTweet> res = twSearch.update(Arrays.asList(tw),
                new MyDate().minusDays(1).toDate());
        // now refresh also deletes!
        twSearch.refresh();
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
        twSearch.update(createTweet(3L, "RT @userA: bla bli blu", "userb"));

        assertEquals(1, twSearch.findByTwitterId(1L).getReplyCount());
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
        JTweet tw = createTweet(2L, "RT @userA: bla bli blu", "userB");
        tw.setCreatedAt(new MyDate().minusDays(2).toDate());
        assertEquals(0, twSearch.update(Arrays.asList(tw),
                new MyDate().minusDays(1).toDate()).size());
    }

    @Test
    public void testAddOldTweetsIfPersistent() {
        JTweet tw = createTweet(2L, "RT @userA: bla bli blu", "userB");
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
        JTweet tw1 = createTweet(4L, "newbla next", "userc").setRt(100);
        tw1.setCreatedAt(new MyDate().minusDays(2).toDate());        
        
        JTweet tw2 = createTweet(2L, "RT @userA: bla bli blu", "userB");
        Date dt = new MyDate().minusDays(2).toDate();
        tw2.setUpdatedAt(dt);
        tw2.setCreatedAt(dt);        
                
        // until date is very old to let tweets going through
        assertEquals(2, twSearch.update(Arrays.asList(tw2, tw1), new Date(0)).size());
        assertEquals(2, twSearch.countAll());        
        assertEquals(100, twSearch.findByTwitterId(4L).getRetweetCount());
        assertNotNull(twSearch.findByTwitterId(2L).getUpdatedAt());        

        JTweet tw3 = createTweet(3L, "another tweet grabbed from search", "userB");
        tw3.setCreatedAt(new MyDate().minusDays(2).toDate());        
        Collection<JTweet> updatedTweets = twSearch.update(Arrays.asList(tw3), new MyDate().minusDays(1).toDate());        
        assertEquals(0, updatedTweets.size());
        assertEquals(2, twSearch.countAll());
        assertTrue(twSearch.searchTweets(new TweetQuery()).contains(tw2));
        assertTrue(twSearch.searchTweets(new TweetQuery()).contains(tw1));
    }

    @Test
    public void testComplexUpdate() throws Exception {
        JTweet tw1 = createTweet(1L, "bla bli blu", "userA");
        tw1.setCreatedAt(new MyDate().minusDays(2).toDate());

        JTweet tw2 = createTweet(2L, "rt @usera: bla bli blu", "userB");
        tw2.setCreatedAt(new MyDate().minusDays(2).plusMinutes(1).toDate());

        JTweet tw3 = createTweet(3L, "rt @usera: bla bli blu", "userC");
        tw3.setCreatedAt(new MyDate().minusDays(2).plusMinutes(1).toDate());

        JTweet tw4 = createTweet(4L, "rt @usera: bla bli blu", "userD");
        tw4.setCreatedAt(new MyDate().minusDays(2).plusMinutes(1).toDate());

        Collection<JTweet> updatedTweets = twSearch.privateUpdate(Arrays.asList(tw1, tw2, tw3, tw4));
        assertEquals(1, twSearch.findByUserName("usera").getOwnTweets().size());
        assertEquals(3, twSearch.findByTwitterId(1L).getReplyCount());
        assertEquals(4, updatedTweets.size());

        // we do not sort the tweets anylonger so that 104 could be also a retweet of:
//        SolrTweet tw100 = createTweet(100L, "newtext", "usera");
        JTweet tw101 = createTweet(101L, "newtext two", "usera");
        tw101.setCreatedAt(new Date());
        JTweet tw102 = createTweet(102L, "newbla one", "userd");
        tw102.setCreatedAt(new Date());
        JTweet tw103 = createTweet(103L, "newbla two", "userd");
        tw103.setCreatedAt(new Date());
        JTweet tw104 = createTweet(104L, "rt @usera: newtext two", "userc");
        tw104.setCreatedAt(new MyDate(tw101.getCreatedAt()).plusMinutes(1).toDate());

        updatedTweets = twSearch.update(Arrays.asList(tw101, tw102, tw103, tw104),
                new MyDate().minusDays(1).toDate());
        // now refresh also deletes!
        twSearch.refresh();
        assertEquals(4, twSearch.countAll());
        assertEquals(4, updatedTweets.size());
        assertEquals(1, twSearch.findByTwitterId(101L).getRetweetCount());
        assertEquals(1, twSearch.findByTwitterId(101L).getReplyCount());

        // no tweet exists with that string
        assertEquals(0, twSearch.search("bla bli blu").size());
    }

    @Test
    public void testDoNotThrowQueryParserException() {
        JTweet tw = createTweet(1L, "rt @jenny2s: -- Earth, Wind & Fire - September  (From \"Live In Japan\")"
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
    public void testUserChoices() {
        twSearch.privateUpdate(Arrays.asList(createTweet(1L, "test", "usera"),
                createTweet(2L, "pest", "usera"),
                createTweet(3L, "schnest", "usera")));

        Collection<String> coll = twSearch.getUserChoices(null, "user");
        assertEquals(1, coll.size());
        coll = twSearch.getUserChoices(null, "loose");
        assertEquals(0, coll.size());
    }

    @Test
    public void testQueryChoices() {
        twSearch.privateUpdate(Arrays.asList(createTweet(1L, "abcd", "usera"),
                createTweet(2L, "bluest abcdxy abcdxy", "usera"),
                createTweet(3L, "bluest bluest abcdxy", "usera"),
                createTweet(4L, "abort", "usera"),
                createTweet(5L, "bingo", "usera"),
                createTweet(6L, "chemical", "usera"),
                createTweet(7L, "destination", "usera"),
                createTweet(8L, "estimation", "usera"),
                createTweet(9L, "finish", "usera"),
                createTweet(10L, "ginie", "usera"),
                createTweet(11L, "home", "usera"),
                // why is as last tweet this necessary? otherwise we don't get 'home' as tag!?
                createTweet(12L, "testing", "usera")));

        assertEquals(2L, twSearch.search(new TweetQuery().addFilterQuery("tag", "bluest")).getHits().getTotalHits());
        assertEquals(1L, twSearch.search(new TweetQuery("home")).getHits().getTotalHits());

        Collection<String> coll = twSearch.getQueryChoices(null, "abcdxy");
        assertEquals(0, coll.size());

        coll = twSearch.getQueryChoices(null, "ab");
        assertEquals(3, coll.size());
        assertTrue(coll.contains("abcdxy"));

        // it is important to filter (with regex filter) away some tags otherwise we don't get the important ones:
        coll = twSearch.getQueryChoices(null, "ho");
        assertEquals(1, coll.size());
        assertTrue(coll.contains("home"));

        coll = twSearch.getQueryChoices(null, "abcdxy ");
        assertEquals(1, coll.size());
        assertTrue(coll.contains("abcdxy bluest"));
    }

    @Test
    public void testQueryChoicesWithoutDateRestrictions() {
        twSearch.privateUpdate(Arrays.asList(createTweet(new MyDate().minusDays(1).minusMinutes(3), "obama obama", "usera"),
                createTweet(new MyDate().minusDays(1).minusMinutes(2), "bluest obama obama", "usera"),
                createTweet(new MyDate().minusDays(1).minusMinutes(1), "bluest bluest obama", "usera"),
                createTweet(new MyDate().minusDays(1), "obama bluest again and again", "usera")));

        assertEquals(3L, twSearch.search(new TweetQuery().addFilterQuery("tag", "bluest")).getHits().getTotalHits());

        Collection<String> coll = twSearch.getQueryChoices(new TweetQuery().addLatestDateFilter(8), "obama ");
        assertEquals(1, coll.size());
        assertTrue(coll.contains("obama bluest"));
    }

    @Test
    public void testFindOrigin() {
        twSearch.privateUpdate(Arrays.asList(createTweet(1L, "text", "usera"),
                createTweet(2L, "RT @usera: text", "userb"),
                createTweet(3L, "RT @usera: text", "userc"),
                createTweet(4L, "new text", "userd")));

        JetwickQuery q = twSearch.createFindOriginQuery(null, "text", 1);
        // crt_b, retw_i
        assertEquals(2, q.getFilterQueries().size());
        assertEquals(ElasticTweetSearch.RT_COUNT, q.getFilterQueries().get(1).getKey());
        assertEquals("[1 TO *]", q.getFilterQueries().get(1).getValue());

        // too high minResults
        int minResults = 3;
        q = twSearch.createFindOriginQuery(null, "text", minResults);
        assertEquals(1, q.getFilterQueries().size());

        // no retweets for 'new text'
        q = twSearch.createFindOriginQuery(null, "new text", 2);
        assertEquals(1, q.getFilterQueries().size());
    }

    @Test
    public void testFacets() {
        twSearch.privateUpdate(Arrays.asList(createTweet(1L, "Beitrag atom. atom again", "userA"),
                createTweet(2L, "atom gruene", "userA"),
                createTweet(3L, "third tweet", "userA")));

        SearchResponse rsp = twSearch.search(new TweetQuery(true));
        assertEquals(3, rsp.hits().getTotalHits());
        // only the second tweet will contain a tag with atom!
        assertEquals(1, ((TermsStatsFacet) rsp.facets().facet("tag")).getEntries().size());

        rsp = twSearch.search(new TweetQuery().addFilterQuery("tag", "atom"));
        assertEquals(2, twSearch.collectObjects(rsp).size());
    }

    @Test
    public void testReadUrlEntries() throws IOException {
        JTweet tw = new JTweet(1L, "text", new JUser("peter"));
        List<UrlEntry> entries = new ArrayList<UrlEntry>();

        UrlEntry urlEntry = new UrlEntry(2, 18, "http://fulltest.de/bla");
        urlEntry.setResolvedDomain("resolved-domain.de");
        urlEntry.setResolvedTitle("ResolvedTitel");
        entries.add(urlEntry);

        tw.setUrlEntries(entries);

        XContentBuilder iDoc = twSearch.createDoc(tw);
        String str = iDoc.prettyPrint().string();
        assertTrue(str.contains("\"url_pos_1_s\":\"2,18\""));
        assertTrue(str.contains("\"dest_url_1_s\":\"http://fulltest.de/bla\""));
        assertTrue(str.contains("\"dest_domain_1_s\":\"resolved-domain.de\""));
        assertTrue(str.contains("\"dest_title_1_s\":\"ResolvedTitel\""));

        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("user", "peter");
        map.put("tw", "text");
        map.put("url_i", 1);
        map.put("retw_i", 0);
        map.put("repl_i", 0);
        map.put("url_pos_1_s", "2,18");
        map.put("dest_url_1_s", "http://fulltest.de/bla");
        map.put("dest_domain_1_s", "resolved-domain.de");
        map.put("dest_title_1_s", "ResolvedTitel");

        JTweet tw2 = twSearch.readDoc(map, "1");
        assertEquals(1, tw2.getUrlEntries().size());
        Iterator<UrlEntry> iter = tw2.getUrlEntries().iterator();
        urlEntry = iter.next();
        assertEquals("http://fulltest.de/bla", urlEntry.getResolvedUrl());
        assertEquals("resolved-domain.de", urlEntry.getResolvedDomain());
        assertEquals("ResolvedTitel", urlEntry.getResolvedTitle());
        assertEquals(2, urlEntry.getIndex());
        assertEquals(18, urlEntry.getLastIndex());
    }

    @Test
    public void testSameUrlTitleButDifferentUrl() throws IOException {
        JTweet tw1 = new JTweet(1L, "text", new JUser("peter"));
        List<UrlEntry> entries = new ArrayList<UrlEntry>();
        UrlEntry urlEntry = new UrlEntry(2, 18, "http://fulltest.de/url2");
        urlEntry.setResolvedDomain("resolved-domain.de");
        urlEntry.setResolvedTitle("ResolvedTitel");
        entries.add(urlEntry);
        tw1.setUrlEntries(entries);

        JTweet tw2 = new JTweet(1L, "text2", new JUser("peter2"));
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
        JTweet tw = createNowTweet(1L, "text jetwick @jetwick", "peter");
        tw.setRt(1);
        tw.setQuality(100);
        twSearch.update(Arrays.asList(tw));
        assertEquals(1, twSearch.search("text").size());
        assertEquals(0, twSearch.searchAds("text").size());

        // at the moment no retweets are required
//        tw = createNowTweet(1L, "text #jetwick", "peter");
//        tw.setQuality(100);
//        twSearch.store(Arrays.asList(tw));
//        twSearch.commit();
//        assertEquals(0, twSearch.searchAds("text").size());

        tw = createNowTweet(1L, "RT @karsten: text #jetwick", "peter");
        tw.setRt(1);
        tw.setQuality(100);
        twSearch.update(Arrays.asList(tw));
        assertEquals(0, twSearch.searchAds("text").size());

        tw = createNowTweet(1L, "text #jetwick", "peter");
        tw.setRt(1);
        tw.setQuality(90);
        twSearch.update(Arrays.asList(tw));
        assertEquals(1, twSearch.searchAds("text").size());
        assertEquals(0, twSearch.searchAds(" ").size());

        tw = createNowTweet(1L, "text #jetwick", "peter");
        tw.setQuality(89);
        tw.setRt(1);
        twSearch.update(Arrays.asList(tw));
        assertEquals(0, twSearch.searchAds("text").size());
    }

    @Test
    public void testGetMoreTweets() throws IOException {
        // fill index with 2 tweets and 1 user
        JTweet tw2;
        twSearch.update(Arrays.asList(
                createTweet(1L, "test", "peter"),
                tw2 = createTweet(2L, "text", "peter")));

        Map<Long, JTweet> alreadyExistingTw = new LinkedHashMap<Long, JTweet>();
        alreadyExistingTw.put(2L, tw2);
        Map<String, JUser> users = new LinkedHashMap<String, JUser>();
        JUser u = new JUser("peter");
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

        assertEquals(1, twSearch.searchTweets(new TweetQuery("duplicate")).size());
        assertEquals(1, twSearch.searchTweets(new TweetQuery("test")).size());

        Set<String> stopWords = new LinkedHashSet<String>();
        stopWords.add("duplicate");

        Set<String> set = new SimilarQuery().doSnowballStemming(
                new WhitespaceTokenizer(new StringReader("duplication tester")));
        assertEquals(2, set.size());
        assertTrue(set.contains("tester"));
        assertTrue(set.contains("duplic"));
    }

    @Test
    public void testFollowerSearch() throws Exception {
        twSearch.update(Arrays.asList(
                createTweet(1L, "test this", "peter"),
                createTweet(2L, "test others", "tester"),
                createTweet(3L, "testnot this", "peter"),
                createTweet(4L, "test this", "peternot")));
        Collection<String> users = Arrays.asList("peter", "tester");
        Collection<JTweet> coll = twSearch.collectObjects(twSearch.search(new TweetQuery("test").createFriendsQuery(users)));

        assertEquals(2, coll.size());
        int counter = 0;
        for (JTweet tw : coll) {
            if (tw.getTwitterId() == 1L)
                counter++;
            else if (tw.getTwitterId() == 2L)
                counter++;
        }
        assertEquals(2, counter);
    }

    @Test
    public void testIndexMerge() throws IOException, InterruptedException {
        String index1 = "index1";
        String index2 = "index2";
        String resindex = "resindex";
        twSearch.createIndex(index1);
        twSearch.createIndex(index2);
        twSearch.createIndex(resindex);
        twSearch.waitForYellow(resindex);
        twSearch.deleteAll(index1);
        twSearch.deleteAll(index2);
        twSearch.deleteAll(resindex);

        twSearch.bulkUpdate(Arrays.asList(
                new JTweet(1L, "hey cool one", new JUser("peter")),
                new JTweet(2L, "two! another one", new JUser("test"))), index1);

        twSearch.bulkUpdate(Arrays.asList(
                new JTweet(3L, "second index. one", new JUser("people")),
                new JTweet(4L, "snd index! two", new JUser("k")),
                new JTweet(5L, "snd index! third", new JUser("k"))), index2);

        twSearch.mergeIndices(Arrays.asList(index1, index2), resindex, 10, true, null);

        assertEquals(5, twSearch.countAll(resindex));
    }

    @Test
    public void testUpdateOneTweetForTwoIndices() throws IOException, InterruptedException {
        String index1 = "index1";
        String index2 = "index2";
        twSearch.saveCreateIndex(index1, false);
        twSearch.saveCreateIndex(index2, false);
        twSearch.waitForYellow(index1);
        twSearch.deleteAll(index1);
        twSearch.deleteAll(index2);

        List<JTweet> list = new ArrayList<JTweet>();
        JUser user2 = new JUser("peter2");
        for (int i = 0; i < 2; i++) {
            list.add(new JTweet(i, "nice day", user2));
        }
        twSearch.bulkUpdate(list, index1, true);
        assertEquals(2, twSearch.countAll(index1));
        assertEquals(0, twSearch.countAll(index2));

        twSearch.bulkUpdate(list, index1, true);
        twSearch.bulkUpdate(list, index2, true);
        assertEquals(2, twSearch.countAll(index1));

        // !! indices are independent !!
        assertEquals(2, twSearch.countAll(index2));
        assertEquals(4, twSearch.countAll(index1, index2));
    }

    @Test
    public void testIndexMergeWithPaging() throws Exception {
        String index1 = "index1";
        String index2 = "index2";
        String resindex = "resindex";
        twSearch.saveCreateIndex(index1, false);
        twSearch.saveCreateIndex(index2, false);
        twSearch.saveCreateIndex(resindex, false);
        twSearch.waitForYellow(resindex);

        // clearing index
        twSearch.deleteAll(index1);
        twSearch.deleteAll(index2);
        twSearch.deleteAll(resindex);

        // this store makes a problem later on, when searching on index1
        twSearch.bulkUpdate(Arrays.asList(new JTweet(1L, "test", new JUser("testuser"))), index1, true);

        List<JTweet> list = new ArrayList<JTweet>();
        JUser user = new JUser("peter");
        for (int i = 0; i < 100; i++) {
            list.add(new JTweet(i, "hey cool one", user));
        }
        JUser user2 = new JUser("peter2");
        for (int i = 100; i < 200; i++) {
            list.add(new JTweet(i, "nice day", user2));
        }
        twSearch.bulkUpdate(list, index1, true);
        // identical tweets -> TODO do or don't store?
        List<JTweet> list2 = new ArrayList<JTweet>();
        for (int i = 0; i < 100; i++) {
            list2.add(new JTweet(i, "[updated] hey cool one", user));
        }
        // different tweets
        JUser user3 = new JUser("peter3");
        for (int i = 300; i < 400; i++) {
            list2.add(new JTweet(i, "what's going on?", user3));
        }
        twSearch.bulkUpdate(list2, index2, true);
//        System.out.println("1:" + twSearch.countAll(index1) + " 2:" + twSearch.countAll(index2) + " res:" + twSearch.countAll(resindex));
        twSearch.mergeIndices(Arrays.asList(index1, index2), resindex, 2, true, null);

        // 100 + 100 in the first list. in list2 only 100 new => 300
        assertEquals(300, twSearch.countAll(resindex));

        SearchResponse rsp = twSearch.query(new TweetQuery().setSize(1000), resindex);
        assertEquals(300, twSearch.collectObjects(twSearch.search(new ArrayList(), rsp)).size());
    }

    @Test
    public void testDeleteAndAlias() throws IOException, InterruptedException {
        // make sure we can delete all entries from resindex        

        String index1 = "index1";
        String resindex = "resindex";
        twSearch.saveCreateIndex(index1, false);
        twSearch.saveCreateIndex(resindex, false);
        twSearch.waitForYellow(resindex);

        twSearch.deleteAll(index1);
        twSearch.deleteAll(resindex);
        // index2 was created in the previously test
        // don't remove index2 to make sure we grab really only from index1
//        twSearch.deleteAll("index2");        

        List<JTweet> list = new ArrayList<JTweet>();
        JUser user = new JUser("peter");
        for (int i = 0; i < 100; i++) {
            list.add(new JTweet(i, "hey cool one", user));
        }

        twSearch.bulkUpdate(list, index1, true);
        assertEquals(0, twSearch.countAll(resindex));
        twSearch.mergeIndices(Arrays.asList(index1), resindex, 2, true, null);
        assertEquals(100, twSearch.countAll(resindex));
        assertEquals(100, twSearch.countAll(index1));

        twSearch.deleteIndex(index1);
        try {
            assertEquals(0, twSearch.countAll(index1));
            assertFalse(true);
        } catch (IndexMissingException ex) {
            assertTrue(true);
        }
        twSearch.addIndexAlias(resindex, index1);
        assertEquals(100, twSearch.countAll(index1));
    }

    @Test
    public void testQueryMultipleIndices() throws Exception {
        String index1 = "index1";
        String index2 = "index2";
        twSearch.saveCreateIndex(index1, false);
        twSearch.saveCreateIndex(index2, false);
        twSearch.waitForYellow(index1);

        twSearch.deleteAll(index1);
        twSearch.deleteAll(index2);

        twSearch.bulkUpdate(Arrays.asList(new JTweet(1L, "test", new JUser("testuser")).setRt(0)), index1, true);
        twSearch.bulkUpdate(Arrays.asList(new JTweet(1L, "test", new JUser("testuser")).setRt(2)), index2, true);

        SearchResponse rsp = twSearch.getClient().prepareSearch(index1, index2).
                setQuery(QueryBuilders.matchAllQuery()).execute().actionGet();
        assertEquals(2, twSearch.collectObjects(rsp).size());
    }

    @Test
    public void testFindUser() {
        twSearch.update(Arrays.asList(
                createTweet(1L, "test this", "peter"),
                createTweet(4L, "test this", "peter_not")));

        assertEquals(1, twSearch.collectObjects(twSearch.search(new TweetQuery().addFilterQuery(ElasticTweetSearch.USER, "peter"))).size());
    }

    @Test
    public void testSuggestFilterRemoval() {
        MyDate md = new MyDate();
        twSearch.update(Arrays.asList(
                createTweet(1L, "RT @user3: test this first tweet", "peter").setCreatedAt(md.toDate()),
                createTweet(2L, "test others", "peter2").setCreatedAt(md.toDate()),
                createTweet(3L, "testnot this", "peter3").setCreatedAt(md.minusHours(2).toDate()),
                createTweet(4L, "test this", "peter4").setCreatedAt(md.toDate())));

        JetwickQuery q = new TweetQuery(false).
                addIsOriginalTweetFilter().
                addLatestDateFilter(1).
                addUserFilter("peter");
        Collection<String> keys = twSearch.suggestRemoval(q);
        assertEquals(3, keys.size());
        Iterator<String> iter = keys.iterator();
        assertEquals(ElasticTweetSearch.USER, iter.next());
        assertEquals(ElasticTweetSearch.DATE, iter.next());
        assertEquals(ElasticTweetSearch.IS_RT, iter.next());
    }
    
    JTweet createSolrTweet(MyDate dt, String twText, String user) {
        return new JTweet(dt.getTime(), twText, new JUser(user)).setCreatedAt(dt.toDate());
    }

    JTweet createTweet(long id, String twText, String user) {
        return new JTweet(id, twText, new JUser(user)).setCreatedAt(new Date(id));
    }

    JTweet createNowTweet(long id, String twText, String user) {
        return new JTweet(id, twText, new JUser(user)).setCreatedAt(new Date());
    }

    JTweet createOldTweet(long id, String twText, String user) {
        return createTweet(id, twText, user).setCreatedAt(new Date(id));
    }

    JTweet createTweet(MyDate dt, String twText, String user) {
        return createTweet(dt.getTime(), twText, user).setCreatedAt(dt.toDate());
    }
}
