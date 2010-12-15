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
import de.jetwick.tw.Extractor;
import de.jetwick.util.Helper;
import de.jetwick.data.UrlEntry;
import de.jetwick.tw.cmd.SerialCommandExecutor;
import de.jetwick.tw.cmd.TermCreateCommand;
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
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Querying + Indexing using the SolrJ interface which uses HTTP queries under the hood:
 * http://wiki.apache.org/solr/Solrj
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SolrTweetSearch extends SolrAbstractSearch {

    public static final String TWEET_TEXT = "tw";
    public static final String DATE = "dt";
    public static final String DATE_TAG = "{!tag=" + DATE + "}";
    public static final String IS_RT = "crt_b";
    public static final String FILTER_IS_NOT_RT = IS_RT + ":\"false\"";
    public static final String RT_COUNT = "retw_i";
    public static final String USER = "user";
    public static final String FILTER_KEY_USER = USER + ":";
    public static final String UPDATE_DT = "update_dt";
    public static final String INREPLY_ID = "inreply_l";
    public static final String DATE_START = "NOW/DAY-6DAYS";
    public static final String FILTER_VALUE_LATEST_DT = "[NOW/HOURS-8HOURS TO *]";
    public static final String FILTER_ENTRY_LATEST_DT = DATE + ":" + FILTER_VALUE_LATEST_DT;
    public static final String FILTER_VALUE_OLD_DT = "[* TO " + DATE_START + "]";
    public static final String FILTER_ENTRY_OLD_DT = DATE + ":" + FILTER_VALUE_OLD_DT;
    public static final String URL_COUNT = "url_i";
    public static final String FILTER_URL_ENTRY = URL_COUNT + ":[1 TO *]";
    public static final String FILTER_NO_URL_ENTRY = URL_COUNT + ":0";
    public static final String QUALITY = "quality_i";
    public static final String FILTER_NO_SPAM = QUALITY + ":[" + (SolrTweet.QUAL_SPAM + 1) + " TO *]";
    public static final String FILTER_SPAM = QUALITY + ":[* TO " + SolrTweet.QUAL_SPAM + "]";
    public static final String TAG = "tag";
    public static final String FIRST_URL_TITLE = "dest_title_1_s";
    protected Logger logger = LoggerFactory.getLogger(getClass());
    private static final int MAX_TWEETS_PER_REQ = 100;

    public SolrTweetSearch(String url) {
        createServer(url, null, null, false);
    }

    public SolrTweetSearch(Configuration config) {
        createServer(config.getTweetSearchUrl(), config.getTweetSearchLogin(), config.getTweetSearchPassword(), config.getTweetStreamingServer());
    }

    public SolrTweetSearch(String url, String login, String pw, boolean streaming) {
        createServer(url, login, pw, streaming);
    }

    public SolrTweetSearch(SolrServer server) {
        setServer(server);
    }

    public void deleteUsers(Collection<String> users) {
        if (users.size() == 0)
            return;

        try {
            for (String u : users) {
                server.deleteByQuery("user:" + u.toLowerCase());
            }

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void delete(String twitterId) {
        try {
            server.deleteById(Arrays.asList(twitterId));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void delete(Collection<SolrTweet> tws) {
        if (tws.size() == 0)
            return;

        try {
            List<String> idList = new ArrayList<String>();
            for (SolrTweet tw : tws) {
                idList.add(Long.toString(tw.getTwitterId()));
            }
            server.deleteById(idList);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public SolrInputDocument createDoc(SolrTweet tw) throws IOException {
        if (tw.getFromUser() == null) {
            // this came from UpdateResult.addNewTweet(tweet1); UpdateResult.addRemovedTweet(tweet1) at the same time
            // but should be fixed via if (!removedTweets.contains(tweet)) newTweets.add(tweet);
            logger.error("fromUser of tweet must not be null:" + tw.getTwitterId() + " " + tw.getText());
            return null;
        }

        // daemon tweets have no known twitterId and no known createdAt date
        if (tw.isDaemon())
            return null;

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", tw.getTwitterId());
        doc.addField(TWEET_TEXT, tw.getText());
        doc.addField("tw_i", tw.getText().length());
        // TODO reduce to minute-precision
        doc.addField(DATE, tw.getCreatedAt());
        doc.addField(UPDATE_DT, tw.getUpdatedAt());
        doc.addField(IS_RT, tw.isRetweet());

        if (tw.getLocation() == null)
            doc.addField("loc", tw.getFromUser().getLocation());
        else
            doc.addField("loc", tw.getLocation());

        if (!SolrTweet.isDefaultInReplyId(tw.getInReplyTwitterId()))
            doc.addField(INREPLY_ID, tw.getInReplyTwitterId());

        doc.addField("user", tw.getFromUser().getScreenName());
        doc.addField("iconUrl", tw.getFromUser().getProfileImageUrl());

        for (Entry<String, Integer> entry : tw.getTextTerms().entrySet()) {
            doc.addField(TAG, entry.getKey());
        }

        int counter = 0;
        for (UrlEntry urlEntry : tw.getUrlEntries()) {
            if (!urlEntry.getResolvedTitle().isEmpty()
                    && !urlEntry.getResolvedUrl().isEmpty()) {
                counter++;
                doc.addField("url_pos_" + counter + "_s", urlEntry.getIndex() + "," + urlEntry.getLastIndex());
                doc.addField("dest_url_" + counter + "_s", urlEntry.getResolvedUrl());
                doc.addField("dest_domain_" + counter + "_s", urlEntry.getResolvedDomain());
                doc.addField("dest_title_" + counter + "_s", urlEntry.getResolvedTitle());
                if (counter >= 3)
                    break;
            }
        }

        doc.addField("url_i", counter);
        doc.addField("lang", tw.getLanguage());
        doc.addField("quality_i", tw.getQuality());
//        doc.addField("qual_debug_s", tw.getQualDebug());
        doc.addField("repl_i", tw.getReplyCount());
        doc.addField(RT_COUNT, tw.getRetweetCount());

        return doc;
    }

    public SolrTweet readDoc(final SolrDocument doc, Map<String, Map<String, List<String>>> hlt) {
        String name = (String) doc.getFieldValue("user");
        SolrUser user = new SolrUser(name);
        user.setLocation((String) doc.getFieldValue("loc"));
        user.setProfileImageUrl((String) doc.getFieldValue("iconUrl"));

        long id = (Long) doc.getFieldValue("id");
        String text = (String) doc.getFieldValue(TWEET_TEXT);
        SolrTweet tw = new SolrTweet(id, text, user);

        tw.setCreatedAt((Date) doc.getFieldValue(DATE));
        tw.setUpdatedAt((Date) doc.getFieldValue(UPDATE_DT));
        int rt = ((Number) doc.getFieldValue(RT_COUNT)).intValue();
        int rp = ((Number) doc.getFieldValue("repl_i")).intValue();
        tw.setRt(rt);
        tw.setReply(rp);

        if (doc.getFieldValue("quality_i") != null)
            tw.setQuality(((Number) doc.getFieldValue("quality_i")).intValue());

        Long replyId = (Long) doc.getFieldValue(INREPLY_ID);
        if (replyId != null)
            tw.setInReplyTwitterId(replyId);

        tw.setUrlEntries(Arrays.asList(parseUrlEntries(doc)));
        return tw;
    }

    public UrlEntry[] parseUrlEntries(SolrDocument doc) {
        int urlCount = 0;
        try {
            urlCount = ((Number) doc.getFieldValue("url_i")).intValue();
        } catch (Exception ex) {
        }

        if (urlCount == 0)
            return new UrlEntry[0];

        //for backward compatibility
        if (doc.getFieldValue("url_pos_1_s") == null)
            return new UrlEntry[0];

        UrlEntry urls[] = new UrlEntry[urlCount];
        for (int i = 0; i < urls.length; i++) {
            urls[i] = new UrlEntry();
        }

        for (int counter = 0; counter < urls.length; counter++) {
            String str = (String) doc.getFieldValue("url_pos_" + (counter + 1) + "_s");
            String strs[] = (str).split(",");
            urls[counter].setIndex(Integer.parseInt(strs[0]));
            urls[counter].setLastIndex(Integer.parseInt(strs[1]));
        }

        for (int counter = 0; counter < urls.length; counter++) {
            String str = (String) doc.getFieldValue("dest_url_" + (counter + 1) + "_s");
            urls[counter].setResolvedUrl(str);
        }

        for (int counter = 0; counter < urls.length; counter++) {
            String str = (String) doc.getFieldValue("dest_domain_" + (counter + 1) + "_s");
            urls[counter].setResolvedDomain(str);
        }

        for (int counter = 0; counter < urls.length; counter++) {
            String str = (String) doc.getFieldValue("dest_title_" + (counter + 1) + "_s");
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

            QueryResponse rsp = search(q);
            long results = rsp.getResults().getNumFound();
            if (results == 0)
                return new JetwickQuery(tag);

            resQuery.addFilterQuery(IS_RT + ":\"false\"");
            TweetQuery.setSort(resQuery, "dt asc");

            int counter = 0;
            for (Entry<String, Integer> entry : orderedFQ.entrySet()) {
                Integer integ = rsp.getFacetQuery().get(entry.getKey());
                counter += integ;
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

    public SolrQuery attachHighlighting(SolrQuery query) {
//        query.setHighlight(true).
//                addHighlightField(TWEET_TEXT).
//                // get the whole tweet == fragment
//                setHighlightFragsize(0).
//                setHighlightSnippets(1).
//                setHighlightSimplePre("<b>").
//                setHighlightSimplePost("</b>");
//
//        // use the TEXT field as fallback if a snippet cannot created
//        query. // set("hl.maxAnalyzedChars", 51200).
//                set("hl.alternateField", TWEET_TEXT);
        return query;
    }

    Collection<SolrUser> search(String str) throws SolrServerException {
        List<SolrUser> user = new ArrayList<SolrUser>();
        search(user, new TweetQuery(str));
        return user;
    }

    @Override
    public QueryResponse search(SolrQuery query) throws SolrServerException {
        return search(new ArrayList(), query);
    }

    public QueryResponse search(Collection<SolrUser> users, SolrQuery query) throws SolrServerException {
        QueryResponse rsp = server.query(query);
        SolrDocumentList docs = rsp.getResults();
        Map<String, Map<String, List<String>>> hlt = rsp.getHighlighting();
        Map<String, SolrUser> usersMap = new LinkedHashMap<String, SolrUser>();

        for (SolrDocument sd : docs) {
            SolrUser u = readDoc(sd, hlt).getFromUser();
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
            SolrQuery sq = new SolrQuery().addFilterQuery("crt_b:" + retweet).addFilterQuery("inreply_l:" + id);
            QueryResponse rsp = server.query(sq);
            return collectTweets(rsp);
        } catch (Exception ex) {
            return Collections.EMPTY_SET;
        }
    }

    void update(SolrTweet tweet, boolean commit) {
        try {
            SolrInputDocument doc = createDoc(tweet);
            if (doc != null)
                server.add(doc);
            if (commit) {
                // 'waitFlush' means wait until the data from this commit is completely written to disk
                // 'waitSearcher' means wait until Solr has completely finished loading up the new index from what it wrote to disk.
                server.commit(true, true);
            }
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

            Iterator<SolrTweet> iter = tmpTweets.iterator();
            while (iter.hasNext()) {
                StringBuilder idStr = new StringBuilder();
                int counts = 0;
                // we can add max ~150 tweets per request (otherwise the webcontainer won't handle the long request)
                for (int i = 0; i < MAX_TWEETS_PER_REQ && iter.hasNext(); i++) {
                    SolrTweet tw = iter.next();
                    counts++;
                    idStr.append("id:");
                    idStr.append(tw.getTwitterId());
                    idStr.append(" ");
                }

                // get existing tweets and users
                SolrQuery query = new SolrQuery().addFilterQuery(idStr.toString()).setRows(counts);
                QueryResponse rsp = search(query);
                SolrDocumentList docs = rsp.getResults();

                for (SolrDocument sd : docs) {
                    SolrTweet tw = readDoc(sd, null);
                    existingTweets.put(tw.getTwitterId(), tw);
                    SolrUser u = tw.getFromUser();
                    SolrUser uOld = usersMap.get(u.getScreenName());
                    if (uOld == null)
                        usersMap.put(u.getScreenName(), u);
                    else
                        uOld.addOwnTweet(u.getOwnTweets().iterator().next());
                }
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

            // add the additionally fetched tweets to the user but do not add to updateTweets
            // this is a bit expensive ~30-40sec for every update call on a large index!
//            fetchMoreTweets(twMap, usersMap);

            update(updateTweets);

            // We are not receiving the deleted tweets! but do we need to
            // update the tweets where this deleted tweet was a retweet?
            // No. Because "userA: text" and "userB: RT @usera: text" now the second tweet is always AFTER the first!
            UpdateResponse ursp = server.deleteByQuery("-" + UPDATE_DT + ":[* TO *] AND "
                    + DATE + ":[* TO " + Helper.toLocalDateTime(removeUntil) + "/DAY]");

            UpdateResponse crsp = server.commit();
//            logger.info("[solr] time deleted: " + ursp.getElapsedTime() / 1000.0f + " commited:" + crsp.getElapsedTime() / 1000.0f);
            return updateTweets;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void update(Collection<SolrTweet> tweets) {
        try {
            tweets = new SerialCommandExecutor(tweets).add(
                    new TermCreateCommand()).execute();
            for (SolrTweet tw : tweets) {
                server.add(createDoc(tw));
            }
        } catch (Exception e) {
            logger.warn("Exception while updating. Error message: " + e.getMessage());
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
                QueryResponse rsp = search(query);
                SolrDocumentList docs = rsp.getResults();
                for (SolrDocument sd : docs) {
                    SolrTweet tw = readDoc(sd, null);
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

    public SolrTweet connectToOrigTweet(SolrTweet tw, String toUserStr) {
        if (tw.isRetweet() && SolrTweet.isDefaultInReplyId(tw.getInReplyTwitterId())) {
            // connect retweets to tweets only searchTweetsDays old

            try {
                QueryResponse qrsp = server.query(new SolrQuery("\"" + tw.extractRTText() + "\"").addFilterQuery("user:" + toUserStr).setRows(10));
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
                logger.error("couldn't connect to orig tweet:" + ex.getMessage());
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
        while (iter.hasNext()) {
            findRepliesInBatch(iter, tweets, replyMap, updatedTweets);
        }
        return updatedTweets;
    }

    protected void findRepliesInBatch(Iterator<SolrTweet> iter, Map<Long, SolrTweet> origTweets,
            Map<Long, SolrTweet> replyIdToTweetMap, Collection<SolrTweet> updatedTweets) {
        int counter = 0;
        StringBuilder idStr = new StringBuilder();
        StringBuilder replyIdStr = new StringBuilder();
        for (int i = 0; i < MAX_TWEETS_PER_REQ && iter.hasNext(); i++) {
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
                replyIdStr.append(" ");
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
                idStr.append("id:");
                idStr.append(tw.getInReplyTwitterId());
                idStr.append(" ");
            }
        }

        try {
            // get tweets which replies our existing tweets
            // INREPLY_ID:"tweets[i].id"
            SolrQuery query = new SolrQuery().addFilterQuery(replyIdStr.toString()).setRows(origTweets.size());
            findRepliesForOriginalTweets(query, origTweets, updatedTweets);

            // get original tweets where we have replies
            query = new SolrQuery().addFilterQuery(idStr.toString()).setRows(counter);
            selectOriginalTweetsWithReplies(query, origTweets.values(), updatedTweets);
        } catch (Exception ex) {
            logger.error("couldn't find replies in batch:" + ex.getMessage());
        }
    }

    protected void findRepliesForOriginalTweets(SolrQuery query, Map<Long, SolrTweet> tweets,
            Collection<SolrTweet> updatedTweets) throws SolrServerException {

        Map<Long, SolrTweet> replyMap = new LinkedHashMap<Long, SolrTweet>();
        QueryResponse rsp = server.query(query);
        SolrDocumentList docs = rsp.getResults();

        for (SolrDocument sd : docs) {
            SolrTweet tw = readDoc(sd, null);
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
            Collection<SolrTweet> updatedTweets) throws SolrServerException {

        QueryResponse rsp = server.query(query);
        SolrDocumentList docs = rsp.getResults();
        Map<Long, SolrTweet> origMap = new LinkedHashMap<Long, SolrTweet>();
        for (SolrDocument sd : docs) {
            SolrTweet tw = readDoc(sd, null);
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
                    addFilterQuery("-id:" + reply.getTwitterId()).
                    addFilterQuery("user:" + reply.getFromUser().getScreenName());
            if (server.query(q).getResults().getNumFound() > 0)
                return false;

            orig.addReply(reply);
            return true;
        } catch (Exception ex) {
            logger.error("couldn't addReply:" + ex.getMessage());
            return false;
        }
    }

    public List<SolrTweet> collectTweets(QueryResponse rsp) {
        SolrDocumentList docs = rsp.getResults();
        List<SolrTweet> list = new ArrayList<SolrTweet>();

        for (SolrDocument sd : docs) {
            list.add(readDoc(sd, null));
        }

        return list;
    }

    public SolrTweet findByTwitterId(Long twitterId) {
        try {
            // set rows to 2 to check uniqueness
            SolrQuery sq = JetwickQuery.createIdQuery(twitterId);
            QueryResponse rsp = search(sq);
            List<SolrTweet> list = collectTweets(rsp);
            if (list.size() > 1)
                throw new IllegalStateException("Not only one document found for twitter id:" + twitterId + " . " + list.size());
            if (list.size() == 0)
                return null;

            return list.get(0);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public Collection<String> getUserChoices(SolrQuery lastQ, String input) {
        try {
            if (input.length() < 2)
                return Collections.emptyList();

            if (lastQ == null)
                lastQ = new TweetQuery("");
            else {
                lastQ = lastQ.getCopy();
                // remove existing user filter
                JetwickQuery.applyFacetChange(lastQ, "user", true);
                // remove any date restrictions
                JetwickQuery.applyFacetChange(lastQ, "dt", true);
            }

            input = input.toLowerCase();
            lastQ.addFilterQuery("user:" + input + "*");
            lastQ.setRows(15);
            List<SolrUser> users = new ArrayList<SolrUser>();
            search(users, lastQ);
            Set<String> res = new TreeSet<String>();
            for (SolrUser u : users) {
                if (u.getScreenName().startsWith(input))
                    res.add(u.getScreenName());

                if (res.size() > 9)
                    break;
            }

            return res;
        } catch (Exception ex) {
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
                lastQ = new TweetQuery(firstPart);
            else {
                lastQ = lastQ.getCopy().setQuery(firstPart);
                // remove any date restrictions
                JetwickQuery.applyFacetChange(lastQ, "dt", true);
            }

            if (!secPart.trim().isEmpty())
                lastQ.setFacetPrefix(TAG, secPart);

            lastQ.setRows(0);
            lastQ.set("f.tag.facet.limit", 15);
            QueryResponse rsp = search(lastQ);
            logger.info(lastQ.toString());
            Set<String> res = new TreeSet<String>();
            if (rsp.getFacetField(TAG) != null && rsp.getFacetField(TAG).getValues() != null) {
                for (Count cnt : rsp.getFacetField(TAG).getValues()) {
                    String lowerSugg = cnt.getName().toLowerCase();
                    if (existingTerms.contains(lowerSugg))
                        continue;

                    if (lowerSugg.startsWith(secPart)) {
                        if (firstPart.isEmpty())
                            res.add(cnt.getName());
                        else
                            res.add(firstPart + " " + cnt.getName());
                    }

                    if (res.size() > 9)
                        break;
                }
            }

            return res;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    SolrUser findByUserName(String uName) {
        try {
            List<SolrUser> list = new ArrayList<SolrUser>();
            // get all tweets of the user so set rows large ...
            search(list, new SolrQuery().addFilterQuery("user:" + uName.toLowerCase()).setRows(10));

            if (list.size() == 0)
                return null;

            return list.get(0);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    long countAll() {
        try {
            return server.query(new SolrQuery()).getResults().getNumFound();
        } catch (SolrServerException ex) {
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

    public Collection<SolrTweet> searchAds(String query) throws SolrServerException {
        query = query.trim();
        if (query.isEmpty())
            return Collections.EMPTY_LIST;

        Collection<SolrUser> users = new LinkedHashSet<SolrUser>();        
        search(users, new SolrQuery(query).addFilterQuery("tw:#jetwick").
                addFilterQuery(RT_COUNT + ":[1 TO *]").
                addFilterQuery(QUALITY + ":[90 TO 100]").
                addFilterQuery(IS_RT + ":false").
                addFilterQuery(DATE + ":[NOW/DAY-3DAYS TO NOW]").
                setSortField(RT_COUNT, SolrQuery.ORDER.desc));

        Set<SolrTweet> res = new LinkedHashSet<SolrTweet>();
        for (SolrUser u : users) {
            if (u.getOwnTweets().size() > 0)
                res.add(u.getOwnTweets().iterator().next());
        }
        return res;
    }
}
