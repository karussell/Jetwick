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

import org.elasticsearch.common.unit.TimeValue;
import java.util.regex.Pattern;
import de.jetwick.solr.SavedSearch;
import org.elasticsearch.client.Requests;
import org.elasticsearch.index.query.xcontent.NotFilterBuilder;
import de.jetwick.util.MyDate;
import org.elasticsearch.search.facet.filter.FilterFacet;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.elasticsearch.index.query.xcontent.QueryBuilders;
import org.elasticsearch.search.SearchHits;
import de.jetwick.config.Configuration;
import de.jetwick.data.UrlEntry;
import de.jetwick.solr.JetwickQuery;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
import de.jetwick.solr.TweetQuery;
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
import org.apache.solr.client.solrj.SolrQuery;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.action.bulk.BulkRequestBuilder;
import org.elasticsearch.common.StopWatch;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.xcontent.FilterBuilders;
import org.elasticsearch.index.query.xcontent.RangeFilterBuilder;
import org.elasticsearch.index.query.xcontent.XContentFilterBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.facet.FacetBuilders;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.elasticsearch.search.facet.terms.TermsFacetBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides search functionality via elasticsearch.
 * 
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class ElasticTweetSearch extends AbstractElasticSearch {

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
    public static final String KEY_USER = "user:";
    public static final String USER = "user";
    public static final String FILTER_IS_NOT_RT = IS_RT + ":\"false\"";
    public static final String FILTER_NO_DUPS = DUP_COUNT + ":0";
    public static final String FILTER_ONLY_DUPS = DUP_COUNT + ":[1 TO *]";
    public static final String FILTER_NO_URL_ENTRY = URL_COUNT + ":0";
    public static final String FILTER_URL_ENTRY = URL_COUNT + ":[1 TO *]";
    public static final String FILTER_NO_SPAM = QUALITY + ":[" + (SolrTweet.QUAL_SPAM + 1) + " TO *]";
    public static final String FILTER_SPAM = QUALITY + ":[* TO " + SolrTweet.QUAL_SPAM + "]";
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

    public void delete(Collection<SolrTweet> tws) {
        if (tws.isEmpty())
            return;

        try {
            for (SolrTweet tw : tws) {
                deleteById(Long.toString(tw.getTwitterId()));
            }

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * @deprecated use new ElasticSearch requestBuilder to pass into the query method
     */
    SearchResponse queryOldSolr(SolrQuery query) {
        SearchRequestBuilder srb = client.prepareSearch(getIndexName());
        new Solr2ElasticTweet().createElasticQuery(query, srb);
        SearchResponse response = srb.execute().actionGet();
        return response;
    }

    public TweetESQuery createQuery() {
        return createQuery(getIndexName());
    }

    public TweetESQuery createQuery(String indexName) {
        // TODO use multiple indices here
        return new TweetESQuery(client.prepareSearch(indexName));
    }

    public SearchResponse query(TweetESQuery query) {
        return query.getRequestBuilder().execute().actionGet();
    }

    public XContentBuilder createDoc(SolrTweet tw) throws IOException {
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

        if (!SolrTweet.isDefaultInReplyId(tw.getInReplyTwitterId()))
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

    public SolrTweet readDoc(Map<String, Object> source, String idAsStr) {
        // if we use in mapping: "_source" : {"enabled" : false}
        // we need to include all fields in query to use doc.getFields() 
        // instead of doc.getSource()

        String name = (String) source.get(USER);
        String text = (String) source.get(TWEET_TEXT);
        if (text == null || name == null || idAsStr == null) {
            logger.error("Null tweet text or id!!!??" + idAsStr + " " + name + " " + text);
            return new SolrTweet(-1L, "", new SolrUser(""));
        }

        SolrUser user = new SolrUser(name);
        user.setLocation((String) source.get("loc"));
        user.setProfileImageUrl((String) source.get("iconUrl"));

        long id = Long.parseLong(idAsStr);
        SolrTweet tw = new SolrTweet(id, text, user);

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
    public SolrQuery createFindOriginQuery(SolrQuery oldQuery, String tag, int minResults) {
        if (tag.isEmpty())
            return new JetwickQuery("");

        try {
            SolrQuery q;
            if (oldQuery == null) {
                q = new SolrQuery(tag);
            } else {
                // TODO remove 8 hour filter? or remove all date filter?
                // q.removeFilterQuery(FILTER_ENTRY_LATEST_DT);
                q = oldQuery.getCopy().setQuery(tag);
            }

            // copy current state of q into resQuery!
            SolrQuery resQuery = q.getCopy();

            // more fine grained information about retweets
            Map<String, Integer> orderedFQ = new LinkedHashMap<String, Integer>();
            orderedFQ.put(RT_COUNT + ":[16 TO *]", 16);
            orderedFQ.put(RT_COUNT + ":[11 TO 15]", 11);
            orderedFQ.put(RT_COUNT + ":[6 TO 10]", 6);
            orderedFQ.put(RT_COUNT + ":[1 TO 5]", 1);
            orderedFQ.put(RT_COUNT + ":0", 0);

            q.setFacet(true).setRows(0).addFilterQuery(IS_RT + ":\"false\"");
            for (String facQ : orderedFQ.keySet()) {
                q.addFacetQuery(facQ);
            }

            SearchResponse rsp = search(q);
            long results = rsp.getHits().getTotalHits();
            if (results == 0)
                return new JetwickQuery(tag);

            resQuery.addFilterQuery(IS_RT + ":\"false\"");
            TweetQuery.setSort(resQuery, "dt asc");

            long counter = 0;
            for (Entry<String, Integer> entry : orderedFQ.entrySet()) {
                FilterFacet ff = rsp.getFacets().facet(entry.getKey());
                counter += ff.count();
                if (counter >= minResults) {
                    if (entry.getValue() > 0)
                        resQuery.addFilterQuery(RT_COUNT + ":[" + entry.getValue() + " TO *]");
                    break;
                }
            }

            return TweetQuery.attachFacetibility(resQuery);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    Collection<SolrUser> search(String str) {
        List<SolrUser> user = new ArrayList<SolrUser>();
        search(user, new TweetQuery(str));
        return user;
    }

    /**
     * @deprecated use new ElasticSearch requestBuilder to pass into search method
     */
    public SearchResponse search(SolrQuery query) {
        return search(new ArrayList(), query);
    }

    /**
     * @deprecated use new ElasticSearch requestBuilder to pass into search method
     */
    public SearchResponse search(Collection<SolrUser> users, SolrQuery query) {
        SearchResponse rsp = queryOldSolr(query);
        SearchHit[] docs = rsp.getHits().getHits();
        Map<String, SolrUser> usersMap = new LinkedHashMap<String, SolrUser>();
        for (SearchHit sd : docs) {
//            System.out.println(sd.getExplanation().toString());
            SolrUser u = readDoc(sd.getSource(), sd.getId()).getFromUser();
            SolrUser uOld = usersMap.get(u.getScreenName());
            if (uOld == null)
                usersMap.put(u.getScreenName(), u);
            else
                uOld.addOwnTweet(u.getOwnTweets().iterator().next());
        }

        users.addAll(usersMap.values());
        return rsp;
    }

    public SearchResponse search(TweetESQuery request) {
        return search(new ArrayList(), request);
    }

    public SearchResponse search(Collection<SolrUser> users, TweetESQuery request) {
        SearchResponse rsp = query(request);
        SearchHit[] docs = rsp.getHits().getHits();
        Map<String, SolrUser> usersMap = new LinkedHashMap<String, SolrUser>();
        for (SearchHit sd : docs) {
//            System.out.println(sd.getExplanation().toString());
            SolrUser u = readDoc(sd.getSource(), sd.getId()).getFromUser();
            SolrUser uOld = usersMap.get(u.getScreenName());
            if (uOld == null)
                usersMap.put(u.getScreenName(), u);
            else
                uOld.addOwnTweet(u.getOwnTweets().iterator().next());
        }

        users.addAll(usersMap.values());
        return rsp;
    }

    public Collection<SolrTweet> searchReplies(long id, boolean retweet) {
        try {
            SolrQuery sq = new SolrQuery().addFilterQuery("crt_b:" + retweet).addFilterQuery(INREPLY_ID + ":" + id);
            SearchResponse rsp = queryOldSolr(sq);
            return collectTweets(rsp);
        } catch (Exception ex) {
            logger.error("Error while searchReplies", ex);
            return Collections.EMPTY_SET;
        }
    }

    void update(SolrTweet tweet, boolean refresh) {
        try {
            XContentBuilder b = createDoc(tweet);
            if (b != null)
                feedDoc(Long.toString(tweet.getTwitterId()), b);

            if (refresh)
                refresh();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    Collection<SolrTweet> update(SolrTweet tmpTweets) {
        return privateUpdate(Arrays.asList(tmpTweets));
    }

    Collection<SolrTweet> privateUpdate(Collection<SolrTweet> tmpTweets) {
        return update(tmpTweets, new Date(0));
    }

    /**
     * Updates a list of tweet's with its replies and retweets.
     *
     * @return a collection of tweets which were updated in solr
     */
    public Collection<SolrTweet> update(Collection<SolrTweet> tmpTweets, Date removeUntil) {
        try {
            Map<String, SolrUser> usersMap = new LinkedHashMap<String, SolrUser>();
            Map<Long, SolrTweet> existingTweets = new LinkedHashMap<Long, SolrTweet>();

            StringBuilder idStr = new StringBuilder();
            int counts = 0;
            // we can add max ~150 tweets per request (otherwise the webcontainer won't handle the long request)
            for (SolrTweet tw : tmpTweets) {
                if (counts > 0)
                    idStr.append(" OR ");
                counts++;
                idStr.append("_id:");
                idStr.append(tw.getTwitterId());
            }

            // get existing tweets and users                
            SolrQuery query = new SolrQuery().addFilterQuery(idStr.toString()).setRows(counts);
            SearchResponse rsp = search(query);
            SearchHits docs = rsp.getHits();

            for (SearchHit sd : docs) {
                SolrTweet tw = readDoc(sd.getSource(), sd.getId());
                existingTweets.put(tw.getTwitterId(), tw);
                SolrUser u = tw.getFromUser();
                SolrUser uOld = usersMap.get(u.getScreenName());
                if (uOld == null)
                    usersMap.put(u.getScreenName(), u);
                else
                    uOld.addOwnTweet(u.getOwnTweets().iterator().next());
            }

            Map<Long, SolrTweet> twMap = new LinkedHashMap<Long, SolrTweet>();
            for (SolrTweet solrTweet : tmpTweets) {
                // do not store if too old
                if (!solrTweet.isPersistent() && solrTweet.getCreatedAt().getTime() < removeUntil.getTime())
                    continue;

                SolrTweet spectw = existingTweets.get(solrTweet.getTwitterId());
                // feed if new or if it should be persistent
                if (spectw == null || solrTweet.isPersistent()) {
                    String name = solrTweet.getFromUser().getScreenName();
                    SolrUser u = usersMap.get(name);
                    if (u == null) {
                        u = solrTweet.getFromUser();
                        usersMap.put(name, u);
                    }

                    u.addOwnTweet(solrTweet);
                    // tweet does not exist. so store it into the todo map
                    twMap.put(solrTweet.getTwitterId(), solrTweet);
                }
            }

            LinkedHashSet<SolrTweet> updateTweets = new LinkedHashSet<SolrTweet>();
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

    public void update(Collection<SolrTweet> tweets) {
        update(tweets, true);
    }

    public void update(Collection<SolrTweet> tweets, boolean refresh) {
        try {
            if (tweets.isEmpty())
                return;

            tweets = new SerialCommandExecutor(tweets).add(
                    new TermCreateCommand()).execute();

            boolean bulk = true;
            if (bulk) {
                bulkUpdate(tweets, getIndexName(), refresh);
            } else {
                for (SolrTweet tw : tweets) {
                    String id = Long.toString(tw.getTwitterId());
                    feedDoc(id, createDoc(tw));
                }
            }
        } catch (Exception e) {
            logger.error("Exception while updating.", e);
        }
    }

    public void bulkUpdate(Collection<SolrTweet> tweets, String indexName) throws IOException {
        bulkUpdate(tweets, indexName, false);
    }

    public void bulkUpdate(Collection<SolrTweet> tweets, String indexName, boolean refresh) throws IOException {
        // now using bulk API instead of feeding each doc separate with feedDoc
        BulkRequestBuilder brb = client.prepareBulk();
        for (SolrTweet tw : tweets) {
            if (tw.getTwitterId() == null) {
                logger.warn("Skipped tweet without twitterid when bulkUpdate:" + tw);
                continue;
            }

            String id = Long.toString(tw.getTwitterId());
            XContentBuilder source = createDoc(tw);
            brb.add(Requests.indexRequest(indexName).type(getIndexType()).id(id).source(source));
            brb.setRefresh(refresh);
        }
        if (brb.numberOfActions() > 0) {
//            System.out.println("actions:" + brb.numberOfActions());
            BulkResponse rsp = brb.execute().actionGet();
            if (rsp.hasFailures())
                logger.error("Error while bulkUpdate:" + rsp.buildFailureMessage());
        }
    }

    /**
     * For every user there should be at least 5 tweets to make spam detection
     * more efficient
     */
    public void fetchMoreTweets(Map<Long, SolrTweet> tweets, final Map<String, SolrUser> userMap) {
        for (SolrUser us : userMap.values()) {
            // guarantee 5 tweets to be in the cache
            if (us.getOwnTweets().size() > 4)
                continue;

            //  fetch 10 tweets if less than 5 tweets are in the cache            
            SolrQuery query = new SolrQuery().addFilterQuery("user:" + us.getScreenName()).setRows(10);
            try {
                SearchResponse rsp = search(query);
                SearchHits docs = rsp.getHits();
                for (SearchHit sd : docs) {
                    SolrTweet tw = readDoc(sd.getSource(), sd.getId());
                    SolrTweet twOld = tweets.get(tw.getTwitterId());
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
    public Collection<SolrTweet> findRetweets(Map<Long, SolrTweet> tweets, final Map<String, SolrUser> userMap) {
        // 1. check if tweets contains originals which were retweeted -> only done for 'tweets'
        // 2. check if tweets contains retweets -> done for 'tweets' and for tweets in solr

        final Set<SolrTweet> updatedTweets = new LinkedHashSet<SolrTweet>();
        Extractor extractor = new Extractor() {

            @Override
            public boolean onNewUser(int index, String user) {
                boolean isRetweet = index >= 3 && text.substring(index - 3, index).equalsIgnoreCase("rt ");
                if (isRetweet) {
                    user = user.toLowerCase();
                    SolrUser existingU = userMap.get(user);
                    SolrTweet resTw = null;

                    // check ifRetweetOf against local tweets
                    if (existingU != null)
                        for (SolrTweet tmp : existingU.getOwnTweets()) {
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

        for (SolrTweet tw : tweets.values()) {
            if (tw.isRetweet())
                extractor.setTweet(tw).run();

        }

        return updatedTweets;
    }

    /**
     * add relation to existing/original tweet
     */
    public SolrTweet connectToOrigTweet(SolrTweet tw, String toUserStr) {
        if (tw.isRetweet() && SolrTweet.isDefaultInReplyId(tw.getInReplyTwitterId())) {
            // connect retweets to tweets only searchTweetsDays old

            try {
                SearchResponse qrsp = queryOldSolr(new SolrQuery("\"" + tw.extractRTText() + "\"").addFilterQuery("user:" + toUserStr).setRows(10));
                List<SolrTweet> existingTw = collectTweets(qrsp);

                for (SolrTweet tmp : existingTw) {
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
    public Collection<SolrTweet> findReplies(Map<Long, SolrTweet> tweets) {
        Set<SolrTweet> updatedTweets = new LinkedHashSet<SolrTweet>();
        Map<Long, SolrTweet> replyMap = new LinkedHashMap<Long, SolrTweet>();
        for (SolrTweet tw : tweets.values()) {
            if (!SolrTweet.isDefaultInReplyId(tw.getInReplyTwitterId()))
                replyMap.put(tw.getInReplyTwitterId(), tw);
        }

        Iterator<SolrTweet> iter = tweets.values().iterator();
        findRepliesInBatch(iter, tweets, replyMap, updatedTweets);

        return updatedTweets;
    }

    protected void findRepliesInBatch(Iterator<SolrTweet> iter, Map<Long, SolrTweet> origTweets,
            Map<Long, SolrTweet> replyIdToTweetMap, Collection<SolrTweet> updatedTweets) {
        int counter = 0;
        StringBuilder idStr = new StringBuilder();
        StringBuilder replyIdStr = new StringBuilder();
        while (iter.hasNext()) {
            SolrTweet tw = iter.next();
            SolrTweet tmp = replyIdToTweetMap.get(tw.getTwitterId());
            if (tmp != null) {
                if (addReplyNoTricks(tw, tmp)) {
                    updatedTweets.add(tw);
                    updatedTweets.add(tmp);
                }
            } else {
                replyIdStr.append(INREPLY_ID);
                replyIdStr.append(":");
                replyIdStr.append(tw.getTwitterId());
                replyIdStr.append(" OR ");
            }

            if (SolrTweet.isDefaultInReplyId(tw.getInReplyTwitterId()))
                continue;

            tmp = origTweets.get(tw.getInReplyTwitterId());
            if (tmp != null) {
                if (addReplyNoTricks(tmp, tw)) {
                    updatedTweets.add(tw);
                    updatedTweets.add(tmp);
                }
            } else {
                counter++;
                idStr.append("_id:");
                idStr.append(tw.getInReplyTwitterId());
                idStr.append(" OR ");
            }
        }

        try {
            // get tweets which replies our input tweets
            // INREPLY_ID:"tweets[i].id"            
            if (replyIdStr.length() > 0) {
                SolrQuery query = new SolrQuery().addFilterQuery(replyIdStr.toString()).setRows(origTweets.size());
                findRepliesForOriginalTweets(query, origTweets, updatedTweets);
            }

            // get original tweets where we have replies            
            if (idStr.length() > 0) {
                SolrQuery query = new SolrQuery().addFilterQuery(idStr.toString()).setRows(counter);
                selectOriginalTweetsWithReplies(query, origTweets.values(), updatedTweets);
            }
        } catch (Exception ex) {
            logger.error("couldn't find replies in a batch query", ex);
        }
    }

    protected void findRepliesForOriginalTweets(SolrQuery query, Map<Long, SolrTweet> tweets,
            Collection<SolrTweet> updatedTweets) {

        Map<Long, SolrTweet> replyMap = new LinkedHashMap<Long, SolrTweet>();
        SearchResponse rsp = queryOldSolr(query);
        SearchHits docs = rsp.getHits();

        for (SearchHit sd : docs) {
            SolrTweet tw = readDoc(sd.getSource(), sd.getId());
            replyMap.put(tw.getTwitterId(), tw);
        }

        for (SolrTweet inReplSolrTweet : replyMap.values()) {
            if (SolrTweet.isDefaultInReplyId(inReplSolrTweet.getInReplyTwitterId()))
                continue;
            SolrTweet origTw = tweets.get(inReplSolrTweet.getInReplyTwitterId());
            if (origTw != null && addReplyNoTricks(origTw, inReplSolrTweet)) {
                updatedTweets.add(origTw);
                updatedTweets.add(inReplSolrTweet);
            }
        }
    }

    protected void selectOriginalTweetsWithReplies(SolrQuery query, Collection<SolrTweet> tweets,
            Collection<SolrTweet> updatedTweets) {

        SearchResponse rsp = queryOldSolr(query);
        SearchHits docs = rsp.getHits();
        Map<Long, SolrTweet> origMap = new LinkedHashMap<Long, SolrTweet>();
        for (SearchHit sd : docs) {
            SolrTweet tw = readDoc(sd.getSource(), sd.getId());
            origMap.put(tw.getTwitterId(), tw);
        }

        if (origMap.size() > 0)
            for (SolrTweet inReplSolrTweet : tweets) {
                if (SolrTweet.isDefaultInReplyId(inReplSolrTweet.getInReplyTwitterId()))
                    continue;
                SolrTweet origTw = origMap.get(inReplSolrTweet.getInReplyTwitterId());
                if (origTw != null && addReplyNoTricks(origTw, inReplSolrTweet)) {
                    updatedTweets.add(origTw);
                    updatedTweets.add(inReplSolrTweet);
                }
            }
    }

    public boolean addReplyNoTricks(SolrTweet orig, SolrTweet reply) {
        if (orig.getFromUser().equals(reply.getFromUser()))
            return false;

        try {
            // ensure that reply.user has not already a tweet in orig.replies   
            SolrQuery q = new SolrQuery().addFilterQuery(INREPLY_ID + ":" + orig.getTwitterId()).
                    addFilterQuery("-_id:" + reply.getTwitterId()).
                    addFilterQuery("user:" + reply.getFromUser().getScreenName());
            if (queryOldSolr(q).getHits().getTotalHits() > 0)
                return false;

            orig.addReply(reply);
            return true;
        } catch (Exception ex) {
            logger.error("couldn't add reply to:" + orig, ex);
            return false;
        }
    }

    public List<SolrTweet> collectTweets(SearchResponse rsp) {
        SearchHits docs = rsp.getHits();
        List<SolrTweet> list = new ArrayList<SolrTweet>();

        for (SearchHit sd : docs) {
            list.add(readDoc(sd.getSource(), sd.getId()));
        }

        return list;
    }

    public SolrTweet findByTwitterId(Long twitterId) {
        try {
            GetResponse rsp = client.prepareGet(getIndexName(), getIndexType(), "" + twitterId).
                    execute().actionGet();
            if (rsp.getSource() == null)
                return null;
            return readDoc(rsp.getSource(), rsp.getId());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public Collection<String> getUserChoices(SolrQuery lastQ, String input) {
        try {
            if (input.length() < 1)
                return Collections.emptyList();

            // NOT context dependent any longer ...                        
            input = input.toLowerCase();
            SearchRequestBuilder srb = client.prepareSearch(getIndexName());
            srb.setQuery(QueryBuilders.fieldQuery(USER, input + "*"));
            List<SolrUser> users = new ArrayList<SolrUser>();
            search(users, new TweetESQuery(srb, false));
            Set<String> res = new TreeSet<String>();
            for (SolrUser u : users) {
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

    public Collection<String> getQueryChoices(SolrQuery lastQ, String input) {
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

            if (lastQ == null)
                lastQ = new TweetQuery(firstPart) {

                    @Override
                    public TweetQuery attachFacetibility() {
                        return this;
                    }
                };
            else {
                lastQ = lastQ.getCopy().setQuery(firstPart);
                // remove any date restrictions
                JetwickQuery.removeFilterQueries(lastQ, "dt");
                JetwickQuery.removeFacets(lastQ);
            }

            SearchRequestBuilder srb = client.prepareSearch(getIndexName());
            new Solr2ElasticTweet().createElasticQuery(lastQ, srb);
            TermsFacetBuilder tfb = FacetBuilders.termsFacet(TAG).field(TAG);

            if (!secPart.trim().isEmpty())
                tfb.regex(secPart + ".*", Pattern.DOTALL);

            srb.addFacet(tfb);
            SearchResponse rsp = search(new TweetESQuery(srb, false));
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
            logger.error("Error while getQueryChoices:" + input + " " + lastQ, ex);
            return Collections.emptyList();
        }
    }

    SolrUser findByUserName(String uName) {
        try {
            List<SolrUser> list = new ArrayList<SolrUser>();
            // get all tweets of the user so set rows large ...            
            search(list, new SolrQuery().addFilterQuery("user:" + uName.toLowerCase()).setRows(10));

            if (list.isEmpty())
                return null;

            return list.get(0);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<SolrTweet> searchTweets(SolrQuery q) {
        try {
            return collectTweets(search(q));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public String getTweetsAsString(SolrQuery q) {
        StringBuilder sb = new StringBuilder();
        List<SolrTweet> tweets = searchTweets(q);
        String separator = ",";
        for (SolrTweet tweet : tweets) {
            sb.append(Helper.toTwitterHref(tweet.getFromUser().getScreenName(), tweet.getTwitterId()));
            sb.append(separator);
            sb.append(tweet.getRetweetCount());
            sb.append(separator);
            sb.append(tweet.getText().replaceAll("\n", " "));
            sb.append("\n");
        }

        return sb.toString();
    }

    public Collection<SolrTweet> searchAds(String query) {
        query = query.trim();
        if (query.isEmpty())
            return Collections.EMPTY_LIST;

        Collection<SolrUser> users = new LinkedHashSet<SolrUser>();
        MyDate now = new MyDate();
        // NOW/DAY-3DAYS
        MyDate from = new MyDate().castToDay().minusDays(2);
        search(users, new SolrQuery(query).addFilterQuery("tw:#jetwick").
                //                addFilterQuery(RT_COUNT + ":[1 TO *]").
                addFilterQuery(QUALITY + ":[90 TO 100]").
                addFilterQuery(IS_RT + ":false").
                addFilterQuery(DATE + ":[" + from.toLocalString() + " TO " + now.toLocalString() + "]").
                setSortField(RT_COUNT, SolrQuery.ORDER.desc));

        Set<SolrTweet> res = new LinkedHashSet<SolrTweet>();
        for (SolrUser u : users) {
            if (u.getOwnTweets().size() > 0)
                res.add(u.getOwnTweets().iterator().next());
        }
        return res;
    }

    public Collection<SolrTweet> findDuplicates(Map<Long, SolrTweet> tweets) {
        final Set<SolrTweet> updatedTweets = new LinkedHashSet<SolrTweet>();
        TermCreateCommand termCommand = new TermCreateCommand();


        double JACC_BORDER = 0.7;
        for (SolrTweet currentTweet : tweets.values()) {
            if (currentTweet.isRetweet())
                continue;

            TweetESQuery reqBuilder = createQuery().createSimilarQuery(currentTweet).addLatestDateFilter(12);
            if (currentTweet.getTextTerms().size() < 3)
                continue;

            int dups = 0;

            try {
                // find dups in index
                for (SolrTweet simTweet : collectTweets(search(reqBuilder))) {
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
            for (SolrTweet simTweet : tweets.values()) {
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

    public SearchResponse updateSavedSearches(Collection<SavedSearch> savedSearches) {
        return search(createQuery().createSavedSearchesQuery(savedSearches));
    }

    /**
     * All indices has to be created before!
     */
    public void mergeIndices(Collection<String> indexList, String intoIndex, int hitsPerPage, boolean forceRefresh,
            XContentFilterBuilder additionalFilter) {
        if (forceRefresh) {
            refresh(indexList);
            refresh(intoIndex);
        }

        for (String fromIndex : indexList) {
            SearchRequestBuilder srb = client.prepareSearch(fromIndex).
                    // important to sort otherwise https://github.com/elasticsearch/elasticsearch/issues/issue/680
                    addSort("_id", SortOrder.ASC).
                    setQuery(QueryBuilders.matchAllQuery()).setSize(hitsPerPage).
                    // important to use QUERY_THEN_FETCH !
                    setSearchType(SearchType.QUERY_THEN_FETCH).
                    setScroll(TimeValue.timeValueMinutes(30));
            if (additionalFilter != null)
                srb.setFilter(additionalFilter);
            SearchResponse rsp = srb.execute().actionGet();

            try {
                long total = rsp.hits().totalHits();
                int collectedResults = 0;
                int currentResults;
                while ((currentResults = rsp.hits().hits().length) > 0) {
                    Collection tweets = collectTweets(rsp);
                    StopWatch updateWatch = new StopWatch().start();
                    bulkUpdate(tweets, intoIndex);
                    updateWatch.stop();
                    collectedResults += currentResults;
                    StopWatch queryWatch = new StopWatch().start();
                    rsp = client.prepareSearchScroll(rsp.scrollId()).
                            setScroll(TimeValue.timeValueMinutes(30)).execute().actionGet();
                    queryWatch.stop();
                    logger.info("Progress " + collectedResults +"/" + total + " fromIndex=" +
                            fromIndex + " update:" + updateWatch.totalTime().getSeconds() + " query:" + queryWatch.totalTime().getSeconds());
                }
                logger.info("Finished copying of index:" + fromIndex + ". Total:" + total + " collected:" + collectedResults);
            } catch (Exception ex) {
                logger.error("Failed to copy data from index " + fromIndex + " into " + intoIndex + ".", ex);
            }
        }

        if (forceRefresh)
            refresh(intoIndex);
    }
}
