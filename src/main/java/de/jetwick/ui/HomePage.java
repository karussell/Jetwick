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
import de.jetwick.solr.JetwickQuery;
import de.jetwick.solr.SavedSearch;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrTweetSearch;
import de.jetwick.solr.SolrUser;
import de.jetwick.solr.SolrUserSearch;
import de.jetwick.solr.TweetQuery;
import de.jetwick.tw.TwitterSearch;
import de.jetwick.tw.queue.QueueThread;
import de.jetwick.ui.jschart.JSDateFilter;
import de.jetwick.wikipedia.WikipediaLazyLoadPanel;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.wicket.PageParameters;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.request.target.basic.RedirectRequestTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterException;
import twitter4j.http.AccessToken;

/**
 * TODO clean up this bloated class
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class HomePage extends WebPage {

    private static final long serialVersionUID = 1L;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private SolrQuery lastQuery;
    private int hitsPerPage = 15;
    private FeedbackPanel feedbackPanel;
    private ResultsPanel resultsPanel;
    private FacetPanel facetPanel;
    private SavedSearchPanel ssPanel;
    private TagCloudPanel tagCloud;
    private NavigationPanel navigationPanel;
    private SearchBox searchBox;
    private String language = "en";
    private String remoteHost = "";
    private WikipediaLazyLoadPanel wikiPanel;
    private UrlTrendPanel urlTrends;
    @Inject
    private Provider<SolrTweetSearch> twindexProvider;
    @Inject
    private Provider<SolrUserSearch> uindexProvider;
    @Inject
    private MyTweetGrabber grabber;
    private JSDateFilter dateFilter;
    private transient Thread tweetThread;
    private static int TWEETS_IF_HIT = 30;
    private static int TWEETS_IF_NO_HIT = 40;

    public TwitterSearch getTwitterSearch() {
        return getMySession().getTwitterSearch();
    }

    public MySession getMySession() {
        return (MySession) getSession();
    }
    // for testing

    HomePage() {
    }

    public HomePage(final PageParameters parameters) {
        initSession();
        String callback = parameters.getString("callback");
        if ("true".equals(callback)) {
            try {
                logger.info("Received callback");
                AccessToken token = CallbackHelper.getParseTwitterUrl(getTwitterSearch(), parameters);
                getMySession().setTwitterSearch(token, uindexProvider.get(), (WebResponse) getResponse());
            } catch (Exception ex) {
                logger.error("Error while receiving callback", ex);
                String msg = TwitterSearch.getMessage(ex);
                if (msg.length() > 0)
                    error(msg);
                else
                    error("Error when getting information from twitter! Please login again!");
                getMySession().logout(uindexProvider.get(), (WebResponse) getResponse());
            }
            // avoid showing the url parameters (e.g. refresh would let it failure!)
            setRedirect(true);
            setResponsePage(HomePage.class);
        } else
            init(createQuery(parameters), 0, true);
    }

    public HomePage(SolrQuery query, int page, boolean twitterFallback) {
        initSession();
        init(query, page, twitterFallback);
    }

    private void initSession() {
        try {
            getMySession().init((WebRequest) getRequest(), uindexProvider.get());
            String msg = getMySession().getSessionTimeoutMessage();
            if (!msg.isEmpty())
                info(msg);
        } catch (Exception ex) {
            logger.error("Error on twitter4j init.", ex);
            error("Couldn't login. Please file report to http://twitter.com/jetwick " + new Date());
        }
    }

    @Override
    protected void configureResponse() {
        super.configureResponse();
        // 1. searchAndGetUsers for wikileak
        // 2. apply de filter
        // 3. Show latest tweets (of user sebringl)
        // back button + de filter => WicketRuntimeException: component filterPanel:filterNames:1:filterValues:2:filterValueLink not found on page de.jetwick.ui.HomePage
        // http://www.richardnichols.net/2010/03/apache-wicket-force-page-reload-to-fix-ajax-back/
        // http://blogs.atlassian.com/developer/2007/12/cachecontrol_nostore_considere.html

        // TODO M2.1
        WebResponse response = getWebRequestCycle().getWebResponse();
        response.setHeader("Cache-Control", "no-cache, max-age=0,must-revalidate, no-store");
    }

    public SolrTweetSearch getTweetSearch() {
        return twindexProvider.get();
    }

    public Thread getQueueThread() {
        return tweetThread;
    }

    public SolrQuery createQuery(PageParameters parameters) {
        // TODO M2.1 parameters.get("h").toString can cause NPE!!
        String hitsStr = parameters.getString("h");
        if (hitsStr != null) {
            try {
                hitsPerPage = Integer.parseInt(hitsStr);
                hitsPerPage = Math.min(100, hitsPerPage);
            } catch (Exception ex) {
                logger.warn("Couldn't parse hits per page:" + hitsStr + " " + ex.getMessage());
            }
        }

        String idStr = parameters.getString("id");
        SolrQuery q = null;

        if (idStr != null) {
            try {
                int index = idStr.lastIndexOf("/");
                if (index > 0 && index + 1 < idStr.length())
                    idStr = idStr.substring(index + 1);

                q = JetwickQuery.createIdQuery(Long.parseLong(idStr));
            } catch (Exception ex) {
            }
        }

        if (q == null) {
            String originStr = parameters.getString("findOrigin");
            if (originStr != null) {
                logger.info("[stats] findOrigin from lastQuery:" + lastQuery);
                q = getTweetSearch().createFindOriginQuery(lastQuery, originStr, 3);
            }
        }

        if (q == null) {
            String queryStr = parameters.getString("q");
            if (queryStr == null)
                queryStr = "";
            String userName = parameters.getString("u");
            q = new TweetQuery(queryStr).addUserFilter(userName);

            // avoid slow queries for *:* query and filter against latest tweets
            if (queryStr.isEmpty() && q.getFilterQueries() == null) {
                logger.info("[stats] q=''");
                q.addFilterQuery(SolrTweetSearch.DATE_TAG + SolrTweetSearch.FILTER_ENTRY_LATEST_DT);
            }
        }

        String sort = parameters.getString("sort");
        if ("retweets".equals(sort))
            JetwickQuery.setSort(q, SolrTweetSearch.RT_COUNT + " desc");
        else if ("latest".equals(sort))
            JetwickQuery.setSort(q, SolrTweetSearch.DATE + " desc");
        else if ("oldest".equals(sort))
            JetwickQuery.setSort(q, SolrTweetSearch.DATE + " asc");

        q.addFilterQuery(SolrTweetSearch.FILTER_NO_SPAM);
        //q.addFilterQuery(SolrTweetSearch.FILTER_NO_DUPS);
        q.addFilterQuery(SolrTweetSearch.FILTER_IS_NOT_RT);

        return getTweetSearch().attachHighlighting(q);
    }

    public void updateAfterAjax(AjaxRequestTarget target, boolean updateSearchBox) {
        if (target != null) {
            target.addComponent(facetPanel);
            target.addComponent(resultsPanel);
            //already in resultsPanel target.addComponent(lazyLoadAdPanel);

            target.addComponent(navigationPanel);
            if (updateSearchBox)
                target.addComponent(searchBox);
            target.addComponent(tagCloud);
            target.addComponent(dateFilter);
            target.addComponent(urlTrends);
            target.addComponent(feedbackPanel);
            target.addComponent(ssPanel);

            // no ajax for wikipedia to avoid requests
            //target.addComponent(wikiPanel);

            // this does not work (scroll to top)
//            target.focusComponent(searchBox);
        }
    }

    public void init(SolrQuery query, final int page, boolean twitterFallback) {
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
                SolrQuery q;
                if (lastQuery != null)
                    q = lastQuery;
                else
                    q = new TweetQuery();

                if (name == null) {
                    JetwickQuery.applyFacetChange(q, SolrTweetSearch.FIRST_URL_TITLE, false);
                } else
                    q.addFilterQuery(SolrTweetSearch.FIRST_URL_TITLE + ":\"" + name + "\"");

                doSearch(q, 0, true);
                updateAfterAjax(target, false);
            }

            @Override
            protected void onDirectUrlClick(AjaxRequestTarget target, String name) {
                if (lastQuery == null || name == null || name.isEmpty())
                    return;

                SolrQuery q = new TweetQuery();
                q.addFilterQuery(SolrTweetSearch.FIRST_URL_TITLE + ":\"" + name + "\"");
                try {
                    List<SolrTweet> tweets = getTweetSearch().collectTweets(getTweetSearch().search(q.setRows(1)));
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
                SolrUser user = getMySession().getUser();
                SavedSearch ss = user.getSavedSearch(ssId);
                if (ss != null) {
                    doSearch(ss.getQuery(user.getSavedSearches()), 0, true);
                    uindexProvider.get().save(user, true);
                }
                updateSSCounts(target);
                updateAfterAjax(target, true);
            }

            @Override
            public void onRemove(AjaxRequestTarget target, long ssId) {
                SolrUser user = getMySession().getUser();
                user.removeSavedSearch(ssId);
                uindexProvider.get().save(user, true);
                updateSSCounts(target);
            }

            @Override
            public void onSave(AjaxRequestTarget target, long ssId) {
                SavedSearch ss = new SavedSearch(ssId, lastQuery);
                SolrUser user = getMySession().getUser();
                user.addSavedSearch(ss);
                uindexProvider.get().save(user, true);
                updateSSCounts(target);
            }

            @Override
            public void updateSSCounts(AjaxRequestTarget target) {
                try {
                    SolrUser user = getMySession().getUser();
                    if (user != null) {
                        SolrQuery query = new SolrQuery().setFacet(true).setRows(0);
                        TweetQuery.updateSavedSearchFacets(query, user.getSavedSearches());
                        logger.info("Start updateSSCounts getTweetSearch");
                        update(getTweetSearch().search(query));
                        if (target != null)
                            target.addComponent(ssPanel);
                        logger.info("Updated saved search counts for " + user.getScreenName());
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

        if (!getMySession().hasLoggedIn())
            ssPanel.setVisible(false);

        add(ssPanel.setOutputMarkupId(true));

        add(new UserPanel("userPanel", this) {

            @Override
            public void onLogout() {
                getMySession().logout(uindexProvider.get(), (WebResponse) getResponse());
                setResponsePage(HomePage.class);
            }

            @Override
            public void updateAfterAjax(AjaxRequestTarget target) {
                HomePage.this.updateAfterAjax(target, false);
            }

            @Override
            public void onShowTweets(AjaxRequestTarget target, String userName) {
                doSearch((TweetQuery) new TweetQuery().addUserFilter(userName), 0, false);
                HomePage.this.updateAfterAjax(target, true);
            }

            @Override
            protected Collection<String> getUserChoices(String input) {
                return getTweetSearch().getUserChoices(lastQuery, input);
            }
        });

        tagCloud = new TagCloudPanel("tagcloud") {

            @Override
            protected void onTagClick(String name) {
                if (lastQuery != null) {
                    lastQuery.setQuery((lastQuery.getQuery() + " " + name).trim());
                    doSearch(lastQuery, 0, true);
                } else {
                    // never happens?
                    PageParameters pp = new PageParameters();
                    pp.add("q", name);
                    setResponsePage(HomePage.class, pp);
                }
            }

            @Override
            protected void onFindOriginClick(String tag) {
                PageParameters pp = new PageParameters();
                pp.add("findOrigin", tag);

                doSearch(createQuery(pp), 0, true);
                // this preserves parameters but cannot be context sensitive!
//                setResponsePage(HomePage.class, pp);
            }
        };
        add(tagCloud.setOutputMarkupId(true));

        searchBox = new SearchBox("searchbox") {

            @Override
            protected Collection<String> getQueryChoices(String input) {
                return getTweetSearch().getQueryChoices(lastQuery, input);
            }

            @Override
            protected void onSelectionChange(AjaxRequestTarget target, String newValue) {
                SolrQuery tmpQ = lastQuery.getCopy().setQuery(newValue);
                JetwickQuery.applyFacetChange(tmpQ, SolrTweetSearch.DATE, true);
                doSearch(tmpQ, 0, false, true);
                updateAfterAjax(target, false);
            }

            @Override
            protected Collection<String> getUserChoices(String input) {
                return getTweetSearch().getUserChoices(lastQuery, input);
            }
        };
        add(searchBox.setOutputMarkupId(true));

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
            public void onFacetChange(AjaxRequestTarget target, String filterQuery, boolean selected) {
                if (lastQuery != null) {
                    JetwickQuery.applyFacetChange(lastQuery, filterQuery, selected);
                } else {
                    logger.error("last query cannot be null but was! ... when clicking on facets!?");
                    return;
                }

                doOldSearch(0);
                updateAfterAjax(target, false);
            }
        };
        add(facetPanel.setOutputMarkupId(true));

        dateFilter = new JSDateFilter("dateFilter") {

            @Override
            protected void onFacetChange(AjaxRequestTarget target, String filter, Boolean selected) {
                if (lastQuery != null) {
                    if (selected == null) {
                        JetwickQuery.removeFilterQueries(lastQuery, filter);
                    } else if (selected) {
//                        getTweetSearch().expandFilterQuery(lastQuery, filter, true);
                        JetwickQuery.replaceFilterQuery(lastQuery, filter, true);
                    } else
                        JetwickQuery.reduceFilterQuery(lastQuery, filter);
                } else {
                    logger.error("last query cannot be null but was! ... when clicking on facets!?");
                    return;
                }

                doOldSearch(0);
                updateAfterAjax(target, false);
            }

            @Override
            protected boolean isAlreadyFiltered(String filter) {
                if (lastQuery != null)
                    return JetwickQuery.containsFilter(lastQuery, filter);

                return false;
            }

            @Override
            public String getFilterName(String key) {
                return facetPanel.getFilterName(key);
            }
        };
        add(dateFilter.setOutputMarkupId(true));

        // TODO M2.1
        language = getWebRequestCycle().getWebRequest().getHttpServletRequest().getLocale().getLanguage();
        remoteHost = getWebRequestCycle().getWebRequest().getHttpServletRequest().getRemoteHost();
        resultsPanel = new ResultsPanel("results", language) {

            @Override
            public void onSortClicked(AjaxRequestTarget target, String sortStr) {
                if (lastQuery != null) {
                    JetwickQuery.setSort(lastQuery, sortStr);
                    doSearch(lastQuery, 0, false);
                    updateAfterAjax(target, false);
//                    setResponsePage(new HomePage(lastQuery, 0, false));
                }
            }

            @Override
            public void onUserClick(String userName, String queryStr) {
//                SolrQuery query = getTweetSearch().createTweetQuery(queryStr);
//                if (userName != null)
//                    query.addFilterQuery(SolrTweetSearch.FILTER_KEY_USER + "\"" + userName.trim() + "\"");

                System.out.println("NOW!:" + userName + " " + queryStr);
                PageParameters p = new PageParameters();
                if (queryStr != null && !queryStr.isEmpty())
                    p.add("q", queryStr);
                if (userName != null)
                    p.add("u", userName.trim());

                doSearch(createQuery(p), 0, true);
            }

            @Override
            public Collection<SolrTweet> onTweetClick(long id, boolean retweet) {
                logger.info("[stats] search replies of:" + id + " retweet:" + retweet);
                return getTweetSearch().searchReplies(id, retweet);
            }

            @Override
            public void onFindSimilar(SolrTweet tweet) {
                SolrQuery query = new TweetQuery().createSimilarQuery(tweet);
                logger.info("[stats] similar search:" + query);
                setResponsePage(new HomePage(query, 0, true));
            }

            @Override
            public Collection<SolrTweet> onInReplyOfClick(long id) {
                SolrTweet tw = getTweetSearch().findByTwitterId(id);
                logger.info("[stats] search tweet:" + id + " " + tw);
                if (tw != null)
                    return Arrays.asList(tw);
                else
                    return new ArrayList();
            }

            @Override
            public String getTweetsAsString() {
                if (lastQuery != null)
                    return twindexProvider.get().getTweetsAsString(lastQuery);

                return "";
            }

            @Override
            public void onHtmlExport() {
                if (lastQuery != null) {
                    PrinterPage printerPage = new PrinterPage();
                    List<SolrTweet> tweets = twindexProvider.get().searchTweets(lastQuery);
                    printerPage.setResults(tweets);
                    setResponsePage(printerPage);
                }
            }
        };
        add(resultsPanel.setOutputMarkupId(true));

        add(wikiPanel = new WikipediaLazyLoadPanel("wikipanel"));

        doSearch(query, page, twitterFallback);
    }

    /**
     * used from facets (which adds filter queries) and
     * from footer which changes the page
     */
    public void doOldSearch(int page) {
        logger.info("[stats] change old search. page:" + page + " IP=" + remoteHost);
        doSearch(lastQuery, page, false);
    }

    public void doSearch(SolrQuery query, int page, boolean twitterFallback) {
        doSearch(query, page, twitterFallback, false);
    }

    public void doSearch(SolrQuery query, int page, boolean twitterFallback, boolean instantSearch) {
        String queryString = searchBox.getQuery();
        if (!instantSearch) {
            // change text field
            searchBox.init(query);
            queryString = searchBox.getQuery();
        } else {
            queryString = query.getQuery();
            if (queryString == null)
                queryString = "";
        }

        String userName = searchBox.getUserName();
        resultsPanel.setAdQuery(queryString);
        wikiPanel.setParams(queryString, language);
        boolean startBGThread = true;

        // do not trigger background searchAndGetUsers if this query is the identical
        // to the last searchAndGetUsers or if it is an instant searchAndGetUsers
        if (instantSearch || lastQuery != null
                && queryString.equals(JetwickQuery.extractNonNullQueryString(lastQuery))
                && userName.equals(JetwickQuery.extractUserName(lastQuery)))
            startBGThread = false;

        // do not trigger twitter searchAndGetUsers if a searchAndGetUsers through a users' tweets is triggered
        if (!userName.isEmpty() && !queryString.isEmpty())
            twitterFallback = false;

        if (JetwickQuery.containsFilterKey(query, "id"))
            twitterFallback = false;

        if (!instantSearch)
            lastQuery = query;

        Collection<SolrUser> users = new LinkedHashSet<SolrUser>();
        getTweetSearch().attachPagability(query, page, hitsPerPage);

        if (getMySession().hasLoggedIn())
            TweetQuery.updateSavedSearchFacets(query, getMySession().getUser().getSavedSearches());

        long start = System.currentTimeMillis();
        long totalHits = 0;
        QueryResponse rsp = null;
        try {
            rsp = getTweetSearch().search(users, query);
            totalHits = rsp.getResults().getNumFound();
            logger.info("[stats] " + totalHits + " hits for: " + query.toString() + " IP=" + remoteHost);
        } catch (Exception ex) {
            logger.error("Error while searching " + query.toString() + ": " + ex.getMessage());
        }

        resultsPanel.clear();
        Collection<SolrTweet> tweets = null;
        String msg = "";
        if (totalHits > 0) {
            float time = (System.currentTimeMillis() - start) / 100.0f;
            time = Math.round(time) / 10f;
            msg = "Found " + totalHits + " tweets in " + time + " s";
        } else {
            if (queryString.isEmpty()) {
                if (userName.isEmpty()) {
                    // something is wrong with our index because q='' and u='' should give us all docs!
                    logger.error("[stats] 0 results for q='' using news!");
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
                            tweets = getTwitterSearch().getTweets(new SolrUser(userName), users, TWEETS_IF_NO_HIT);
                        } else
                            tweets = getTwitterSearch().searchAndGetUsers(queryString, users, TWEETS_IF_NO_HIT, 1);
                    }
                } catch (TwitterException ex) {
                    logger.warn("Warning while querying twitter:" + ex.getMessage());
                } catch (Exception ex) {
                    logger.error("Error while querying twitter:" + ex.getMessage());
                }
            }

            if (users.size() == 0) {
                if (!msg.isEmpty())
                    msg = " " + msg;
                msg = "Sorry, nothing found" + msg + ".";
            } else {
                resultsPanel.setQueryMessageWarn("Sparse results.");
                msg = "Using twitter-search " + msg + ".";
                logger.warn("[stats] qNotFound:" + query.getQuery());
            }

            if (startBGThread)
                msg += " Come back in ~10min for filterable results.";
        }

        if (startBGThread) {
            try {
                tweetThread = new Thread(queueTweets(tweets, queryString, userName));
                tweetThread.start();
            } catch (Exception ex) {
                logger.error("Couldn't queue tweets. query" + queryString + " user=" + userName);
            }
        } else
            tweetThread = null;

        facetPanel.update(rsp, query);
        tagCloud.update(rsp, query);
        urlTrends.update(rsp, query);

        resultsPanel.setQueryMessage(msg);
        resultsPanel.setQuery(queryString);
        resultsPanel.setUser(userName);
        resultsPanel.setHitsPerPage(hitsPerPage);

        dateFilter.update(rsp);

        resultsPanel.setSort(query.getSortField());
        resultsPanel.setTweetsPerUser(-1);
        for (SolrUser user : users) {
            resultsPanel.add(user);
        }

        navigationPanel.setPage(page);
        navigationPanel.setHits(totalHits);
        navigationPanel.setHitsPerPage(hitsPerPage);
        navigationPanel.updateVisibility();
        logger.info("Finished Constructing UI");
    }

    public QueueThread queueTweets(Collection<SolrTweet> tweets,
            String qs, String userName) {
        return grabber.init(tweets, qs, userName).setTweetsCount(TWEETS_IF_HIT).
                setTwitterSearch(getTwitterSearch()).queueTweetPackage();
    }
}
