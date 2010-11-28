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

import de.jetwick.config.Configuration;
import de.jetwick.tw.TweetDetector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.MoreLikeThisParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Querying + Indexing using the SolrJ interface which uses HTTP queries under the hood:
 * http://wiki.apache.org/solr/Solrj
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SolrUserSearch extends SolrAbstractSearch {

    protected Logger logger = LoggerFactory.getLogger(getClass());
    private static final String TWEET = "tw";
    private static final String SCREEN_NAME = "name";

    public SolrUserSearch(String url) {
        createServer(url, null, null, false);
    }

    public SolrUserSearch(Configuration config) {
        createServer(config.getUserSearchUrl(), config.getUserSearchLogin(), config.getUserSearchPassword(), false);
    }

    public SolrUserSearch(SolrServer server) {
        setServer(server);
    }

    void delete(SolrUser user, boolean commit) {
        if (user.getScreenName() == null)
            throw new NullPointerException("Null " + SolrUser.SCREEN_NAME + " is not allowed! User:" + user);

        try {
            server.deleteById(user.getScreenName());
            if (commit)
                commit();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void update(Collection<SolrUser> users) {
        update(users, users.size());
    }

    /**
     * This method pushes the specified user in the specified batch size to solr.
     * If it is configured it will retry if one batch is failing.
     */
    public void update(Collection<SolrUser> users, int batchSize) {
        Collection<SolrInputDocument> list = new ArrayList<SolrInputDocument>();
        int usersProcessed = 0;
        int exceptionCounter = 0;
        int MAX_TIMEOUTS = 0;
        List<SolrUser> userList = new ArrayList<SolrUser>(users);

        while (usersProcessed < users.size()) {
            try {
                list.clear();
                for (int counter = 0;
                        counter + usersProcessed < userList.size()
                        && counter < batchSize; counter++) {

                    SolrInputDocument doc = createDoc(userList.get(counter + usersProcessed));
                    if (doc != null)
                        list.add(doc);
                }

                if (list.size() > 0)
                    server.add(list);

                // do not get into an infinity loop
                usersProcessed += batchSize;
            } catch (Exception e) {
                logger.warn("Exception while updating. users already processed: "
                        + usersProcessed + ". Error message: " + e.getMessage());
                if (exceptionCounter >= MAX_TIMEOUTS) {
                    exceptionCounter = 0;
                    usersProcessed += batchSize;
                } else
                    exceptionCounter++;
            }
        }
    }

    public void update(SolrUser user, boolean optimize, boolean commit) {
        save(user, commit);
    }

    void save(SolrUser user) {
        save(user, true);
    }

    public void save(SolrUser user, boolean commit) {
        try {
            SolrInputDocument doc = createDoc(user);
            if (doc != null)
                server.add(doc);
            if (commit)
                commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public SolrInputDocument createDoc(SolrUser u) throws IOException {
        // some users were only mentioned by others ...
        Collection<SolrTweet> tweets = u.getOwnTweets();
        if (tweets.size() == 0)
            return null;

        SolrInputDocument doc1 = new SolrInputDocument();
        // make sure that if we look for a specific user this user will show up first:
        doc1.addField(SCREEN_NAME, u.getScreenName());
        doc1.addField("realName", u.getRealName());
        doc1.addField("iconUrl", u.getProfileImageUrl());
        doc1.addField("webUrl", u.getWebUrl());
        doc1.addField("bio", u.getDescription());

        TweetDetector extractor = new TweetDetector(tweets);
        for (Entry<String, Integer> entry : extractor.run().getSortedTerms()) {
            if (entry.getValue() > termMinFrequency)
                doc1.addField("tag", entry.getKey());
        }

//        List<String> betterLang = extractor.filterLanguages(langMinFrequency);
//        for (String lang : betterLang) {
//            doc1.addField("lang", lang);
//        }

//        addReputation(doc1, u);

        // for date facetting
        if (tweets.size() > 0)
            doc1.addField("latestTw_dt", tweets.iterator().next().getCreatedAt());

        doc1.addField("tw_i", tweets.size());

        // main content: the tweets
        for (SolrTweet tw : tweets) {
            doc1.addField("tw", tw.getTwitterId() + "\t" + tw.getCreatedAt().getTime() + "\t" + tw.getText());
        }
        return doc1;
    }

    public SolrUser readDoc(final SolrDocument doc, Map<String, Map<String, List<String>>> hlt) {
        String name = (String) doc.getFieldValue(SCREEN_NAME);
        SolrUser user = new SolrUser(name);
        user.setRealName((String) doc.getFieldValue("realName"));
        user.setProfileImageUrl((String) doc.getFieldValue("iconUrl"));
        user.setWebUrl((String) doc.getFieldValue("webUrl"));
        user.setDescription((String) doc.getFieldValue("bio"));

        // only used for facet search? doc.getFieldValue("lang");        

        Collection<Object> tags = doc.getFieldValues("tag");
        if (tags != null)
            for (Object tag : tags) {
                user.addTag((String) tag);
            }


        // if highlighting
        if (hlt != null) {
            Map<String, List<String>> ret = hlt.get(user.getScreenName());
            List<String> tweets = ret.get(TWEET);
            // sometimes nothing was highlighted
            if (tweets != null)
                for (String text : tweets) {
                    readTweet(text, user);
                }
        } else {
            Collection<Object> tweetContent = doc.getFieldValues(TWEET);
            // users can have no tweets
            if (tweetContent == null)
                return user;

            for (Object text : tweetContent) {
                readTweet((String) text, user);
            }
        }

        return user;
    }

    public SolrTweet readTweet(String text, SolrUser user) {
        String attr[] = text.split("\t", 3);
        long twId = Long.parseLong(attr[0]);
        Date date = new Date(Long.parseLong(attr[1]));
        SolrTweet tw = new SolrTweet(twId, (String) attr[2], user);
        tw.setCreatedAt(date);
        return tw;
    }

    public SolrQuery createMltQuery(String queryStr) {
        UserQuery query = new UserQuery();

        // this does not work:
//        query.setQuery(queryStr).
//                setQueryType("standard").
//                set(MoreLikeThisParams.QF, SCREEN_NAME).
//                set(MoreLikeThisParams.MLT, "true").

        // TODO this won't use the dismax boosts!?
        query.setQuery(SCREEN_NAME + ":" + queryStr).
                setQueryType("/" + MoreLikeThisParams.MLT).
                set(MoreLikeThisParams.SIMILARITY_FIELDS, TWEET).
                set(MoreLikeThisParams.MIN_WORD_LEN, 2).
                set(MoreLikeThisParams.MIN_DOC_FREQ, 1).
                // don't return the user itself
                set(MoreLikeThisParams.MATCH_INCLUDE, false);

        // the following param could be a nice addition: show the user which terms are similar:
        // MoreLikeThisParams.INTERESTING_TERMS
        return query;
    }

    public SolrQuery attachHighlighting(SolrQuery query, int tweets) {
        query.setHighlight(true).
                addHighlightField(TWEET).
                // get the whole tweet == fragment
                setHighlightFragsize(0).
                // get the first two tweets
                setHighlightSnippets(tweets).
                setHighlightSimplePre("<b>").
                setHighlightSimplePost("</b>");

        // use the TWEET field as fallback if a snippet cannot created
        query. // set("hl.maxAnalyzedChars", 51200).
                set("hl.alternateField", TWEET);
        return query;
    }

    @Override
    public QueryResponse search(SolrQuery query) throws SolrServerException {
        return search(new ArrayList(), query);
    }

    public QueryResponse search(Collection<SolrUser> users, SolrQuery query) throws SolrServerException {
        QueryResponse rsp = server.query(query);
        SolrDocumentList docs = rsp.getResults();
        Map<String, Map<String, List<String>>> hlt = rsp.getHighlighting();

        for (SolrDocument sd : docs) {
            SolrUser u = readDoc(sd, hlt);
            users.add(u);
        }

        return rsp;
    }

//    public static boolean isMlt(SolrQuery lastQuery) {
//        String par = lastQuery.get(MoreLikeThisParams.MLT);
//        return par != null && par.equals("true");
//    }
    public boolean isMlt(SolrQuery lastQuery) {
        String qt = lastQuery.getQueryType();
        return qt != null && qt.contains(MoreLikeThisParams.MLT);
    }

    /** use createMltQuery + search instead */
    @Deprecated
    long searchMoreLikeThis(Collection<SolrUser> users, String qStr, int hitsPerPage,
            int page, boolean testing) throws SolrServerException {

        SolrQuery query = createMltQuery(qStr);
        attachPagability(query, page, hitsPerPage);
        if (!testing)
            query.set(MoreLikeThisParams.MIN_TERM_FREQ, 5);

        return search(users, query).getResults().getNumFound();
    }

    /** use createQuery + search instead */
    @Deprecated
    Collection<SolrUser> search(String string) throws SolrServerException {
        Set<SolrUser> ret = new LinkedHashSet<SolrUser>();
        search(ret, string, 10, 0);
        return ret;
    }

    /** use createQuery + search instead */
    @Deprecated
    long search(Collection<SolrUser> users, String qStr, int hitsPerPage, int page) throws SolrServerException {
        SolrQuery query = new UserQuery(qStr);
        attachPagability(query, page, hitsPerPage);
        QueryResponse rsp = search(users, query);
        return rsp.getResults().getNumFound();
    }
}
