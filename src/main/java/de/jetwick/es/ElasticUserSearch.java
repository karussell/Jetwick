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
import org.elasticsearch.action.search.SearchResponse;
import de.jetwick.config.Configuration;
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
import de.jetwick.tw.TweetDetector;
import de.jetwick.tw.cmd.StringFreqMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.FilteredQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder.Operator;
import org.elasticsearch.search.facet.FacetBuilders;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class ElasticUserSearch extends AbstractElasticSearch<JUser> {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private static final String TOKEN = "token_s";
    private static final String QUERY_TERMS = "ss_qterms_mv_s";
    private static final String SCREEN_NAME = "name";
    private static final String ACTIVE = "active";
    private static final String LAST_VISIT_DT = "lastVisit_dt";
    private static final String CREATED_DT = "createdAt_dt";
    private static final String TOPICS = "topics";
    private static final String TWITTER_ID = "twitterId";
    private static final String EMAIL = "email";
    protected int termMinFrequency = 2;
    private String indexName = "uindex";

    public ElasticUserSearch(Configuration config) {
        this(config.getTweetSearchUrl());
    }

    public ElasticUserSearch(String url) {
        super(url);
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

    void delete(JUser user, boolean commit) {
        if (user.getScreenName() == null)
            throw new NullPointerException("Null " + JUser.SCREEN_NAME + " is not allowed! User:" + user);

        deleteById(user.getScreenName());
        if (commit)
            refresh();
    }

    public void update(Collection<JUser> users) {
        bulkUpdate(users, getIndexName(), false);
    }

    public void update(JUser user, boolean optimize, boolean refresh) {
        save(user, refresh);
    }

    public void save(JUser user, boolean refresh) {
        try {
            bulkUpdate(Collections.singleton(user), getIndexName(), refresh);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public XContentBuilder createDoc(JUser user) throws IOException {
        XContentBuilder b = JsonXContent.contentBuilder().startObject();
        // make sure that if we look for a specific user this user will show up first:
        b.field(SCREEN_NAME, user.getScreenName());

        if (user.getTwitterId() != null)
            b.field(TWITTER_ID, user.getTwitterId());

        b.field("realName", user.getRealName());
        b.field("protected", user.isProtected());
        b.field("weekFallback", user.isWeekFallback());
        b.field("iconUrl", user.getProfileImageUrl());
        b.field("webUrl", user.getWebUrl());
        b.field("bio", user.getDescription());
        b.field("mode", user.getMode());
        b.field(TOKEN, user.getTwitterToken());
        b.field("tokenSecret_s", user.getTwitterTokenSecret());

        b.field(CREATED_DT, user.getCreatedAt());
        b.field("twCreatedAt_dt", user.getTwitterCreatedAt());
        b.field("friendsUpdate_dt", user.getLastFriendsUpdate());
        b.field("friends", Helper.toStringArray(user.getFriends()));
        b.field(LAST_VISIT_DT, user.getLastVisit());
        b.field(EMAIL, user.getEmail());
        b.field(ACTIVE, user.isActive());
        b.field("role", user.getRole());

        b.field("followersCount", user.getFollowersCount());
        b.field("friendsCount", user.getFriendsCount());

        int counter = 1;
        for (SavedSearch ss : user.getSavedSearches()) {
            b.field("ss_" + counter + "_query_s", ss.getCleanQuery().toString());
            b.field("ss_" + counter + "_last_dt", ss.getLastQueryDate());

            if (ss.getQueryTerm() != null && !ss.getQueryTerm().isEmpty()) {
                // for tweetProducer (pick via facets) and stats:
                b.field(QUERY_TERMS, ss.getQueryTerm());
            }
            counter++;
        }

        // some users were only mentioned by others ...
        Collection<JTweet> tweets = user.getOwnTweets();
        if (tweets.size() > 0) {
            TweetDetector extractor = new TweetDetector(tweets);
            List<String> tagList = new ArrayList<String>();
            for (Entry<String, Integer> entry : extractor.run().getSortedTerms()) {
                if (entry.getValue() > termMinFrequency)
                    tagList.add(entry.getKey());
            }
            b.field("tag", tagList);

            StringFreqMap langs = new StringFreqMap();
            for (JTweet tw : tweets) {
                langs.inc(tw.getLanguage(), 1);
            }

            List<String> langList = new ArrayList<String>();
            for (Entry<String, Integer> lang : langs.getSorted()) {
                langList.add(lang.getKey());
            }
            b.field("lang", langList);
        }

        List<Map<String, Object>> listOfMaps = new ArrayList<Map<String, Object>>(
                user.getTopicsMap().size());
        for (Entry<String, Date> entry : user.getTopicsMap().entrySet()) {
            Map<String, Object> map = new LinkedHashMap<String, Object>(2);
            map.put("name", entry.getKey());
            map.put("lastRead", entry.getValue());
            listOfMaps.add(map);
        }
        b.array(TOPICS, listOfMaps.toArray(new Map[listOfMaps.size()]));
        return b;
    }

    @Override
    public JUser readDoc(String idAsStr, long version, Map<String, Object> doc) {
        String userName = idAsStr;
        JUser user = new JUser(userName);
        if (doc.get(TWITTER_ID) != null)
            user.setTwitterId(((Number) doc.get(TWITTER_ID)).longValue());

        Boolean active = (Boolean) doc.get(ACTIVE);
        if (active == null)
            user.setActive(true);
        else
            user.setActive(active);

        user.setRealName((String) doc.get("realName"));
        user.setProfileImageUrl((String) doc.get("iconUrl"));
        user.setWebUrl((String) doc.get("webUrl"));
        user.setDescription((String) doc.get("bio"));
        user.setTwitterToken((String) doc.get(TOKEN));
        user.setTwitterTokenSecret((String) doc.get("tokenSecret_s"));
        user.setMode((String) doc.get("mode"));

        Collection<Map<String, Object>> topics = ((Collection<Map<String, Object>>) doc.get(TOPICS));
        if (topics != null)
            for (Map t : topics) {
                Date date = Helper.toDateNoNPE((String) t.get("lastRead"));
                user.updateTopic((String) t.get("name"), date);
            }

        if (doc.get("protected") != null)
            user.setProtected((Boolean) doc.get("protected"));

        if (doc.get("weekFallback") != null)
            user.setWeekFallback((Boolean) doc.get("weekFallback"));

        user.setLastVisit(Helper.toDateNoNPE((String) doc.get(LAST_VISIT_DT)));
        user.setCreatedAt(Helper.toDateNoNPE((String) doc.get(CREATED_DT)));
        user.setTwitterCreatedAt(Helper.toDateNoNPE((String) doc.get("twCreatedAt_dt")));
        user.setLastFriendsUpdate(Helper.toDateNoNPE((String) doc.get("friendsUpdate_dt")));
        user.setFriends((Collection<String>) doc.get("friends"));

        if (doc.get("followersCount") != null)
            user.setFollowersCount(((Number) doc.get("followersCount")).intValue());

        if (doc.get("friendsCount") != null)
            user.setFriendsCount(((Number) doc.get("friendsCount")).intValue());

        if (doc.get("role") != null)
            user.setRole((String) doc.get("role"));
        user.setEmail((String) doc.get(EMAIL));

        long counter = 1;
        while (true) {
            String qString = (String) doc.get("ss_" + counter + "_query_s");
            if (qString == null)
                // backward compatibility
                break;

            TweetQuery q = TweetQuery.parseQuery(qString);
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

    SearchResponse prepareFindBy(String key, Object value) {
        SearchRequestBuilder srb = createSearchBuilder();
        // fastest method. we only expect one or two objects
        srb.setSearchType(SearchType.QUERY_AND_FETCH);
        srb.setQuery(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
                FilterBuilders.termFilter(key, value)));
        return srb.execute().actionGet();
    }

    /**
     * Deprecated. Use findById
     * @param token
     * @return 
     */
    public JUser findByTwitterToken(String token) {
        try {
            Collection<JUser> res = collectObjects(prepareFindBy(TOKEN, token));
            if (res.isEmpty())
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

    /**
     * @return the user with the specified twitter id
     */
    public JUser findById(long userTwitterId) {
        try {            
            Collection<JUser> res = collectObjects(prepareFindBy(TWITTER_ID, userTwitterId));
            if (res.isEmpty())
                return null;
            else if (res.size() == 1) {
                JUser u = res.iterator().next();
                u.setTwitterId(userTwitterId);
                return u;
            } else
                throw new IllegalStateException("userId search:" + userTwitterId + " returns more than one users:" + res);
        } catch (Exception ex) {
            logger.error("Couldn't load user with userId:" + userTwitterId + " " + ex.getMessage());
            return null;
        }
    }

    public JUser findByScreenName(String name) {
        try {
            name = name.toLowerCase();
            GetRequestBuilder grb = client.prepareGet(getIndexName(), getIndexType(), name);            
            GetResponse gr = grb.execute().actionGet();
            if (gr.isExists())
                return readDoc(gr.id(), gr.version(), gr.sourceAsMap());            
        } catch (Exception ex) {
            logger.error("Couldn't load user with screenName:" + name + " " + ex.getMessage());            
        }
        return null;
    }

    public JUser findByEmail(String email) {
        try {
            email = email.toLowerCase();
            Collection<JUser> res = collectObjects(prepareFindBy(EMAIL, email));
            if (res.isEmpty())
                return null;
            else if (res.size() == 1)
                return res.iterator().next();
            else
                throw new IllegalStateException("email search:" + email + " returns more than one users:" + res);
        } catch (Exception ex) {
            logger.error("Couldn't load user with email:" + email + " " + ex.getMessage());
            return null;
        }
    }

    public Collection<JUser> findByTopic(String topic, int size) {
        try {
            SearchRequestBuilder srb = createSearchBuilder();
            srb.addSort(LAST_VISIT_DT, SortOrder.DESC);
            srb.setQuery(QueryBuilders.queryString(topic).defaultOperator(Operator.AND).defaultField(TOPICS + ".name").
                    allowLeadingWildcard(false).useDisMax(true));
            srb.setSize(size);
            SearchResponse rsp = srb.execute().actionGet();
            logger.info("[user.findByTopic] took:" + rsp.getTookInMillis() / 1000f + " topic:" + topic + " hits:" + rsp.getHits().totalHits());
            List<JUser> list = collectObjects(rsp);
            Collections.shuffle(list);
            return list;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    public Collection<String> getQueryTerms() {
        SearchRequestBuilder srb = createSearchBuilder();
        FilteredQueryBuilder fb = QueryBuilders.filteredQuery(
                QueryBuilders.matchAllQuery(), FilterBuilders.existsFilter(TOKEN));

        srb.addFacet(FacetBuilders.termsFacet(QUERY_TERMS).field(QUERY_TERMS).size(1000)).
                setQuery(fb);
        SearchResponse rsp = srb.execute().actionGet();
//        SearchResponse rsp = search(new UserQuery().addFacetField(QUERY_TERMS, 1000));
        TermsFacet tf = (TermsFacet) rsp.getFacets().facet(QUERY_TERMS);
        if (tf.entries() != null && !tf.entries().isEmpty()) {
            Collection<String> res = new ArrayList<String>();
            for (TermsFacet.Entry cnt : tf.entries()) {
                if (cnt.getCount() > 0)
                    res.add(cnt.getTerm());
            }
            return res;
        }
        return new ArrayList(1);
    }

    // topics is analyzed!!
//    public Collection<String> getTopics() {
//        SearchRequestBuilder srb = createSearchBuilder();
//        FilteredQueryBuilder fb = QueryBuilders.filteredQuery(
//                QueryBuilders.matchAllQuery(), FilterBuilders.existsFilter(TOKEN));
//
//        srb.addFacet(FacetBuilders.termsFacet(TOPICS).field(TOPICS).size(1000)).
//                setQuery(fb);
//        SearchResponse rsp = srb.execute().actionGet();
//        TermsFacet tf = (TermsFacet) rsp.getFacets().facet(TOPICS);
//        if (tf.entries() != null && !tf.entries().isEmpty()) {
//            Collection<String> res = new ArrayList<String>();
//            for (TermsFacet.Entry cnt : tf.entries()) {
//                if (cnt.getCount() > 0)
//                    res.add(cnt.getTerm());
//            }
//            return res;
//        }
//        return Collections.emptyList();
//    }
    @Override
    public SearchResponse query(JetwickQuery query) {
        return query(new ArrayList(), query);
    }

    public SearchResponse query(Collection<JUser> users, JetwickQuery query) {
        SearchRequestBuilder srb = createSearchBuilder();
        SearchResponse response = query.initRequestBuilder(srb).execute().actionGet();
        users.addAll(collectObjects(response));
        return response;
    }

    /** use createQuery + search instead */
    @Deprecated
    Collection<JUser> search(String string) {
        Set<JUser> ret = new LinkedHashSet<JUser>();
        search(ret, string, 10, 0);
        return ret;
    }

    /** use createQuery + search instead */
    @Deprecated
    long search(Collection<JUser> users, String qStr, int hitsPerPage, int page) {
        JetwickQuery query = new UserQuery(qStr);
        query.attachPagability(page, hitsPerPage);
        SearchResponse rsp = query(users, query);
        return rsp.getHits().totalHits();
    }

    void setTermMinFrequency(int tmf) {
        termMinFrequency = tmf;
    }

    public void searchLastLoggedIn(Set<JUser> users, int from, int size) {
        SearchRequestBuilder srb = createSearchBuilder();
        srb.setQuery(QueryBuilders.filteredQuery(
                QueryBuilders.matchAllQuery(),
                FilterBuilders.andFilter(
                FilterBuilders.existsFilter(TOKEN),
                FilterBuilders.termFilter(ACTIVE, true))));
        srb.setFrom(from);
        srb.setSize(size);
        // prefer last logged in users        
        srb.addSort(ElasticUserSearch.CREATED_DT, SortOrder.DESC);
        users.addAll(collectObjects(srb.execute().actionGet()));
    }
}
