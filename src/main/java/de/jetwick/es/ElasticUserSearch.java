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

import de.jetwick.util.Helper;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.action.search.SearchResponse;
import de.jetwick.config.Configuration;
import de.jetwick.solr.JetwickQuery;
import de.jetwick.solr.SavedSearch;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
import de.jetwick.solr.UserQuery;
import de.jetwick.tw.TweetDetector;
import de.jetwick.tw.cmd.StringFreqMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.params.MoreLikeThisParams;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.action.bulk.BulkRequestBuilder;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.xcontent.QueryBuilders;
import org.elasticsearch.index.query.xcontent.XContentFilterBuilder;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class ElasticUserSearch extends AbstractElasticSearch {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private static final String QUERY_TERMS = "ss_qterms_mv_s";
    private static final String SCREEN_NAME = "name";
    public static final String CREATED_AT = "createdAt_dt";
    protected int termMinFrequency = 2;
    private String indexName = "uindex";

    public ElasticUserSearch(Configuration config) {
        this(config.getTweetSearchUrl(), config.getTweetSearchLogin(), config.getTweetSearchPassword());
    }

    public ElasticUserSearch(String url, String login, String pw) {
        super(url, login, pw);
    }

    public ElasticUserSearch(Client client) {
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
        return "user";
    }

    void delete(SolrUser user, boolean commit) {
        if (user.getScreenName() == null)
            throw new NullPointerException("Null " + SolrUser.SCREEN_NAME + " is not allowed! User:" + user);

        deleteById(user.getScreenName());
        if (commit)
            refresh();
    }

    public void update(Collection<SolrUser> users) {
        update(users, users.size());
    }

    /**
     * This method pushes the specified user in the specified batch size to solr.
     * If it is configured it will retry if one batch is failing.
     */
    public void update(Collection<SolrUser> users, int batchSize) {
        Collection<XContentBuilder> list = new ArrayList<XContentBuilder>();

        for (SolrUser user : users) {
            try {
                XContentBuilder doc = createDoc(user);
                if (doc != null)
                    list.add(doc);

                feedDoc(user.getScreenName(), doc);
            } catch (IOException ex) {
                logger.error("skipped user when feeding:" + user, ex);
            }
        }
    }

    public void update(SolrUser user, boolean optimize, boolean refresh) {
        save(user, refresh);
    }

    public void save(SolrUser user, boolean refresh) {
        try {
            XContentBuilder b = createDoc(user);
            if (b != null)
                feedDoc(user.getScreenName(), b);

            if (refresh)
                refresh();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public XContentBuilder createDoc(SolrUser user) throws IOException {
        XContentBuilder b = JsonXContent.unCachedContentBuilder().startObject();
        // make sure that if we look for a specific user this user will show up first:
        b.field(SCREEN_NAME, user.getScreenName());
        b.field("realName", user.getRealName());
        b.field("iconUrl", user.getProfileImageUrl());
        b.field("webUrl", user.getWebUrl());
        b.field("bio", user.getDescription());
        b.field("token_s", user.getTwitterToken());
        b.field("tokenSecret_s", user.getTwitterTokenSecret());

        b.field(CREATED_AT, user.getCreatedAt());
        b.field("twCreatedAt_dt", user.getTwitterCreatedAt());
        b.field("friendsUpdate_dt", user.getLastFriendsUpdate());
        b.field("friends", Helper.toStringArray(user.getFriends()));

        int counter = 1;
        for (SavedSearch ss : user.getSavedSearches()) {
            b.field("ss_" + counter + "_query_s", ss.getCleanQuery().toString());
            b.field("ss_" + counter + "_last_dt", ss.getLastQueryDate());

            if (ss.getQueryTerm() != null && !ss.getQueryTerm().isEmpty()) {
                // for tweetProducer (pick important via facets) and stats:
                b.field(QUERY_TERMS, ss.getQueryTerm().toLowerCase());
            }
            counter++;
        }

        // some users were only mentioned by others ...
        Collection<SolrTweet> tweets = user.getOwnTweets();
        if (tweets.size() > 0) {
            TweetDetector extractor = new TweetDetector(tweets);
            List<String> tagList = new ArrayList<String>();
            for (Entry<String, Integer> entry : extractor.run().getSortedTerms()) {
                if (entry.getValue() > termMinFrequency)
                    tagList.add(entry.getKey());
            }
            b.field("tag", tagList);

            StringFreqMap langs = new StringFreqMap();
            for (SolrTweet tw : tweets) {
                langs.inc(tw.getLanguage(), 1);
            }

            List<String> langList = new ArrayList<String>();
            for (Entry<String, Integer> lang : langs.getSorted()) {
                langList.add(lang.getKey());
            }
            b.field("lang", langList);
        }

        return b;
    }

    public SolrUser readDoc(Map<String, Object> doc, String idAsStr) {
        String userName = idAsStr;
        SolrUser user = new SolrUser(userName);
        user.setRealName((String) doc.get("realName"));
        user.setProfileImageUrl((String) doc.get("iconUrl"));
        user.setWebUrl((String) doc.get("webUrl"));
        user.setDescription((String) doc.get("bio"));
        user.setTwitterToken((String) doc.get("token_s"));
        user.setTwitterTokenSecret((String) doc.get("tokenSecret_s"));

        user.setUpdateAt(Helper.toDateNoNPE((String) doc.get("timestamp")));
        user.setCreatedAt(Helper.toDateNoNPE((String) doc.get(CREATED_AT)));
        user.setTwitterCreatedAt(Helper.toDateNoNPE((String) doc.get("twCreatedAt_dt")));
        user.setLastFriendsUpdate(Helper.toDateNoNPE((String) doc.get("friendsUpdate_dt")));
        user.setFriends((Collection<String>) doc.get("friends"));

        long counter = 1;
        while (true) {
            String qString = (String) doc.get("ss_" + counter + "_query_s");
            if (qString == null)
                // backward compatibility
                break;

            SolrQuery q = JetwickQuery.parse(qString);
            SavedSearch ss = new SavedSearch(counter, q);
            ss.setLastQueryDate(Helper.toDateNoNPE((String) doc.get("ss_" + counter + "_last_dt")));
            user.addSavedSearch(ss);
            counter++;
        }
        // only used for facet search? doc.get("lang");        

        Collection<Object> tags = (Collection<Object>) doc.get("tag");
        if (tags != null)
            for (Object tag : tags) {
                user.addTag((String) tag);
            }

        Collection<Object> langs = (Collection<Object>) doc.get("lang");
        if (langs != null)
            for (Object lang : langs) {
                user.addLanguage((String) lang);
            }

        return user;
    }

    public SolrQuery createMltQuery(String queryStr) {
        UserQuery query = new UserQuery();

        // this does not work:
//        query.setQuery(queryStr).
//                setQueryType("standard").
//                set(MoreLikeThisParams.QF, SCREEN_NAME).
//                set(MoreLikeThisParams.MLT, "true").

        query.setQuery(SCREEN_NAME + ":" + queryStr).
                setQueryType("/" + MoreLikeThisParams.MLT).
                set(MoreLikeThisParams.SIMILARITY_FIELDS, "bio", "tag").
                set(MoreLikeThisParams.MIN_WORD_LEN, 2).
                set(MoreLikeThisParams.MIN_DOC_FREQ, 1).
                // don't return the user itself
                set(MoreLikeThisParams.MATCH_INCLUDE, false);

        // the following param could be a nice addition: show the user which terms are similar:
//         MoreLikeThisParams.INTERESTING_TERMS
        return query;
    }

    public SolrUser findByTwitterToken(String token) {
        try {
            Collection<SolrUser> res = collectUsers(search(new SolrQuery().addFilterQuery("token_s:" + token)));
            if (res.size() == 0)
                return null;
            else if (res.size() == 1)
                return res.iterator().next();
            else
                throw new IllegalStateException("token search:" + token + " returns more than one users:" + res);
        } catch (Exception ex) {
            logger.error("Couldn't load user with token:" + token + " " + ex.getMessage());
            return null;
        }
    }

    public SolrUser findByScreenName(String name) {
        try {
            name = name.toLowerCase();
            Collection<SolrUser> res = collectUsers(search(new SolrQuery().addFilterQuery(SCREEN_NAME + ":" + name)));
            if (res.isEmpty())
                return null;
            else if (res.size() == 1)
                return res.iterator().next();
            else
                throw new IllegalStateException("screenName search:" + name + " returns more than one users:" + res);
        } catch (Exception ex) {
            logger.error("Couldn't load user with screenName:" + name + " " + ex.getMessage());
            return null;
        }
    }

    // TODO facet pagination
    public Collection<String> getQueryTerms() {
        SearchResponse rsp = search(new SolrQuery().setFacet(true).addFacetField(QUERY_TERMS).setFacetMinCount(1));
        TermsFacet tf = (TermsFacet) rsp.getFacets().facet(QUERY_TERMS);
        Collection<String> res = new ArrayList<String>();
        if (tf.entries() != null)
            for (TermsFacet.Entry cnt : tf.entries()) {
                if (cnt.getCount() > 0)
                    res.add(cnt.getTerm());
            }
        return res;
    }

    public SearchResponse search(SolrQuery query) {
        return search(new ArrayList(), query);
    }

    public SearchResponse search(Collection<SolrUser> users, SolrQuery query) {
        SearchRequestBuilder srb = client.prepareSearch(getIndexName());
        new Solr2ElasticUser().createElasticQuery(query, srb);
        SearchResponse response = srb.execute().actionGet();
        users.addAll(collectUsers(response));
        return response;
    }

    public Collection<SolrUser> collectUsers(SearchResponse rsp) {
        SearchHit docs[] = rsp.getHits().getHits();
        Collection<SolrUser> users = new LinkedHashSet<SolrUser>();

        for (SearchHit sd : docs) {
            SolrUser u = readDoc(sd.getSource(), sd.getId());
            users.add(u);
        }
        return users;
    }

    /** use createQuery + search instead */
    @Deprecated
    Collection<SolrUser> search(String string) {
        Set<SolrUser> ret = new LinkedHashSet<SolrUser>();
        search(ret, string, 10, 0);
        return ret;
    }

    /** use createQuery + search instead */
    @Deprecated
    long search(Collection<SolrUser> users, String qStr, int hitsPerPage, int page) {
        SolrQuery query = new UserQuery(qStr);
        attachPagability(query, page, hitsPerPage);
        SearchResponse rsp = search(users, query);
        return rsp.getHits().totalHits();
    }

    void setTermMinFrequency(int tmf) {
        termMinFrequency = tmf;
    }

    public void bulkUpdate(Collection<SolrUser> users, String indexName) throws IOException {
        bulkUpdate(users, indexName, false);
    }

    public void bulkUpdate(Collection<SolrUser> users, String indexName, boolean refresh) throws IOException {
        // now using bulk API instead of feeding each doc separate with feedDoc
        BulkRequestBuilder brb = client.prepareBulk();
        logger.info(users.toString());
        for (SolrUser user : users) {
            if (user.getScreenName() == null) {
                logger.warn("Skipped user without screenName when bulkUpdate:" + user);
                continue;
            }

            String id = user.getScreenName();
            XContentBuilder source = createDoc(user);
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
                    Collection users = collectUsers(rsp);
                    bulkUpdate(users, intoIndex);
                    collectedResults += currentResults;
                    rsp = client.prepareSearchScroll(rsp.scrollId()).
                            setScroll(TimeValue.timeValueMinutes(30)).execute().actionGet();
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
