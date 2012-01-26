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
import de.jetwick.util.MyDate;
import org.elasticsearch.search.facet.filter.FilterFacet;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.elasticsearch.search.SearchHits;
import de.jetwick.config.Configuration;
import de.jetwick.data.UrlEntry;
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
import de.jetwick.tw.Extractor;
import de.jetwick.tw.cmd.SerialCommandExecutor;
import de.jetwick.tw.cmd.TermCreateCommand;
import de.jetwick.util.AnyExecutor;
import de.jetwick.util.Helper;
import de.jetwick.util.MapEntry;
import de.jetwick.util.StopWatch;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.GeoDistanceFilterBuilder;
import org.elasticsearch.index.query.NotFilterBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.index.query.RangeFilterBuilder;
import org.elasticsearch.index.search.geo.GeoDistance;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.facet.Facet;
import org.elasticsearch.search.facet.FacetBuilders;
import org.elasticsearch.search.facet.Facets;
import org.elasticsearch.search.facet.query.QueryFacet;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.elasticsearch.search.facet.terms.TermsFacetBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides search functionality via elasticsearch.
 * 
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class ElasticTweetSearch extends AbstractElasticSearchQueueEnabled<JTweet> {

    public static final long OLDEST_DT_IN_MILLIS = 4 * 24 * MyDate.ONE_HOUR;
    public static final String TITLE = "dest_title_t";
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
    public static final String LANG = "lang";
    public static final String URL_COUNT = "url_i";
    public static final String FIRST_URL_TITLE = "dest_title_1_s";
    public static final String USER = "user";
    public static final String FILTER_NO_DUPS = DUP_COUNT + ":0";
    public static final String FILTER_ONLY_DUPS = DUP_COUNT + ":[1 TO *]";
    public static final String FILTER_NO_URL_ENTRY = URL_COUNT + ":0";
    public static final String FILTER_URL_ENTRY = URL_COUNT + ":[1 TO *]";
    public static final String FILTER_NO_SPAM = QUALITY + ":[" + (JTweet.QUAL_SPAM + 1) + " TO *]";
    public static final String FILTER_SPAM = QUALITY + ":[* TO " + JTweet.QUAL_SPAM + "]";
    public static final String RELEVANCE = "relevance";
    public static final String _ID = "_id_";
    private String indexName = "twindex";
    private List<AnyExecutor<JTweet>> commitListener = new ArrayList<AnyExecutor<JTweet>>(1);
    private Logger logger = LoggerFactory.getLogger(getClass());

    public ElasticTweetSearch() {
    }

    public ElasticTweetSearch(Configuration config) {
        this(config.getTweetSearchUrl());
    }

    public ElasticTweetSearch(String url) {
        super(url);
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
        logger.info("Deleting tweets older than " + removeUntil);
        NotFilterBuilder notPersistentFilter = FilterBuilders.notFilter(FilterBuilders.existsFilter(UPDATE_DT));
        FilterBuilder fewRetweetsFilter = FilterBuilders.rangeFilter(RT_COUNT).lt(100).includeUpper(false);
        RangeFilterBuilder tooOldFilter = FilterBuilders.rangeFilter(DATE);
        tooOldFilter.lte(removeUntil);
        FilterBuilder filter = FilterBuilders.andFilter(tooOldFilter,
                notPersistentFilter, fewRetweetsFilter);

        client.prepareDeleteByQuery(getIndexName()).setTypes(getIndexType()).
                setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), filter)).
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

    @Override
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

        b.field("geo", tw.getLat() + "," + tw.getLon());

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
            counter++;
            b.field("orig_url_" + counter + "_s", urlEntry.getOriginalUrl(tw));
            b.field("url_pos_" + counter + "_s", urlEntry.getIndex() + "," + urlEntry.getLastIndex());
            b.field("dest_url_" + counter + "_s", urlEntry.getResolvedUrl());
            if (!Helper.isEmpty(urlEntry.getResolvedDomain()))
                b.field("dest_domain_" + counter + "_s", urlEntry.getResolvedDomain());

            if (!Helper.isEmpty(urlEntry.getResolvedDomain()))
                b.field("dest_title_" + counter + "_s", urlEntry.getResolvedTitle());

            if (counter == 1)
                b.field(TITLE, urlEntry.getResolvedTitle());

            if (counter >= 3)
                break;
        }

        b.field(URL_COUNT, counter);
        b.field(DUP_COUNT, tw.getDuplicates().size());
        b.field(LANG, tw.getLanguage());
        b.field(QUALITY, tw.getQuality());
        b.field("repl_i", tw.getReplyCount());
        b.field(RT_COUNT, tw.getRetweetCount());

        b.endObject();
        return b;
    }

    @Override
    public JTweet readDoc(String idAsStr, long version, Map<String, Object> source) {
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
        tw.setVersion(version);

        String p = (String) source.get("geo");
        if (p != null)
            try {
                String[] strs = p.split(",");
                double lat = Double.parseDouble(strs[0]);
                double lon = Double.parseDouble(strs[1]);
                tw.setGeoLocation(lat, lon);
            } catch (Exception ex) {
            }

        tw.setCreatedAt(Helper.toDateNoNPE((String) source.get(DATE)));
        tw.setUpdatedAt(Helper.toDateNoNPE((String) source.get(UPDATE_DT)));
        int rt = ((Number) source.get(RT_COUNT)).intValue();
        int rp = ((Number) source.get("repl_i")).intValue();
        tw.setRetweetCount(rt);
        tw.setReplyCount(rp);

        if (source.get(QUALITY) != null)
            tw.setQuality(((Number) source.get(QUALITY)).intValue());

        tw.setLanguage((String) source.get(LANG));

        if (source.get(INREPLY_ID) != null) {
            long replyId = ((Number) source.get(INREPLY_ID)).longValue();
            tw.setInReplyTwitterId(replyId);
        }

        tw.setUrlEntries(Arrays.asList(parseUrlEntries(source)));
        return tw;
    }

    public UrlEntry[] parseUrlEntries(Map<String, Object> source) {
        int urlCount = 0;
        try {
            urlCount = ((Number) source.get(URL_COUNT)).intValue();
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

            SearchResponse rsp = query(q);
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
        query(user, new TweetQuery(str));
        return user;
    }

    @Override
    public SearchResponse query(JetwickQuery query) {
        return query(new ArrayList(), query);
    }

    public SearchResponse query(Collection<JUser> users, JetwickQuery query) {
        return query(users, super.query(query));
    }

    public SearchResponse query(Collection<JUser> users, SearchResponse rsp) {
        SearchHit[] docs = rsp.getHits().getHits();
        Map<String, JUser> usersMap = new LinkedHashMap<String, JUser>();
        for (SearchHit sd : docs) {
//            System.out.println(sd.getExplanation().toString());
            JUser u = readDoc(sd.getId(), sd.getVersion(), sd.getSource()).getFromUser();
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

    void testUpdate(JTweet tmpTweets) {
        queueObject(tmpTweets);
        forceEmptyQueueAndRefresh();
    }

    void testUpdate(Collection<JTweet> tmpTweets) {
        queueObjects(tmpTweets);
        forceEmptyQueueAndRefresh();
    }

    /**
     * Updates a list of tweet's with its replies and retweets.
     *
     * @param tmpTweets
     * @param removeUntil the date until all old tweet should be removed
     * @param performDelete avoid too frequent removing!     
     * @return updated tweets
     */
    public Collection<JTweet> update(Collection<JTweet> tmpTweets, Date removeUntil, boolean performDelete) {
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
            JetwickQuery query = new TweetQuery().addFilterQuery(_ID + getIndexType(), idStr.toString()).setSize(counts);
            SearchResponse rsp = query(query);
            SearchHits docs = rsp.getHits();

            for (SearchHit sd : docs) {
                JTweet tw = readDoc(sd.getId(), sd.getVersion(), sd.getSource());
                existingTweets.put(tw.getTwitterId(), tw);
                JUser u = tw.getFromUser();
                JUser uOld = usersMap.get(u.getScreenName());
                if (uOld == null)
                    usersMap.put(u.getScreenName(), u);
                else
                    uOld.addOwnTweet(u.getOwnTweets().iterator().next());
            }

            // Avoid storing existing tweets again
            Map<Long, JTweet> twMap = new LinkedHashMap<Long, JTweet>();
            for (JTweet tmpTweet : tmpTweets) {
                // do not store if too old
                if (!tmpTweet.isPersistent() && tmpTweet.getCreatedAt().getTime() < removeUntil.getTime())
                    continue;

                JTweet exTw = existingTweets.get(tmpTweet.getTwitterId());
                // feed if new or if it should be persistent
                if (exTw == null || tmpTweet.isPersistent()) {
                    String name = tmpTweet.getFromUser().getScreenName();
                    JUser u = usersMap.get(name);
                    if (u == null) {
                        u = tmpTweet.getFromUser();
                        usersMap.put(name, u);
                    }

                    u.addOwnTweet(tmpTweet);
                    // tweet does not exist. so store it into the todo map
                    twMap.put(tmpTweet.getTwitterId(), tmpTweet);

                    // overwrite existing tweets if persistent BUT update version
                    if (tmpTweet.isPersistent() && exTw != null)
                        tmpTweet.setVersion(exTw.getVersion());
                }
            }

            LinkedHashSet<JTweet> updateTweets = new LinkedHashSet<JTweet>(twMap.values());
            updateTweets.addAll(findReplies(twMap));
            updateTweets.addAll(findRetweets(twMap, usersMap));
            updateTweets.addAll(findDuplicates(twMap));

            // add the additionally fetched tweets to the user but do not add to updateTweets
            // this is a bit expensive ~30-40sec for every store call on a large index!
//            fetchMoreTweets(twMap, usersMap);            
            store(updateTweets, false);

            // We are not receiving the deleted tweets! but do we need to
            // store the tweets where this deleted tweet was a retweet?
            // No. Because "userA: text" and "userB: RT @usera: text" now the second tweet is always AFTER the first!
            if (performDelete) {
                logger.info("Deleting tweets older than " + removeUntil);
                deleteUntil(removeUntil);
            }

            return updateTweets;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    private StopWatch sw1 = new StopWatch();
    private StopWatch sw2 = new StopWatch();
    private StopWatch sw3 = new StopWatch();
    private StopWatch sw4 = new StopWatch();

    void store(Collection<JTweet> tweets, boolean refresh) {
        try {
            if (tweets.isEmpty())
                return;

            tweets = new SerialCommandExecutor(tweets).add(
                    new TermCreateCommand().setSw1(sw1).setSw2(sw2).setSw3(sw3).setSw4(sw4)).execute();

            List<JTweet> list = new ArrayList<JTweet>(tweets);
            Collection<Integer> failedArticleIndices = bulkUpdate(list, getIndexName());
            for (Integer integ : failedArticleIndices) {
                JTweet tw = list.get(integ);
                tw.setUpdateCount(tw.getUpdateCount() + 1);
                if (tw.getUpdateCount() > 10)
                    logger.warn("PROBLEM: skipped tweet. it failed " + tw.getUpdateCount() + " times:" + tw);
                else
                    queueFailedObject(tw);
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
                SearchResponse rsp = query(query);
                SearchHits docs = rsp.getHits();
                for (SearchHit sd : docs) {
                    JTweet tw = readDoc(sd.getId(), sd.getVersion(), sd.getSource());
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
                    JUser existingUser = userMap.get(user);
                    JTweet resTw = null;

                    // check ifRetweetOf against local tweets
                    if (existingUser != null)
                        for (JTweet tmp : existingUser.getOwnTweets()) {
                            if (tmp.getCreatedAt().getTime() < tweet.getCreatedAt().getTime()
                                    && tweet.isRetweetOf(tmp)) {
                                if (addReplyNoTricks(tmp, tweet)) {
                                    resTw = tmp;
                                    break;
                                }
                            }
                        }

                    // check ifRetweetOf against tweets existing in index
                    if (resTw == null)
                        resTw = connectToOrigTweet(tweet, user);

                    if (resTw != null) {
                        updatedTweets.add(resTw);
                        return false;
                    }
                }

                // break loop of Extractor because we only need the first user!
                return true;
            }
        };

        for (JTweet tw : tweets.values()) {
            if (tw.isRetweet()) {
                extractor.setTweet(tw).run();
            }
        }
        return updatedTweets;
    }

    /**
     * add relation to existing/original tweet
     */
    public JTweet connectToOrigTweet(JTweet tw, String toUserStr) {
        if (tw.isRetweet()) {
            // do not connect if retweeted user == user who retweets  
            if (toUserStr.equals(tw.getFromUser().getScreenName()))
                return null;

            try {
                // connect retweets to tweets only searchTweetsDays old
                SearchResponse rsp = query(new TweetQuery(JetwickQuery.escapeQuery(tw.extractRTText())).addFilterQuery(USER, toUserStr).
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
            if (!JTweet.isDefaultInReplyId(tw.getInReplyTwitterId()) && !tw.isRetweet())
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
                if (replyIdStr.length() > 0)
                    replyIdStr.append(" OR ");

                replyIdStr.append(tw.getTwitterId());
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
                if (idStr.length() > 0)
                    idStr.append(" OR ");

                idStr.append(tw.getInReplyTwitterId());
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
                JetwickQuery query = new TweetQuery().addFilterQuery(_ID + getIndexType(), idStr.toString()).setSize(counter);
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
            JTweet tw = readDoc(sd.getId(), sd.getVersion(), sd.getSource());
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
            JTweet tw = readDoc(sd.getId(), sd.getVersion(), sd.getSource());
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
                    addFilterQuery("-" + _ID + getIndexType(), reply.getTwitterId()).
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

    /**
     * @param exec will be called directly after the tweets have beed feeded 
     * into the index. WARNING: it is not guarantueed that the tweets are 
     * already searchable as every index has a realtime latency
     */
    public void addListener(AnyExecutor<JTweet> exec) {
        if (!commitListener.contains(exec))
            commitListener.add(exec);
    }

    public void removeListener(AnyExecutor<JTweet> exec) {
        commitListener.remove(exec);
    }

    public JTweet findByTwitterId(Long twitterId) {
        try {
            GetResponse rsp = client.prepareGet(getIndexName(), getIndexType(), Long.toString(twitterId)).
                    execute().actionGet();
            if (rsp.getSource() == null)
                return null;
            return readDoc(rsp.getId(), rsp.getVersion(), rsp.getSource());
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
            SearchRequestBuilder srb = createSearchBuilder();
            srb.setQuery(QueryBuilders.fieldQuery(USER, input + "*"));
            List<JUser> users = new ArrayList<JUser>();
            query(users, new TweetQuery(false));
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

            SearchRequestBuilder srb = createSearchBuilder();
            lastQ.initRequestBuilder(srb);

            TermsFacetBuilder tfb = FacetBuilders.termsFacet(TAG).field(TAG);
            if (!secPart.trim().isEmpty())
                tfb.regex(secPart + ".*", Pattern.DOTALL);

            srb.addFacet(tfb);
            SearchResponse rsp = query(new ArrayList<JUser>(), srb.execute().actionGet());
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
            query(list, new TweetQuery().addFilterQuery("user", uName.toLowerCase()).setSize(10));

            if (list.isEmpty())
                return null;

            return list.get(0);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<JTweet> searchTweets(JetwickQuery q) {
        try {
            return collectObjects(query(q));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public Collection<String> searchTrends(JetwickQuery q, int limit) {
        try {
            q.addFacetField(TAG);
            SearchResponse rsp = query(q);
            Facets facets = rsp.facets();
            if (facets == null)
                return Collections.emptyList();

            Set<String> set = new LinkedHashSet<String>();
            for (Facet facet : facets.facets()) {
                if (facet instanceof TermsFacet) {
                    TermsFacet ff = (TermsFacet) facet;
                    for (TermsFacet.Entry e : ff.entries()) {
                        if (e.count() > limit)
                            set.add(e.getTerm());
                    }
                }
            }
            return set;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public String getTweetsAsString(JetwickQuery q, String separator) {
        StringBuilder sb = new StringBuilder();
        List<JTweet> tmpTweets = searchTweets(q);
        for (JTweet tweet : tmpTweets) {
            sb.append(Helper.toTwitterHref(tweet.getFromUser().getScreenName(), tweet.getTwitterId()));
            sb.append(separator);
            sb.append(tweet.getRetweetCount());
            sb.append(separator);
            sb.append(tweet.getText().replaceAll("\n", " "));
            sb.append("\n");
        }

        return sb.toString();
    }

    public Collection<JTweet> findDuplicates(Map<Long, JTweet> tweets) {
        final Set<JTweet> updatedTweets = new LinkedHashSet<JTweet>();
        TermCreateCommand termCommand = new TermCreateCommand();
        double JACC_BORDER = 0.7;
        for (JTweet currentTweet : tweets.values()) {
            if (currentTweet.isRetweet())
                continue;

            JetwickQuery reqBuilder = new SimilarTweetQuery(currentTweet, false).addLatestDateFilter(24);
            if (currentTweet.getTextTerms().size() < 3)
                continue;

            int dups = 0;
            try {
                // find dups in index
                for (JTweet simTweet : collectObjects(query(reqBuilder))) {
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
                            createQSQB(ss.calcFacetQuery())));
                }
            }
        }.setFrom(0).setSize(0);

        return query(q);
    }

    QueryStringQueryBuilder createQSQB(String qStr) {
        return QueryBuilders.queryString(qStr).
                useDisMax(true).defaultOperator(QueryStringQueryBuilder.Operator.AND).
                field(ElasticTweetSearch.TWEET_TEXT).field(TITLE).field(USER, 0);
    }

    /**
     * @return a collection where the first string indicates the filter key
     * which should be removed to increase the number of results.
     * Of course this can be only a heuristic sorting against the count of each
     * filter query
     */
    public Collection<String> suggestRemoval(final JetwickQuery q) {
        SearchResponse rsp = query(new TweetQuery() {

            @Override
            protected void processFacetQueries(SearchRequestBuilder srb) {
                int counter = 0;
                String initFacetQ = SavedSearch.buildInitialFacetQuery(q.getQuery());
                for (Entry<String, Object> e : q.getFilterQueries()) {
                    String facetQuery = initFacetQ + " AND " + e.getKey() + ":" + e.getValue().toString();
                    srb.addFacet(FacetBuilders.queryFacet("ss_" + counter, createQSQB(facetQuery)));
                    counter++;
                }
            }
        });

        List<Entry<String, Long>> list = new ArrayList<Entry<String, Long>>();
        int counter = 0;
        boolean forceDateSuggestion = false;
        for (Entry<String, Object> e : q.getFilterQueries()) {
            QueryFacet qf = (QueryFacet) rsp.facets().facet("ss_" + counter);
            list.add(new MapEntry<String, Long>(e.getKey(), qf.count()));
            counter++;
            if (DATE.equals(e.getKey())) {
                try {
                    String str = (String) e.getValue();
                    int index = str.indexOf(" ");
                    // get from date
                    if (index > 0)
                        str = str.substring(1, index);
                    if ((new Date().getTime() - Helper.toDate(str).getTime()) / MyDate.ONE_DAY <= 1)
                        forceDateSuggestion = true;
                } catch (Exception ex) {
                }
            }
        }

        Helper.sortInplaceLongReverse(list);
        Collection<String> res = new LinkedHashSet<String>();
        for (Entry<String, Long> e : list) {
            if (e.getValue() > 0)
                res.add(e.getKey());
        }

        if (forceDateSuggestion)
            res.add(DATE);

        return res;
    }

    public List<JTweet> searchGeo(double lat, double lon, double length) {
        GeoDistanceFilterBuilder geoFilter = FilterBuilders.geoDistanceFilter("geo").
                lat(lat).lon(lon).distance(length, DistanceUnit.KILOMETERS).geoDistance(GeoDistance.PLANE);
        SearchRequestBuilder srb = createSearchBuilder();
        srb.setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), geoFilter));
        return collectObjects(srb.execute().actionGet());
    }

    public Set<String> getQuerySuggestions(JetwickQuery query, SearchResponse rsp, long hits) {
        TermsFacet tags = (TermsFacet) rsp.facets().facet(ElasticTweetSearch.TAG);

        if (tags == null)
            return Collections.emptySet();
        Set<String> tmp = new LinkedHashSet<String>();
        for (TermsFacet.Entry e : tags.entries()) {
//            logger.info(e.term() + " " + e.count() + " " + hits);
            if (e.count() > hits / 10000.0 + 1) {
                boolean contains = false;
                for (String tmpTerm : tmp) {
                    if (e.term().contains(tmpTerm) || tmpTerm.contains(e.term()))
                        contains = true;
                }
                if (!contains)
                    tmp.add(e.term());
            }
        }

        Set<String> qSuggestions = new LinkedHashSet<String>();
        int counter = 0;
        for (String t : tmp) {
            if (query.getQuery().contains(t) || t.contains(query.getQuery()))
                continue;

            qSuggestions.add(query.getQuery() + " " + t);
            qSuggestions.add(query.getQuery() + " -" + t);

            if (++counter > 2)
                break;
        }

        if (qSuggestions.size() > 0)
            qSuggestions.add(query.getQuery());
        return qSuggestions;
    }

    public GetResponse findByTwitterIdRaw(Long twitterId) {
        return client.prepareGet(getIndexName(), getIndexType(), Long.toString(twitterId)).
                execute().actionGet();
    }

    SearchRequestBuilder createSearchBuilder(String indexName) {
        return client.prepareSearch(indexName).setTypes(getIndexType()).setVersion(true);
    }
    private Map<String, JTweet> tweets = new LinkedHashMap<String, JTweet>(100);
    private StopWatch sw = new StopWatch();
    private int tweetCounter = 0;
    private AtomicInteger feededTweets = new AtomicInteger(0);
    private Collection<JTweet> protectedTweets = new LinkedHashSet<JTweet>();
    private int feedCounter = 0;

    public int getFeededTweets() {
        return feededTweets.get();
    }

    @Override
    public void innerAdd(JTweet tw) {
        // do not add protected tweets and add them only once
        if (!tw.isProtected()) {
            JTweet existingTweet = tweets.put(tw.getId(), tw);            
            if (existingTweet != null) {
                existingTweet.updateFrom(tw);                
                tweets.put(existingTweet.getId(), existingTweet);
            }
        } else
            protectedTweets.add(tw);
    }

    @Override
    public void innerThreadMethod() throws InterruptedException {
        sw.start();
        boolean delete = testing || feedCounter++ % 400 == 0;
        // tweets can be updated from another thread (failed tweets)
        Collection<JTweet> res = update(new ArrayList<JTweet>(tweets.values()), createRemoveOlderThan().toDate(), delete);
        tweetCounter += res.size();
        feededTweets.set(res.size());
        sw.stop();
        if (tweetCounter > getBatchSize()) {
            logger.info("Updated " + tweetCounter + " tweets "
                    + tweetCounter / sw.getSeconds() + " per sec. Remaining:"
                    + getTodoObjects().size());
            logger.info("sw1:" + sw1.getSeconds() + "\t sw2:" + sw2.getSeconds()
                    + "\t sw3:" + sw3.getSeconds() + "\t sw4:" + sw4.getSeconds());
            tweetCounter = 0;
            sw = new StopWatch();
        }

        res.addAll(protectedTweets);
        for (AnyExecutor<JTweet> exec : commitListener) {
            for (JTweet tw : res) {
                exec.execute(tw);
            }
        }

        protectedTweets.clear();
        tweets.clear();
    }

    /**
     * Warning this is not real time!
     */
    public List<JTweet> findByUrl(String url) {
        SearchRequestBuilder srb = createSearchBuilder();
        srb.setSearchType(SearchType.QUERY_AND_FETCH);
        srb.setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
                FilterBuilders.orFilter(
                    FilterBuilders.termFilter("dest_url_1_s", url),
                    FilterBuilders.termFilter("orig_url_1_s", url))));
        return collectObjects(srb.execute().actionGet());
    }

    public boolean tooOld(Date dt) {
        return dt.getTime() < System.currentTimeMillis()
                - ElasticTweetSearch.OLDEST_DT_IN_MILLIS;
    }

    @Override
    public void deleteAll(String indexName, String indexType) {
        protectedTweets.clear();
        tweets.clear();
        super.deleteAll(indexName, indexType);
    }        
}
