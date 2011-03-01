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

import java.util.regex.Pattern;
import org.elasticsearch.index.query.xcontent.NotFilterBuilder;
import de.jetwick.util.MyDate;
import org.elasticsearch.search.facet.filter.FilterFacet;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.elasticsearch.index.query.xcontent.QueryBuilders;
import org.elasticsearch.search.SearchHits;
import de.jetwick.config.Configuration;
import de.jetwick.data.UrlEntry;
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
import de.jetwick.tw.Extractor;
import de.jetwick.tw.cmd.SerialCommandExecutor;
import de.jetwick.tw.cmd.TermCreateCommand;
import de.jetwick.util.Helper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.mvel2.Operator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.xcontent.FilterBuilders;
import org.elasticsearch.index.query.xcontent.QueryStringQueryBuilder;
import org.elasticsearch.index.query.xcontent.RangeFilterBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.facet.FacetBuilders;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.elasticsearch.search.facet.terms.TermsFacetBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides search functionality via elasticsearch.
 * 
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class ElasticTweetSearch extends AbstractElasticSearch<JTweet> {

    public static final String TWEET_TEXT = "tw";
    public static final String DATE = "dt";
    public static final String DATE_FACET = "datefacet";
    public static final String RT_COUNT = "retw_i";
    public static final String DUP_COUNT = "dups_i";
    public static final String IS_RT = "crt_b";
    public static final String UPDATE_DT = "update_dt";
    public static final String TAG = "tag";
    public static final String INREPLY_ID = "inreply_l";
    public static final String QUALITY = "quality_i";
    public static final String URL_COUNT = "url_i";
    public static final String FIRST_URL_TITLE = "dest_title_1_s";
//    public static final String KEY_USER = "user:";
    public static final String USER = "user";
//    public static final String FILTER_IS_NOT_RT = IS_RT + ":\"false\"";
    public static final String FILTER_NO_DUPS = DUP_COUNT + ":0";
    public static final String FILTER_ONLY_DUPS = DUP_COUNT + ":[1 TO *]";
    public static final String FILTER_NO_URL_ENTRY = URL_COUNT + ":0";
    public static final String FILTER_URL_ENTRY = URL_COUNT + ":[1 TO *]";
    public static final String FILTER_NO_SPAM = QUALITY + ":[" + (JTweet.QUAL_SPAM + 1) + " TO *]";
    public static final String FILTER_SPAM = QUALITY + ":[* TO " + JTweet.QUAL_SPAM + "]";
    public static final String RELEVANCE = "relevance";
    private String indexName = "twindex";
    private Logger logger = LoggerFactory.getLogger(getClass());

    public ElasticTweetSearch() {
    }

    public ElasticTweetSearch(Configuration config) {
        this(config.getTweetSearchUrl(), config.getTweetSearchLogin(), config.getTweetSearchPassword());
    }

    public ElasticTweetSearch(String url, String login, String pw) {
        super(url, login, pw);
    }

    public ElasticTweetSearch(Client client) {
        super(client);
    }

    @Override
    public String getIndexName() {
        return indexName;
    }

    @Override
    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    @Override
    public String getIndexType() {
        return "tweet";
    }

    Client getClient() {
        return client;
    }

    public void deleteUntil(Date removeUntil) {
        NotFilterBuilder f1 = FilterBuilders.notFilter(FilterBuilders.existsFilter(UPDATE_DT));
        RangeFilterBuilder rfb = FilterBuilders.rangeFilter(DATE);
        rfb.lte(new MyDate(removeUntil.getTime()).castToDay().toDate()).cache(true);

        DeleteByQueryResponse response2 = client.prepareDeleteByQuery(getIndexName()).
                setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), FilterBuilders.andFilter(f1, rfb))).
                execute().
                actionGet();
    }

    public void delete(Collection<JTweet> tws) {
        if (tws.isEmpty())
            return;

        try {
            for (JTweet tw : tws) {
                deleteById(tw.getId());
            }

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public SearchResponse query(JetwickQuery query) {
        return query(query, getIndexName());
    }

    public SearchResponse query(JetwickQuery query, String index) {
        SearchRequestBuilder srb = client.prepareSearch(index);
        query.initRequestBuilder(srb);
        return srb.execute().actionGet();
    }

    public XContentBuilder createDoc(JTweet tw) throws IOException {
        if (tw.getFromUser() == null) {
            // this came from UpdateResult.addNewTweet(tweet1); UpdateResult.addRemovedTweet(tweet1) at the same time
            // but should be fixed via if (!removedTweets.contains(tweet)) newTweets.add(tweet);
            logger.error("fromUser of tweet must not be null:" + tw.getTwitterId() + " " + tw.getText());
            return null;
        }

        // daemon tweets have no known twitterId and no known createdAt date
        if (tw.isDaemon())
            return null;

        XContentBuilder b = JsonXContent.unCachedContentBuilder().startObject();
        b.field(TWEET_TEXT, tw.getText());
        b.field("tw_i", tw.getText().length());
        b.field(UPDATE_DT, tw.getUpdatedAt());
        b.field(DATE, tw.getCreatedAt());
        b.field(IS_RT, tw.isRetweet());

        if (tw.getLocation() == null)
            b.field("loc", tw.getFromUser().getLocation());
        else
            b.field("loc", tw.getLocation());

        if (!JTweet.isDefaultInReplyId(tw.getInReplyTwitterId()))
            b.field(INREPLY_ID, tw.getInReplyTwitterId());

        b.field(USER, tw.getFromUser().getScreenName());
        b.field("iconUrl", tw.getFromUser().getProfileImageUrl());

        double relevancy = tw.getCreatedAt().getTime() / MyDate.ONE_HOUR;
        // every 14 retweets boosts the tweet one hour further
        float scale = 14;
        if (tw.getRetweetCount() <= 100)
            relevancy += tw.getRetweetCount() / scale;
        else
            relevancy += 100 / scale;
        if (tw.getText().length() <= 30)
            relevancy *= 0.5;
        if (tw.getQuality() <= 65)
            relevancy *= 0.5;
        b.field(RELEVANCE, relevancy);

        for (Entry<String, Integer> entry : tw.getTextTerms().entrySet()) {
            b.field(TAG, entry.getKey());
        }

        int counter = 0;
        for (UrlEntry urlEntry : tw.getUrlEntries()) {
            if (!urlEntry.getResolvedTitle().isEmpty()
                    && !urlEntry.getResolvedUrl().isEmpty()) {
                counter++;
                b.field("url_pos_" + counter + "_s", urlEntry.getIndex() + "," + urlEntry.getLastIndex());
                b.field("dest_url_" + counter + "_s", urlEntry.getResolvedUrl());
                b.field("dest_domain_" + counter + "_s", urlEntry.getResolvedDomain());
                b.field("dest_title_" + counter + "_s", urlEntry.getResolvedTitle());
                // index this field with a tokenizer ...
                if (counter == 1)
                    b.field("dest_title_t", urlEntry.getResolvedTitle());

                if (counter >= 3)
                    break;
            }
        }

        b.field("url_i", counter);
        b.field(DUP_COUNT, tw.getDuplicates().size());
        b.field("lang", tw.getLanguage());
        b.field("quality_i", tw.getQuality());
        b.field("repl_i", tw.getReplyCount());
        b.field(RT_COUNT, tw.getRetweetCount());

        b.endObject();
        return b;
    }

    @Override
    public JTweet readDoc(Map<String, Object> source, String idAsStr) {
        // if we use in mapping: "_source" : {"enabled" : false}
        // we need to include all fields in query to use doc.getFields() 
        // instead of doc.getSource()

        String name = (String) source.get(USER);
        String text = (String) source.get(TWEET_TEXT);
        if (text == null || name == null || idAsStr == null) {
            logger.error("Null tweet text or id!!!??" + idAsStr + " " + name + " " + text);
            return new JTweet(-1L, "", new JUser(""));
        }

        JUser user = new JUser(name);
        user.setLocation((String) source.get("loc"));
        user.setProfileImageUrl((String) source.get("iconUrl"));

        long id = Long.parseLong(idAsStr);
        JTweet tw = new JTweet(id, text, user);

        tw.setCreatedAt(Helper.toDateNoNPE((String) source.get(DATE)));
        tw.setUpdatedAt(Helper.toDateNoNPE((String) source.get(UPDATE_DT)));
        int rt = ((Number) source.get(RT_COUNT)).intValue();
        int rp = ((Number) source.get("repl_i")).intValue();
        tw.setRt(rt);
        tw.setReply(rp);

        if (source.get("quality_i") != null)
            tw.setQuality(((Number) source.get("quality_i")).intValue());

//        System.out.println("now "+map.get(INREPLY_ID) + " " + doc.field(INREPLY_ID));

        if (source.get(INREPLY_ID) != null) {
//            Long replyId = (Long) doc.field(INREPLY_ID).getValue();

            long replyId = ((Number) source.get(INREPLY_ID)).longValue();
            tw.setInReplyTwitterId(replyId);
        }

        tw.setUrlEntries(Arrays.asList(parseUrlEntries(source)));
        return tw;
    }

    public UrlEntry[] parseUrlEntries(Map<String, Object> source) {
        int urlCount = 0;
        try {
            urlCount = ((Number) source.get("url_i")).intValue();
        } catch (Exception ex) {
        }

        if (urlCount == 0)
            return new UrlEntry[0];

        UrlEntry urls[] = new UrlEntry[urlCount];
        for (int i = 0; i < urls.length; i++) {
            urls[i] = new UrlEntry();
        }

        for (int counter = 0; counter < urls.length; counter++) {
            String str = (String) source.get("url_pos_" + (counter + 1) + "_s");
            String strs[] = (str).split(",");
            urls[counter].setIndex(Integer.parseInt(strs[0]));
            urls[counter].setLastIndex(Integer.parseInt(strs[1]));
        }

        for (int counter = 0; counter < urls.length; counter++) {
            String str = (String) source.get("dest_url_" + (counter + 1) + "_s");
            urls[counter].setResolvedUrl(str);
        }

        for (int counter = 0; counter < urls.length; counter++) {
            String str = (String) source.get("dest_domain_" + (counter + 1) + "_s");
            urls[counter].setResolvedDomain(str);
        }

        for (int counter = 0; counter < urls.length; counter++) {
            String str = (String) source.get("dest_title_" + (counter + 1) + "_s");
            urls[counter].setResolvedTitle(str);
        }
        return urls;
    }

    /**
     * Find a reason for a (trending) topic
     * 1. first query via q=topic
     * 2. retweet count should be high enough (not too high to have no results)
     *    but not too low (avoid noise) -> use facets with more fine grained buckets
     *    and determine the correct filterquery!
     * 3. return created solrquery (added sort 'oldest'!)
     */
    public JetwickQuery createFindOriginQuery(JetwickQuery oldQuery, String tag, int minResults) {
        if (tag.isEmpty())
            return new TweetQuery("");

        try {
            JetwickQuery q;
            if (oldQuery == null)
                q = new TweetQuery(tag);
            else
                q = oldQuery.getCopy().setQuery(tag);

            // copy current state of q into resQuery!
            JetwickQuery resQuery = q.getCopy();

            // more fine grained information about retweets
            Map<String, Integer> orderedFQ = new LinkedHashMap<String, Integer>();
            orderedFQ.put("[16 TO *]", 16);
            orderedFQ.put("[11 TO 15]", 11);
            orderedFQ.put("[6 TO 10]", 6);
            orderedFQ.put("[1 TO 5]", 1);
            orderedFQ.put("0", 0);

            q.setSize(0).addFilterQuery(IS_RT, false);
            for (String facQ : orderedFQ.keySet()) {
                q.addFacetQuery(RT_COUNT, facQ);
            }

            SearchResponse rsp = search(q);
            long results = rsp.getHits().getTotalHits();
            if (results == 0)
                return new TweetQuery(tag);

            resQuery.addFilterQuery(IS_RT, false);
            resQuery.setSort(DATE, "asc");

            long counter = 0;
            for (Entry<String, Integer> entry : orderedFQ.entrySet()) {
                FilterFacet ff = rsp.getFacets().facet(RT_COUNT + ":" + entry.getKey());
//                System.out.println("facets:" + ff.count());
                counter += ff.count();
                if (counter >= minResults) {
                    if (entry.getValue() > 0)
                        resQuery.addFilterQuery(RT_COUNT, "[" + entry.getValue() + " TO *]");
                    break;
                }
            }

            return resQuery;//.attachFacetibility();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    Collection<JUser> search(String str) {
        List<JUser> user = new ArrayList<JUser>();
        search(user, new TweetQuery(str));
        return user;
    }

    public SearchResponse search(JetwickQuery query) {
        return search(new ArrayList(), query);
    }

    public SearchResponse search(Collection<JUser> users, JetwickQuery query) {
        return search(users, query(query));
    }

    public SearchResponse search(Collection<JUser> users, SearchResponse rsp) {
        SearchHit[] docs = rsp.getHits().getHits();
        Map<String, JUser> usersMap = new LinkedHashMap<String, JUser>();
        for (SearchHit sd : docs) {
//            System.out.println(sd.getExplanation().toString());
            JUser u = readDoc(sd.getSource(), sd.getId()).getFromUser();
            JUser uOld = usersMap.get(u.getScreenName());
            if (uOld == null)
                usersMap.put(u.getScreenName(), u);
            else
                uOld.addOwnTweet(u.getOwnTweets().iterator().next());
        }

        users.addAll(usersMap.values());
        return rsp;
    }

    public Collection<JTweet> searchReplies(long id, boolean retweet) {
        try {
            JetwickQuery sq = new TweetQuery(true).addFilterQuery("crt_b", retweet).addFilterQuery(INREPLY_ID, id);
            SearchResponse rsp = query(sq);
            return collectObjects(rsp);
        } catch (Exception ex) {
            logger.error("Error while searchReplies", ex);
            return Collections.EMPTY_SET;
        }
    }    

    Collection<JTweet> update(JTweet tmpTweets) {
        return privateUpdate(Arrays.asList(tmpTweets));
    }

    Collection<JTweet> privateUpdate(Collection<JTweet> tmpTweets) {
        return update(tmpTweets, new Date(0));
    }

    /**
     * Updates a list of tweet's with its replies and retweets.
     *
     * @return a collection of tweets which were updated in solr
     */
    public Collection<JTweet> update(Collection<JTweet> tmpTweets, Date removeUntil) {
        try {
            Map<String, JUser> usersMap = new LinkedHashMap<String, JUser>();
            Map<Long, JTweet> existingTweets = new LinkedHashMap<Long, JTweet>();

            StringBuilder idStr = new StringBuilder();
            int counts = 0;
            // we can add max ~150 tweets per request (otherwise the webcontainer won't handle the long request)
            for (JTweet tw : tmpTweets) {
                if (counts > 0)
                    idStr.append(" OR ");
                counts++;
                idStr.append(tw.getTwitterId());
            }

            // get existing tweets and users                
            JetwickQuery query = new TweetQuery().addFilterQuery("_id", idStr.toString()).setSize(counts);
            SearchResponse rsp = search(query);
            SearchHits docs = rsp.getHits();

            for (SearchHit sd : docs) {
                JTweet tw = readDoc(sd.getSource(), sd.getId());
                existingTweets.put(tw.getTwitterId(), tw);
                JUser u = tw.getFromUser();
                JUser uOld = usersMap.get(u.getScreenName());
                if (uOld == null)
                    usersMap.put(u.getScreenName(), u);
                else
                    uOld.addOwnTweet(u.getOwnTweets().iterator().next());
            }

            Map<Long, JTweet> twMap = new LinkedHashMap<Long, JTweet>();
            for (JTweet solrTweet : tmpTweets) {
                // do not store if too old
                if (!solrTweet.isPersistent() && solrTweet.getCreatedAt().getTime() < removeUntil.getTime())
                    continue;

                JTweet spectw = existingTweets.get(solrTweet.getTwitterId());
                // feed if new or if it should be persistent
                if (spectw == null || solrTweet.isPersistent()) {
                    String name = solrTweet.getFromUser().getScreenName();
                    JUser u = usersMap.get(name);
                    if (u == null) {
                        u = solrTweet.getFromUser();
                        usersMap.put(name, u);
                    }

                    u.addOwnTweet(solrTweet);
                    // tweet does not exist. so store it into the todo map
                    twMap.put(solrTweet.getTwitterId(), solrTweet);
                }
            }

            LinkedHashSet<JTweet> updateTweets = new LinkedHashSet<JTweet>();
            updateTweets.addAll(twMap.values());
            updateTweets.addAll(findReplies(twMap));
            updateTweets.addAll(findRetweets(twMap, usersMap));
            updateTweets.addAll(findDuplicates(twMap));

            // add the additionally fetched tweets to the user but do not add to updateTweets
            // this is a bit expensive ~30-40sec for every update call on a large index!
//            fetchMoreTweets(twMap, usersMap);

            update(updateTweets);

            // We are not receiving the deleted tweets! but do we need to
            // update the tweets where this deleted tweet was a retweet?
            // No. Because "userA: text" and "userB: RT @usera: text" now the second tweet is always AFTER the first!
            deleteUntil(removeUntil);

            // force visibility for next call of update
            refresh();
            return updateTweets;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void update(Collection<JTweet> tweets) {
        update(tweets, true);
    }

    public void update(Collection<JTweet> tweets, boolean refresh) {
        try {
            if (tweets.isEmpty())
                return;

            tweets = new SerialCommandExecutor(tweets).add(
                    new TermCreateCommand()).execute();

            boolean bulk = true;
            if (bulk) {
                bulkUpdate(tweets, getIndexName(), refresh);
            } else {
                for (JTweet tw : tweets) {
                    String id = Long.toString(tw.getTwitterId());
                    feedDoc(id, createDoc(tw));
                }
            }
        } catch (Exception e) {
            logger.error("Exception while updating.", e);
        }
    }

    /**
     * For every user there should be at least 5 tweets to make spam detection
     * more efficient
     */
    public void fetchMoreTweets(Map<Long, JTweet> tweets, final Map<String, JUser> userMap) {
        for (JUser us : userMap.values()) {
            // guarantee 5 tweets to be in the cache
            if (us.getOwnTweets().size() > 4)
                continue;

            //  fetch 10 tweets if less than 5 tweets are in the cache            
            JetwickQuery query = new TweetQuery().addFilterQuery("user", us.getScreenName()).setSize(10);
            try {
                SearchResponse rsp = search(query);
                SearchHits docs = rsp.getHits();
                for (SearchHit sd : docs) {
                    JTweet tw = readDoc(sd.getSource(), sd.getId());
                    JTweet twOld = tweets.get(tw.getTwitterId());
                    if (twOld == null)
                        us.addOwnTweet(tw);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Connect tweets via its retweet text
     *
     * @return all tweets which should be updated
     */
    public Collection<JTweet> findRetweets(Map<Long, JTweet> tweets, final Map<String, JUser> userMap) {
        // 1. check if tweets contains originals which were retweeted -> only done for 'tweets'
        // 2. check if tweets contains retweets -> done for 'tweets' and for tweets in solr

        final Set<JTweet> updatedTweets = new LinkedHashSet<JTweet>();
        Extractor extractor = new Extractor() {

            @Override
            public boolean onNewUser(int index, String user) {
                boolean isRetweet = index >= 3 && text.substring(index - 3, index).equalsIgnoreCase("rt ");
                if (isRetweet) {
                    user = user.toLowerCase();
                    JUser existingU = userMap.get(user);
                    JTweet resTw = null;

                    // check ifRetweetOf against local tweets
                    if (existingU != null)
                        for (JTweet tmp : existingU.getOwnTweets()) {
                            if (tmp.getCreatedAt().getTime() < tweet.getCreatedAt().getTime()
                                    && tweet.isRetweetOf(tmp)) {
                                if (addReplyNoTricks(tmp, tweet)) {
                                    resTw = tmp;
                                    break;
                                }
                            }
                        }

                    // check ifRetweetOf against tweets existing in solr index
                    if (resTw == null)
                        resTw = connectToOrigTweet(tweet, user);

                    if (resTw != null) {
                        updatedTweets.add(resTw);
                        return false;
                    }
                }

                // TODO break loop of Extractor because we only need the first user!
                return true;
            }
        };

        for (JTweet tw : tweets.values()) {
            if (tw.isRetweet())
                extractor.setTweet(tw).run();

        }

        return updatedTweets;
    }

    /**
     * add relation to existing/original tweet
     */
    public JTweet connectToOrigTweet(JTweet tw, String toUserStr) {
        if (tw.isRetweet() && JTweet.isDefaultInReplyId(tw.getInReplyTwitterId())) {            
            // do not connect if retweeted user == user who retweets  
            if (toUserStr.equals(tw.getFromUser().getScreenName()))
                return null;
            
            try {
                // connect retweets to tweets only searchTweetsDays old
                SearchResponse rsp = query(new TweetQuery(JetwickQuery.escapeQuery(tw.extractRTText())).                        
                        addFilterQuery(USER, toUserStr).
                        addFilterQuery(IS_RT, false).                        
                        setSize(10));
                List<JTweet> existingTw = collectObjects(rsp);
                for (JTweet tmp : existingTw) {
                    boolean isRetweet = tw.isRetweetOf(tmp);
                    if (isRetweet) {
                        boolean check = addReplyNoTricks(tmp, tw);
                        if (check)
                            return tmp;
                    }
                }
            } catch (Exception ex) {
                logger.error("couldn't connect tweet to orig tweet:" + ex.getMessage());
            }
        }
        return null;
    }

    /**
     * Connect tweets via its inReplyId
     *
     * @return all tweets which should be updated
     */
    public Collection<JTweet> findReplies(Map<Long, JTweet> tweets) {
        Set<JTweet> updatedTweets = new LinkedHashSet<JTweet>();
        Map<Long, JTweet> replyMap = new LinkedHashMap<Long, JTweet>();
        for (JTweet tw : tweets.values()) {
            if (!JTweet.isDefaultInReplyId(tw.getInReplyTwitterId()))
                replyMap.put(tw.getInReplyTwitterId(), tw);
        }

        Iterator<JTweet> iter = tweets.values().iterator();
        findRepliesInBatch(iter, tweets, replyMap, updatedTweets);

        return updatedTweets;
    }

    protected void findRepliesInBatch(Iterator<JTweet> iter, Map<Long, JTweet> origTweets,
            Map<Long, JTweet> replyIdToTweetMap, Collection<JTweet> updatedTweets) {
        int counter = 0;
        StringBuilder idStr = new StringBuilder();
        StringBuilder replyIdStr = new StringBuilder();
        while (iter.hasNext()) {
            JTweet tw = iter.next();
            JTweet tmp = replyIdToTweetMap.get(tw.getTwitterId());
            if (tmp != null) {
                if (addReplyNoTricks(tw, tmp)) {
                    updatedTweets.add(tw);
                    updatedTweets.add(tmp);
                }
            } else {
                replyIdStr.append(tw.getTwitterId());
                replyIdStr.append(" OR ");
            }

            if (JTweet.isDefaultInReplyId(tw.getInReplyTwitterId()))
                continue;

            tmp = origTweets.get(tw.getInReplyTwitterId());
            if (tmp != null) {
                if (addReplyNoTricks(tmp, tw)) {
                    updatedTweets.add(tw);
                    updatedTweets.add(tmp);
                }
            } else {
                counter++;
                idStr.append(tw.getInReplyTwitterId());
                idStr.append(" OR ");
            }
        }

        try {
            // get tweets which replies our input tweets
            // INREPLY_ID:"tweets[i].id"            
            if (replyIdStr.length() > 0) {
                JetwickQuery query = new TweetQuery().addFilterQuery(INREPLY_ID, replyIdStr.toString()).setSize(origTweets.size());
                findRepliesForOriginalTweets(query, origTweets, updatedTweets);
            }

            // get original tweets where we have replies            
            if (idStr.length() > 0) {
                JetwickQuery query = new TweetQuery().addFilterQuery("_id", idStr.toString()).setSize(counter);
                selectOriginalTweetsWithReplies(query, origTweets.values(), updatedTweets);
            }
        } catch (Exception ex) {
            logger.error("couldn't find replies in a batch query", ex);
        }
    }

    protected void findRepliesForOriginalTweets(JetwickQuery query, Map<Long, JTweet> tweets,
            Collection<JTweet> updatedTweets) {

        Map<Long, JTweet> replyMap = new LinkedHashMap<Long, JTweet>();
        SearchResponse rsp = query(query);
        SearchHits docs = rsp.getHits();

        for (SearchHit sd : docs) {
            JTweet tw = readDoc(sd.getSource(), sd.getId());
            replyMap.put(tw.getTwitterId(), tw);
        }

        for (JTweet inReplSolrTweet : replyMap.values()) {
            if (JTweet.isDefaultInReplyId(inReplSolrTweet.getInReplyTwitterId()))
                continue;
            JTweet origTw = tweets.get(inReplSolrTweet.getInReplyTwitterId());
            if (origTw != null && addReplyNoTricks(origTw, inReplSolrTweet)) {
                updatedTweets.add(origTw);
                updatedTweets.add(inReplSolrTweet);
            }
        }
    }

    protected void selectOriginalTweetsWithReplies(JetwickQuery query, Collection<JTweet> tweets,
            Collection<JTweet> updatedTweets) {

        SearchResponse rsp = query(query);
        SearchHits docs = rsp.getHits();
        Map<Long, JTweet> origMap = new LinkedHashMap<Long, JTweet>();
        for (SearchHit sd : docs) {
            JTweet tw = readDoc(sd.getSource(), sd.getId());
            origMap.put(tw.getTwitterId(), tw);
        }

        if (origMap.size() > 0)
            for (JTweet inReplSolrTweet : tweets) {
                if (JTweet.isDefaultInReplyId(inReplSolrTweet.getInReplyTwitterId()))
                    continue;
                JTweet origTw = origMap.get(inReplSolrTweet.getInReplyTwitterId());
                if (origTw != null && addReplyNoTricks(origTw, inReplSolrTweet)) {
                    updatedTweets.add(origTw);
                    updatedTweets.add(inReplSolrTweet);
                }
            }
    }

    public boolean addReplyNoTricks(JTweet orig, JTweet reply) {
        if (orig.getFromUser().equals(reply.getFromUser()))
            return false;

        try {
            // ensure that reply.user has not already a tweet in orig.replies   
            JetwickQuery q = new TweetQuery().addFilterQuery(INREPLY_ID, orig.getTwitterId()).
                    addFilterQuery("-_id", reply.getTwitterId()).
                    addFilterQuery("user", reply.getFromUser().getScreenName());
            if (query(q).getHits().getTotalHits() > 0)
                return false;

            orig.addReply(reply);
            return true;
        } catch (Exception ex) {
            logger.error("couldn't add reply to:" + orig, ex);
            return false;
        }
    }    

    public JTweet findByTwitterId(Long twitterId) {
        try {
            GetResponse rsp = client.prepareGet(getIndexName(), getIndexType(), Long.toString(twitterId)).
                    execute().actionGet();
            if (rsp.getSource() == null)
                return null;
            return readDoc(rsp.getSource(), rsp.getId());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public Collection<String> getUserChoices(JetwickQuery lastQ, String input) {
        try {
            if (input.length() < 1)
                return Collections.emptyList();

            // NOT context dependent any longer ...                        
            input = input.toLowerCase();
            SearchRequestBuilder srb = client.prepareSearch(getIndexName());
            srb.setQuery(QueryBuilders.fieldQuery(USER, input + "*"));
            List<JUser> users = new ArrayList<JUser>();
            search(users, new TweetQuery(false));
            Set<String> res = new TreeSet<String>();
            for (JUser u : users) {
                if (u.getScreenName().startsWith(input))
                    res.add(u.getScreenName());

                if (res.size() > 9)
                    break;
            }

            return res;
        } catch (Exception ex) {
            logger.error("Error while getUserChoices:" + input + " " + lastQ, ex);
            return Collections.emptyList();
        }
    }

    public Collection<String> getQueryChoices(JetwickQuery lastQ, String input) {
        try {
            if (input.length() < 2)
                return Collections.emptyList();

            String firstPart = "";
            String secPart = input;
            int index = input.lastIndexOf(" ");
            Set<String> existingTerms = new HashSet<String>();
            if (index > 0 && index < input.length()) {
                firstPart = input.substring(0, index);
                secPart = input.substring(index + 1);
                for (String tmp : input.split(" ")) {
                    existingTerms.add(tmp.toLowerCase().trim());
                }
            } else
                existingTerms.add(secPart);

            if (lastQ == null) {
                lastQ = new TweetQuery(firstPart, false);
            } else {
                lastQ = lastQ.getCopy().setQuery(firstPart);
                // remove any date restrictions
                lastQ.removeFilterQueries(DATE);
                lastQ.removeFacets();
            }

            SearchRequestBuilder srb = client.prepareSearch(getIndexName());
            lastQ.initRequestBuilder(srb);

            TermsFacetBuilder tfb = FacetBuilders.termsFacet(TAG).field(TAG);
            if (!secPart.trim().isEmpty())
                tfb.regex(secPart + ".*", Pattern.DOTALL);

            srb.addFacet(tfb);
            SearchResponse rsp = search(new ArrayList<JUser>(), srb.execute().actionGet());
            Set<String> res = new TreeSet<String>();
            TermsFacet tf = rsp.facets().facet(TAG);
            if (tf != null) {
                for (TermsFacet.Entry cnt : tf.entries()) {
                    String lowerSugg = cnt.getTerm().toLowerCase();
                    if (existingTerms.contains(lowerSugg))
                        continue;

                    if (lowerSugg.startsWith(secPart)) {
                        if (firstPart.isEmpty())
                            res.add(cnt.getTerm());
                        else
                            res.add(firstPart + " " + cnt.getTerm());
                    }

                    if (res.size() > 9)
                        break;
                }
            }

            return res;
        } catch (Exception ex) {
            logger.error("Error while getQueryChoices:" + input + " " + lastQ + " -> Error:" + ex.getMessage());
            return Collections.emptyList();
        }
    }

    JUser findByUserName(String uName) {
        try {
            List<JUser> list = new ArrayList<JUser>();
            // get all tweets of the user so set rows large ...            
            search(list, new TweetQuery().addFilterQuery("user", uName.toLowerCase()).setSize(10));

            if (list.isEmpty())
                return null;

            return list.get(0);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<JTweet> searchTweets(JetwickQuery q) {
        try {
            return collectObjects(search(q));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public String getTweetsAsString(JetwickQuery q) {
        StringBuilder sb = new StringBuilder();
        List<JTweet> tweets = searchTweets(q);
        String separator = ",";
        for (JTweet tweet : tweets) {
            sb.append(Helper.toTwitterHref(tweet.getFromUser().getScreenName(), tweet.getTwitterId()));
            sb.append(separator);
            sb.append(tweet.getRetweetCount());
            sb.append(separator);
            sb.append(tweet.getText().replaceAll("\n", " "));
            sb.append("\n");
        }

        return sb.toString();
    }

    public Collection<JTweet> searchAds(String query) {
        query = query.trim();
        if (query.isEmpty())
            return Collections.EMPTY_LIST;

        Collection<JUser> users = new LinkedHashSet<JUser>();
        MyDate now = new MyDate();
        // NOW/DAY-3DAYS
        MyDate from = new MyDate().castToDay().minusDays(2);
        search(users, new TweetQuery(query, false).addFilterQuery(TWEET_TEXT, "#jetwick").
                //                addFilterQuery(RT_COUNT + ":[1 TO *]").
                addFilterQuery(QUALITY, "[90 TO 100]").
                addFilterQuery(IS_RT, false).
                addFilterQuery(DATE, "[" + from.toLocalString() + " TO " + now.toLocalString() + "]").
                setSort(RT_COUNT, "desc"));

        Set<JTweet> res = new LinkedHashSet<JTweet>();
        for (JUser u : users) {
            if (u.getOwnTweets().size() > 0)
                res.add(u.getOwnTweets().iterator().next());
        }
        return res;
    }

    public Collection<JTweet> findDuplicates(Map<Long, JTweet> tweets) {
        final Set<JTweet> updatedTweets = new LinkedHashSet<JTweet>();
        TermCreateCommand termCommand = new TermCreateCommand();
        double JACC_BORDER = 0.7;
        for (JTweet currentTweet : tweets.values()) {
            if (currentTweet.isRetweet())
                continue;

            JetwickQuery reqBuilder = new SimilarQuery(currentTweet, false).addLatestDateFilter(24);
            if (currentTweet.getTextTerms().size() < 3)
                continue;

            int dups = 0;
            try {
                // find dups in index
                for (JTweet simTweet : collectObjects(search(reqBuilder))) {
                    if (simTweet.getTwitterId().equals(currentTweet.getTwitterId()))
                        continue;

                    termCommand.calcTermsWithoutNoise(simTweet);
                    if (TermCreateCommand.calcJaccardIndex(currentTweet.getTextTerms(), simTweet.getTextTerms())
                            >= JACC_BORDER) {
                        currentTweet.addDuplicate(simTweet.getTwitterId());
                        dups++;
                    }
                }
            } catch (Exception ex) {
                logger.error("Error while findDuplicate query execution", ex);
            }

            // find dups in tweets map
            for (JTweet simTweet : tweets.values()) {
                if (simTweet.getTwitterId().equals(currentTweet.getTwitterId()) || simTweet.isRetweet())
                    continue;

                if (currentTweet.getCreatedAt().getTime() < simTweet.getCreatedAt().getTime())
                    continue;

                termCommand.calcTermsWithoutNoise(simTweet);
                if (TermCreateCommand.calcJaccardIndex(currentTweet.getTextTerms(), simTweet.getTextTerms())
                        >= JACC_BORDER) {
                    currentTweet.addDuplicate(simTweet.getTwitterId());
                    dups++;
                }
            }

//            tw.setDuplicates(dups);
        }

        return updatedTweets;
    }

    public SearchResponse updateSavedSearches(final Collection<SavedSearch> savedSearches) {
        JetwickQuery q = new TweetQuery() {

            @Override
            protected void processFacetQueries(SearchRequestBuilder srb) {
                for (SavedSearch ss : savedSearches) {
                    srb.addFacet(FacetBuilders.queryFacet(SAVED_SEARCHES + "_" + ss.getId(),
                            QueryBuilders.queryString(ss.calcFacetQuery()).useDisMax(true).defaultOperator(QueryStringQueryBuilder.Operator.AND).
                            field(ElasticTweetSearch.TWEET_TEXT).field("dest_title_t").field("user", 0)));
                }
            }
        }.setFrom(0).setSize(0);

        return search(q);
    }
}
