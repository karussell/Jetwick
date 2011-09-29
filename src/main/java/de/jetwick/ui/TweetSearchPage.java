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
package de.jetwick.ui;

import de.jetwick.tw.MyTweetGrabber;
import com.google.inject.Inject;
import com.google.inject.Provider;
import de.jetwick.data.UrlEntry;
import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.es.JetwickQuery;
import de.jetwick.es.SavedSearch;
import de.jetwick.es.SimilarTweetQuery;
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
import de.jetwick.es.TweetQuery;
import de.jetwick.tw.TwitterSearch;
import de.jetwick.tw.queue.QueueThread;
import de.jetwick.ui.jschart.JSDateFilter;
import de.jetwick.util.Helper;
import de.jetwick.util.StopWatch;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.apache.wicket.PageParameters;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.request.target.basic.RedirectRequestTarget;
import org.elasticsearch.action.search.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterException;

/**
 * TODO clean up this bloated class
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetSearchPage extends JetwickPage {
    
    private static final long serialVersionUID = 1L;    
    public static final String Q = "query";
    public static final String CRAWLER = "crawler";
    public static final String PASSWORD = "pw";
    public static final String PASSWORD_CORRECT = "geeknews";
    public static final String TIME = "time";
    public static final String TIME_TODAY = "today";
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private JetwickQuery lastQuery;
    private int hitsPerPage = 15;
    private FeedbackPanel feedbackPanel;
    private ResultsPanel resultsPanel;
    private FacetPanel facetPanel;
    private SavedSearchPanel ssPanel;
    private TagCloudPanel tagCloudPanel;
    private NavigationPanel navigationPanel;
    private SearchBox searchBox;
    private String language = "en";
    private String remoteHost = "";
//    private WikipediaLazyLoadPanel wikiPanel;
    private UrlTrendPanel urlTrends;
    @Inject
    private Provider<ElasticTweetSearch> twindexProvider;
    @Inject
    private MyTweetGrabber grabber;
    private boolean twitterFallback = false;
    private JSDateFilter dateFilterPanel;
    private transient Thread tweetThread;
    private static int TWEETS_IF_HIT = 30;
    private static int TWEETS_IF_NO_HIT = 40;
    private String userName = "";

    // for testing
    TweetSearchPage() {
    }

    TweetSearchPage(JetwickQuery q) {
        init(q, PageParameters.NULL);
    }

    public TweetSearchPage(final PageParameters parameters) {
        super.init(parameters);
    }

    @Override
    protected void configureResponse() {
        super.configureResponse();
        super.myConfigureResponse();
    }

    public ElasticTweetSearch getTweetSearch() {
        return twindexProvider.get();
    }

    public Thread getQueueThread() {
        return tweetThread;
    }

    @Override
    public JetwickQuery createQuery(PageParameters parameters) {
        // TODO M2.1 parameters.get("hits").toString can cause NPE!!
        String hitsStr = parameters.getString("hits");
        if (hitsStr != null) {
            try {
                hitsPerPage = Integer.parseInt(hitsStr);
                hitsPerPage = Math.min(100, hitsPerPage);
            } catch (Exception ex) {
                logger.warn("Couldn't parse hits per page:" + hitsStr + " " + ex.getMessage());
            }
        }

        String idStr = parameters.getString("id");
        JetwickQuery q = null;

        if (idStr != null) {
            try {
                int index = idStr.lastIndexOf("/");
                if (index > 0 && index + 1 < idStr.length())
                    idStr = idStr.substring(index + 1);

                q = new TweetQuery(true).addFilterQuery(ElasticTweetSearch._ID + "tweet", Long.parseLong(idStr));
            } catch (Exception ignore) {
            }
        }

        if (q == null) {
            String originStr = parameters.getString("findOrigin");
            if (originStr != null) {
                logger.info("[stats] findOrigin from lastQuery:" + lastQuery);
                q = getTweetSearch().createFindOriginQuery(lastQuery, originStr, 3);
            }
        }

        String queryStr = parameters.getString("q");        
        if(Helper.isEmpty(queryStr))
            queryStr = parameters.getString("query");
        if (queryStr == null)
            queryStr = "";
        
        if(queryStr.contains("*")) {
            warn("Cannot process query with asterisk");            
            queryStr = "";
        }

        userName = "";
        if (q == null) {
            userName = parameters.getString("u");
            if (userName == null)
                userName = parameters.getString("user");

            if (userName == null)
                userName = "";
            q = new TweetQuery(queryStr);
            if (!Helper.isEmpty(userName))
                q.addUserFilter(userName);
        }

        String fromDateStr = parameters.getString("until");
        if (fromDateStr != null) {
            if (!fromDateStr.contains("T"))
                fromDateStr += "T00:00:00Z";

            q.addFilterQuery(ElasticTweetSearch.DATE, "[" + fromDateStr + " TO *]");
        }

        // front page/empty => sort against relevance
        // user search    => sort against latest date
        // other        => sort against retweets if no sort specified

        String sort = parameters.getString("sort");
        if ("retweets".equals(sort))
            q.setSort(ElasticTweetSearch.RT_COUNT, "desc");
        else if ("latest".equals(sort))
            q.setSort(ElasticTweetSearch.DATE, "desc");
        else if ("oldest".equals(sort))
            q.setSort(ElasticTweetSearch.DATE, "asc");
        else if ("relevance".equals(sort))
            q.setSort(ElasticTweetSearch.RELEVANCE, "desc");
        else {
            q.setSort(ElasticTweetSearch.RT_COUNT, "desc");

            if (!Helper.isEmpty(userName))
                q.setSort(ElasticTweetSearch.DATE, "desc");
        }

        // front page: avoid slow queries for matchall query and filter against latest tweets only
        if (queryStr.isEmpty() && q.getFilterQueries().isEmpty() && fromDateStr == null) {
            logger.info(addIP("[stats] q=''"));
            q.addLatestDateFilter(8);
            if (Helper.isEmpty(sort))
                q.setSort(ElasticTweetSearch.RELEVANCE, "desc");
        }

        String filter = parameters.getString("filter");
        if (Helper.isEmpty(userName) && !"none".equals(filter)) {
            q.addNoSpamFilter().addNoDupsFilter().addIsOriginalTweetFilter().addLatestDateFilter(24 * 7);
        }
        return q;
    }

    public void updateAfterAjax(AjaxRequestTarget target, boolean updateSearchBox) {
        if (target != null) {
            target.addComponent(facetPanel);
            target.addComponent(resultsPanel);
            target.addComponent(navigationPanel);

            //already in resultsPanel target.addComponent(lazyLoadAdPanel);           
            if (updateSearchBox)
                target.addComponent(searchBox);
            target.addComponent(tagCloudPanel);
            target.addComponent(dateFilterPanel);
            target.addComponent(urlTrends);
            target.addComponent(feedbackPanel);
            target.addComponent(ssPanel);

            // no ajax for wikipedia to avoid requests
            //target.addComponent(wikiPanel);

            // this does not work (scroll to top)
//            target.focusComponent(searchBox);
        }
    }

    public void setTwitterFallback(boolean twitterFallback) {
        this.twitterFallback = twitterFallback;
    }

    @Override
    public void init(JetwickQuery query, final PageParameters parameters) {
        feedbackPanel = new FeedbackPanel("feedback");
        add(feedbackPanel.setOutputMarkupId(true));
        add(new Label("title", new Model() {

            @Override
            public Serializable getObject() {
                String str = "";
                if (!searchBox.getQuery().isEmpty())
                    str += searchBox.getQuery() + " ";
                if (!searchBox.getUserName().isEmpty()) {
                    if (str.isEmpty())
                        str = "User " + searchBox.getUserName() + " ";
                    else
                        str = "Search " + str + "in user " + searchBox.getUserName() + " ";
                }

                if (str.isEmpty())
                    return "Jetwick Twitter Search";

                return "Jetwick | " + str + "| Twitter Search Without Noise";
            }
        }));

        add(new ExternalLinksPanel("externalRefs"));
        add(new ExternalLinksPanelRight("externalRefsRight"));

        urlTrends = new UrlTrendPanel("urltrends") {

            @Override
            protected void onUrlClick(AjaxRequestTarget target, String name) {
                JetwickQuery q;
                if (lastQuery != null)
                    q = lastQuery;
                else
                    q = new TweetQuery(true);

                if (name == null) {
                    q.removeFilterQueries(ElasticTweetSearch.FIRST_URL_TITLE);
                } else
                    q.addFilterQuery(ElasticTweetSearch.FIRST_URL_TITLE, name);

                doSearch(q, 0, true);
                updateAfterAjax(target, false);
            }

            @Override
            protected void onDirectUrlClick(AjaxRequestTarget target, String name) {
                if (lastQuery == null || name == null || name.isEmpty())
                    return;

                TweetQuery q = new TweetQuery(true);
                q.addFilterQuery(ElasticTweetSearch.FIRST_URL_TITLE, name);
                try {
                    List<JTweet> tweets = getTweetSearch().collectObjects(getTweetSearch().query(q.setSize(1)));
                    if (tweets.size() > 0 && tweets.get(0).getUrlEntries().size() > 0) {
                        // TODO there could be more than 1 url!
                        UrlEntry entry = tweets.get(0).getUrlEntries().iterator().next();
                        getRequestCycle().setRequestTarget(new RedirectRequestTarget(entry.getResolvedUrl()));
                    }
                } catch (Exception ex) {
                    logger.error("Error while executing onDirectUrlClick", ex);
                }
            }
        };
        add(urlTrends.setOutputMarkupId(true));

        ssPanel = new SavedSearchPanel("savedSearches") {

            @Override
            public void onClick(AjaxRequestTarget target, long ssId) {
                String searchType = parameters.getString("search");
                if (searchType != null && !searchType.isEmpty() && !SearchBox.ALL.equals(searchType)) {
                    warn("Removed user filter when executing your saved search");
                    searchBox.setSearchType(SearchBox.ALL);
                }
                JUser user = getMySession().getUser();
                SavedSearch ss = user.getSavedSearch(ssId);
                if (ss != null) {
                    doSearch(ss.getQuery(), 0, true);
                    uindexProvider.get().save(user, true);
                }
                updateSSCounts(target);
                updateAfterAjax(target, true);
            }

            @Override
            public void onRemove(AjaxRequestTarget target, long ssId) {
                JUser user = getMySession().getUser();
                user.removeSavedSearch(ssId);
                uindexProvider.get().save(user, true);
                updateSSCounts(target);
            }

            @Override
            public void onSave(AjaxRequestTarget target, long ssId) {
                if (lastQuery == null)
                    return;

                SavedSearch ss = new SavedSearch(ssId, lastQuery);
                JUser user = getMySession().getUser();
                user.addSavedSearch(ss);
                uindexProvider.get().save(user, true);
                updateSSCounts(target);
            }

            @Override
            public void updateSSCounts(AjaxRequestTarget target) {
                try {
                    JUser user = getMySession().getUser();
                    if (user != null) {
                        StopWatch sw = new StopWatch().start();
                        update(getTweetSearch().updateSavedSearches(user.getSavedSearches()));
                        if (target != null)
                            target.addComponent(ssPanel);
                        logger.info("Updated saved search counts for " + user.getScreenName() + " " + sw.stop().getSeconds());
                    }
                } catch (Exception ex) {
                    logger.error("Error while searching in savedSearches", ex);
                }
            }

            @Override
            public String translate(long id) {
                SavedSearch ss = getMySession().getUser().getSavedSearch(id);
                return ss.getName();
            }
        };

        add(ssPanel.setOutputMarkupId(true));

        add(new UserPanel("userPanel", this) {

            @Override
            public void onLogout() {
                getMySession().logout(uindexProvider.get(), (WebResponse) getResponse(), true);
                setResponsePage(TweetSearchPage.class, parameters);
            }

            @Override
            public void updateAfterAjax(AjaxRequestTarget target) {
                TweetSearchPage.this.updateAfterAjax(target, false);
            }

            @Override
            public void onHomeline(AjaxRequestTarget target, String user) {
                searchBox.setSearchType(SearchBox.FRIENDS);
                doSearch(createFriendQuery(""), 0, false);
                TweetSearchPage.this.updateAfterAjax(target, true);
            }

            @Override
            public void onShowTweets(AjaxRequestTarget target, String userName) {
                searchBox.setSearchType(SearchBox.USER);
                doSearch(new TweetQuery(true).addUserFilter(userName).setSort(ElasticTweetSearch.DATE, "desc"), 0, false);
                TweetSearchPage.this.updateAfterAjax(target, true);
            }

            @Override
            protected Collection<String> getUserChoices(String input) {
                return getTweetSearch().getUserChoices(lastQuery, input);
            }
        });

        tagCloudPanel = new TagCloudPanel("tagcloud") {

            @Override
            protected void onTagClick(String name) {
                if (lastQuery != null) {
                    lastQuery.setQuery((lastQuery.getQuery() + " " + name).trim());
                    doSearch(lastQuery, 0, true);
                } else {
                    // never happens?
                    PageParameters pp = new PageParameters();
                    pp.add("q", name);
                    setResponsePage(TweetSearchPage.class, pp);
                }
            }

            @Override
            protected void onFindOriginClick(String tag) {
                PageParameters pp = new PageParameters();
                pp.add("findOrigin", tag);

                doSearch(createQuery(pp), 0, true);
                // this preserves parameters but cannot be context sensitive!
//                setResponsePage(TweetSearchPage.class, pp);
            }
        };
        add(tagCloudPanel.setOutputMarkupId(true));

        navigationPanel = new NavigationPanel("navigation", hitsPerPage) {

            @Override
            public void onPageChange(AjaxRequestTarget target, int page) {
                // this does not scroll to top:
//                doOldSearch(page);
//                updateAfterAjax(target);

                doOldSearch(page);
            }
        };
        add(navigationPanel.setOutputMarkupId(true));

        facetPanel = new FacetPanel("filterPanel") {

            @Override
            public void onRemoveAllFilter(AjaxRequestTarget target, String key) {
                if (lastQuery != null)
                    lastQuery.removeFilterQueries(key);
                else {
                    logger.error("last query cannot be null but was! ... when clicking on facets!?");
                    return;
                }

                doOldSearch(0);
                updateAfterAjax(target, false);
            }

            @Override
            public void onFilterChange(AjaxRequestTarget target, String key, Object val, Boolean selected) {
                if (lastQuery != null) {
                    if (selected == null)
                        lastQuery.removeFilterQuery(key, val);
                    else if (selected)
                        lastQuery.addFilterQuery("-" + key, val);
                    else
                        lastQuery.addFilterQuery(key, val);
                } else {
                    logger.error("last query cannot be null but was! ... when clicking on facets!?");
                    return;
                }

                doOldSearch(0);
                updateAfterAjax(target, false);
            }
        };
        add(facetPanel.setOutputMarkupId(true));

        dateFilterPanel = new JSDateFilter("dateFilter") {

            @Override
            protected void onFilterChange(AjaxRequestTarget target, String filter, Boolean selected) {
                if (lastQuery != null) {
                    if (selected == null) {
                        lastQuery.removeFilterQueries(filter);
                    } else if (selected) {
                        lastQuery.replaceFilterQuery(filter);
                    } else
                        lastQuery.reduceFilterQuery(filter);
                } else {
                    logger.error("last query cannot be null but was! ... when clicking on facets!?");
                    return;
                }

                doOldSearch(0);
                updateAfterAjax(target, false);
            }

            @Override
            protected boolean isAlreadyFiltered(String key, Object val) {
                if (lastQuery != null)
                    return lastQuery.containsFilter(key, val);

                return false;
            }

            @Override
            public String getFilterName(String key) {
                return facetPanel.getFilterName(key);
            }
        };
        add(dateFilterPanel.setOutputMarkupId(true));

        // TODO M2.1
        language = getWebRequestCycle().getWebRequest().getHttpServletRequest().getLocale().getLanguage();
        remoteHost = getWebRequestCycle().getWebRequest().getHttpServletRequest().getRemoteHost();
        resultsPanel = new ResultsPanel("results", language) {

            @Override
            public void onSortClicked(AjaxRequestTarget target, String sortKey, String sortVal) {
                if (lastQuery != null) {
                    lastQuery.setSort(sortKey, sortVal);
                    doSearch(lastQuery, 0, false);
                    updateAfterAjax(target, false);
                }
            }

            @Override
            public void onUserClick(String userName, String queryStr) {
                PageParameters p = new PageParameters();
                if (queryStr != null && !queryStr.isEmpty())
                    p.add("q", queryStr);
                if (userName != null) {
                    p.add("user", userName.trim());
                    searchBox.setSearchType(SearchBox.USER);
                }

                doSearch(createQuery(p), 0, true);
            }

            @Override
            public Collection<JTweet> onTweetClick(long id, boolean retweet) {
                logger.info("[stats] search replies of:" + id + " retweet:" + retweet);
                return getTweetSearch().searchReplies(id, retweet);
            }

            @Override
            protected void onRetweet(JTweet tweet, AjaxRequestTarget target) {
                if (getMySession().hasLoggedIn())
                    try {
                        getTwitterSearch().doRetweet(tweet.getTwitterId());
                        info("Retweeted " + tweet.getFromUser().getScreenName() + " by " + getTwitterSearch().getUser());
                    } catch (Exception ex) {
                        error("Cannot retweet " + tweet.getFromUser().getScreenName() + ". "
                                + "Problems with twitter. Please try again.");
                    }
                else
                    error("Please login.");
                updateAfterAjax(target, false);
            }

            @Override
            public void onFindSimilar(JTweet tweet, AjaxRequestTarget target) {
                JetwickQuery query = new SimilarTweetQuery(tweet, true);
                if (tweet.getTextTerms().size() == 0) {
                    warn("Try a different tweet. This tweet is too short.");
                    return;
                }

                logger.info("[stats] similar search:" + query.toString());
                doSearch(query, 0, false);
                updateAfterAjax(target, false);
            }

            @Override
            public Collection<JTweet> onInReplyOfClick(long id) {
                JTweet tw = getTweetSearch().findByTwitterId(id);
                logger.info("[stats] search tweet:" + id + " " + tw);
                if (tw != null)
                    return Arrays.asList(tw);
                else
                    return new ArrayList();
            }

            @Override
            public String getTweetsAsString() {
                if (lastQuery != null)
                    return twindexProvider.get().getTweetsAsString(lastQuery, "\t");

                return "";
            }

            @Override
            public void onHtmlExport() {
                if (lastQuery != null) {
                    PrinterPage printerPage = new PrinterPage();
                    List<JTweet> tweets = twindexProvider.get().searchTweets(lastQuery);
                    printerPage.setResults(tweets);
                    setResponsePage(printerPage);
                }
            }
        };
        add(resultsPanel.setOutputMarkupId(true));
//        add(wikiPanel = new WikipediaLazyLoadPanel("wikipanel"));

        String searchType = parameters.getString("search");
        String tmpUserName = null;
        boolean showSpacer = true;
        if (getMySession().hasLoggedIn()) {
            if (query.getQuery().isEmpty() && userName.isEmpty() && (searchType == null || searchType.isEmpty()))
                searchType = SearchBox.FRIENDS;
            tmpUserName = getMySession().getUser().getScreenName();
//            showSpacer = false;
        } else {
            ssPanel.setVisible(false);
            facetPanel.setVisible(false);
//            dateFilterPanel.setVisible(false);
            // so that the jetwick link on my twitter account works ;)            
            if (userName.isEmpty()) {
                tagCloudPanel.setVisible(false);

//                if (query.getQuery().isEmpty()) {
//                    resultsPanel.setVisible(false);
//                    navigationPanel.setVisible(false);
//                    showSpacer = false;
//                }
            }
        }
        dateFilterPanel.setVisible(false);

        searchBox = new SearchBox("searchbox", tmpUserName, searchType, showSpacer) {

            @Override
            protected Collection<String> getQueryChoices(String input) {
                return getTweetSearch().getQueryChoices(lastQuery, input);
            }

            @Override
            protected void onSelectionChange(AjaxRequestTarget target, String newValue) {
                if (lastQuery == null)
                    return;

                JetwickQuery tmpQ = lastQuery.getCopy().setQuery(newValue);
                tmpQ.removeFilterQueries(ElasticTweetSearch.DATE);
                doSearch(tmpQ, 0, false, true);
                updateAfterAjax(target, false);
            }

            @Override
            protected Collection<String> getUserChoices(String input) {
                return getTweetSearch().getUserChoices(lastQuery, input);
            }
        };
        add(searchBox.setOutputMarkupId(true));

        if (SearchBox.FRIENDS.equalsIgnoreCase(searchType)) {
            twitterFallback = false;
            query = createFriendQuery(query.getQuery());
            if (query == null)
                return;
        }

        doSearch(query, 0, twitterFallback);
    }

    public JetwickQuery createFriendQuery(String queryStr) {
        if (getMySession().hasLoggedIn()) {
            Collection<String> friends = getMySession().getFriends(uindexProvider.get());
            if (friends.isEmpty()) {
                info("You recently logged in. Please try again in 2 minutes to use friend search.");
            } else {
                return new TweetQuery(queryStr).createFriendsQuery(friends).
                        addLatestDateFilter(8).
                        setSort(ElasticTweetSearch.DATE, "desc");
            }
        } else
            info("Login to use friend search!");

        PageParameters pp = new PageParameters();
        pp.put("q", queryStr);                
        return createQuery(pp);
    }

    /**
     * used from facets (which adds filter queries) and
     * from footer which changes the page
     */
    public void doOldSearch(int page) {
        logger.info(addIP("[stats] change old search. page:" + page));
        if (lastQuery == null) {
            lastQuery = createQuery(new PageParameters());
            logger.warn(addIP("Last query is null!? created new default"));
        }
        doSearch(lastQuery, page, false);
    }

    public void doSearch(JetwickQuery query, int page, boolean twitterFallback) {
        doSearch(query, page, twitterFallback, false);
    }

    public void doSearch(JetwickQuery query, int page, boolean twitterFallback, boolean instantSearch) {
        if (getMySession().hasLoggedIn())
            query.attachUserFacets();

        String queryString;
        if (!instantSearch) {
            // change text field
            searchBox.init(query.setEscape(false).getQuery(), query.extractUserName());
        }

        queryString = query.setEscape(true).getQuery();
        // if query is lastQuery then user is saved in filter not in a pageParam
        String userName = searchBox.getUserName();

        // do not show front page
//        if (!getMySession().hasLoggedIn() && query.getQuery().isEmpty() && userName.isEmpty())
//            return;

//        wikiPanel.setParams(queryString, language);
        boolean startBGThread = true;

        // do not trigger background searchAndGetUsers if this query is the identical
        // to the last searchAndGetUsers or if it is an instant searchAndGetUsers
        if (instantSearch || lastQuery != null
                && queryString.equals(lastQuery.getQuery())
                && userName.equals(lastQuery.extractUserName()))
            startBGThread = false;

        // do not trigger twitter searchAndGetUsers if a searchAndGetUsers through a users' tweets is triggered
        if (!userName.isEmpty() && !queryString.isEmpty())
            twitterFallback = false;

        if (query.containsFilterKey("id"))
            twitterFallback = false;

        if (!instantSearch)
            lastQuery = query;

        Collection<JUser> users = new LinkedHashSet<JUser>();
        query.attachPagability(page, hitsPerPage);
        long start = System.currentTimeMillis();
        long totalHits = 0;
        SearchResponse rsp = null;
        try {
            rsp = getTweetSearch().query(users, query);
            totalHits = rsp.getHits().getTotalHits();
            logger.info(addIP("[stats] " + totalHits + " hits for: " + query.toString()));
        } catch (Exception ex) {
            logger.error("Error while searching " + query.toString(), ex);
        }

        resultsPanel.clear();
        Collection<JTweet> tweets = null;
        String msg = "";
        if (totalHits > 0) {
            float time = (System.currentTimeMillis() - start) / 100.0f;
            time = Math.round(time) / 10f;
            msg = "Found " + totalHits + " tweets in " + time + " s";
        } else {
            if (queryString.isEmpty()) {
                if (userName.isEmpty()) {
                    // something is wrong with our index because q='' and user='' should give us all docs!
                    logger.warn(addIP("[stats] 0 results for q='' using news!"));
                    queryString = "news";
                    startBGThread = false;
                } else
                    msg = "for user '" + userName + "'";
            }

            if (twitterFallback) {
                // try TWITTER SEARCH
                users.clear();
                try {
                    if (getTwitterSearch().getRateLimitFromCache() > TwitterSearch.LIMIT) {
                        if (!userName.isEmpty()) {
                            tweets = getTwitterSearch().getTweets(new JUser(userName), users, TWEETS_IF_NO_HIT);
                        } else
                            tweets = getTwitterSearch().searchAndGetUsers(queryString, users, TWEETS_IF_NO_HIT, 1);
                    }
                } catch (TwitterException ex) {
                    logger.warn("Warning while querying twitter:" + ex.getMessage());
                } catch (Exception ex) {
                    logger.error("Error while querying twitter:" + ex.getMessage());
                }
            }

            if (users.isEmpty()) {
                if (!msg.isEmpty())
                    msg = " " + msg;
                msg = "Sorry, nothing found" + msg + ".";
            } else {
                resultsPanel.setQueryMessageWarn("Sparse results.");
                msg = "Using twitter-search " + msg + ".";
                logger.warn("[stats] qNotFound:" + query.getQuery());
            }

            if (startBGThread)
                msg += " Please try again in two minutes to get jetwicked results.";
        }

        if (startBGThread) {
            try {                
                tweetThread = new Thread(queueTweets(tweets, queryString, userName));
                tweetThread.start();
            } catch (Exception ex) {
                logger.error("Couldn't queue tweets. query " + queryString + " user=" + userName + " Error:" + Helper.getMsg(ex));
            }
        } else
            tweetThread = null;

        facetPanel.update(rsp, query);
        tagCloudPanel.update(rsp, query);
        urlTrends.update(rsp, query);

        resultsPanel.setQueryMessage(msg);
        resultsPanel.setQuery(queryString);
        resultsPanel.setUser(userName);
        resultsPanel.setHitsPerPage(hitsPerPage);

        dateFilterPanel.update(rsp);

        if (!query.getSortFields().isEmpty()) {
            resultsPanel.setSort(query.getSortFields().get(0).getKey(), query.getSortFields().get(0).getValue());
        } else
            resultsPanel.setSort(null, null);

        resultsPanel.setTweetsPerUser(-1);
        for (JUser user : users) {
            resultsPanel.add(user);
        }

        navigationPanel.setPage(page);
        navigationPanel.setHits(totalHits);
        navigationPanel.setHitsPerPage(hitsPerPage);
        navigationPanel.updateVisibility();
        logger.info(addIP("Finished Constructing UI."));
    }

    public QueueThread queueTweets(Collection<JTweet> tweets,
            String qs, String userName) {
        return grabber.init(tweets, qs, userName).setTweetsCount(TWEETS_IF_HIT).
                setTwitterSearch(getTwitterSearch()).queueTweetPackage();
    }

    String addIP(String str) {
        String q = "";
        if (getWebRequestCycle() != null)
            q = getWebRequestCycle().getWebRequest().getParameter("q");

        return str + " IP=" + remoteHost
                + " session=" + getSession().getId()
                + " q=" + q;
    }
}
