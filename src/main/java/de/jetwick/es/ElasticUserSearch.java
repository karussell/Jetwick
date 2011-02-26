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
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
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
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class ElasticUserSearch extends AbstractElasticSearch<JUser> {

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

    void delete(JUser user, boolean commit) {
        if (user.getScreenName() == null)
            throw new NullPointerException("Null " + JUser.SCREEN_NAME + " is not allowed! User:" + user);

        deleteById(user.getScreenName());
        if (commit)
            refresh();
    }

    public void update(Collection<JUser> users) {
        update(users, users.size());
    }

    /**
     * This method pushes the specified user in the specified batch size to solr.
     * If it is configured it will retry if one batch is failing.
     */
    public void update(Collection<JUser> users, int batchSize) {
        Collection<XContentBuilder> list = new ArrayList<XContentBuilder>();

        for (JUser user : users) {
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

    public void update(JUser user, boolean optimize, boolean refresh) {
        save(user, refresh);
    }

    public void save(JUser user, boolean refresh) {
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

    @Override
    public XContentBuilder createDoc(JUser user) throws IOException {
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

        return b;
    }

    @Override
    public JUser readDoc(Map<String, Object> doc, String idAsStr) {
        String userName = idAsStr;
        JUser user = new JUser(userName);
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

    public JUser findByTwitterToken(String token) {
        try {
            Collection<JUser> res = collectObjects(search(new UserQuery().addFilterQuery("token_s", token)));
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

    public JUser findByScreenName(String name) {
        try {
            name = name.toLowerCase();
            Collection<JUser> res = collectObjects(search(new UserQuery().addFilterQuery(SCREEN_NAME, name)));
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

    public Collection<String> getQueryTerms() {
        SearchResponse rsp = search(new UserQuery().addFacetField(QUERY_TERMS));
        TermsFacet tf = (TermsFacet) rsp.getFacets().facet(QUERY_TERMS);
        Collection<String> res = new ArrayList<String>();
        if (tf.entries() != null)
            for (TermsFacet.Entry cnt : tf.entries()) {
                if (cnt.getCount() > 0)
                    res.add(cnt.getTerm());
            }
        return res;
    }

    public SearchResponse search(JetwickQuery query) {
        return search(new ArrayList(), query);
    }

    public SearchResponse search(Collection<JUser> users, JetwickQuery query) {
        SearchRequestBuilder srb = client.prepareSearch(getIndexName());        
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
        SearchResponse rsp = search(users, query);
        return rsp.getHits().totalHits();
    }

    void setTermMinFrequency(int tmf) {
        termMinFrequency = tmf;
    }
}
