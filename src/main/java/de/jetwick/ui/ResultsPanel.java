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

import com.google.api.translate.Language;
import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.ui.util.LabeledLink;
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
import de.jetwick.util.Helper;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.wicket.PageParameters;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxFallbackLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebResource;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.link.ResourceLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;
import org.odlabs.wiquery.core.commons.IWiQueryPlugin;
import org.odlabs.wiquery.core.commons.WiQueryResourceManager;
import org.odlabs.wiquery.core.javascript.JsStatement;
import org.odlabs.wiquery.ui.dialog.util.DialogUtilsBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class ResultsPanel extends Panel implements IWiQueryPlugin {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private ListView userView;
    private List<JUser> users = new ArrayList<JUser>();
    private String queryMessage;
    private String queryMessageWarn;
    private String query;
    private String user;
    private int tweetsPerUser;
    private String _sortKey;
    private String _sortVal;
    private LabeledLink findOriginLink;
    private LabeledLink translateAllLink;
    private Map<Long, String> translateMap = new LinkedHashMap<Long, String>();
    private boolean translateAll = false;
    private int hitsPerPage;
    private Map<Long, JTweet> allTweets = new LinkedHashMap<Long, JTweet>();

    // for test only
    public ResultsPanel(String id) {
        this(id, "en");
    }

    public ResultsPanel(String id, final String toLanguage) {
        super(id);

        add(new Label("qm", new PropertyModel(this, "queryMessage")));
        add(new Label("qmWarn", new PropertyModel(this, "queryMessageWarn")) {

            @Override
            public boolean isVisible() {
                return queryMessageWarn != null && queryMessageWarn.length() > 0;
            }
        });

        add(createHitLink(15));
        add(createHitLink(30));
        add(createHitLink(60));

        Model qModel = new Model() {

            @Override
            public Serializable getObject() {
                if (query == null)
                    return "";
                String str = query;
                if (str.length() > 20)
                    str = str.substring(0, 20) + "..";
                return "Find origin of '" + str + "'";
            }
        };
        findOriginLink = new LabeledLink("findOriginLink", null, qModel, false) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                PageParameters pp = new PageParameters();
                pp.add("findOrigin", query);
                setResponsePage(TweetSearchPage.class, pp);
            }
        };

        add(findOriginLink);
        translateAllLink = new LabeledLink("translateAllLink", null, new Model<String>() {

            @Override
            public String getObject() {
                if (translateAll)
                    return "Show original language";
                else
                    // get english name of iso language chars
                    return "Translate tweets into " + new Locale(toLanguage).getDisplayLanguage(new Locale("en"));
            }
        }) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                if (target == null)
                    return;

                translateAll = !translateAll;
                if (!translateAll)
                    translateMap.clear();
                target.addComponent(ResultsPanel.this);
            }
        };

        add(translateAllLink);
        add(createSortLink("sortRelevance", ElasticTweetSearch.RELEVANCE, "desc"));
        add(createSortLink("sortRetweets", ElasticTweetSearch.RT_COUNT, "desc"));
        add(createSortLink("sortLatest", ElasticTweetSearch.DATE, "desc"));
        add(createSortLink("sortOldest", ElasticTweetSearch.DATE, "asc"));        
            
        add(new DialogUtilsBehavior());

        userView = new ListView("users", users) {

            @Override
            public void populateItem(final ListItem item) {
                final JUser user = (JUser) item.getModelObject();
                String name = user.getScreenName();
                if (user.getRealName() != null)
                    name = user.getRealName() + "  (" + name + ")";

                LabeledLink userNameLink = new LabeledLink("userNameLink", name, false) {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        onUserClick(user.getScreenName(), null);
                    }
                };
                item.add(userNameLink);                                		
                Link showLatestTweets = new Link("profileUrl") {

                    @Override
                    public void onClick() {
                        onUserClick(user.getScreenName(), null);
                    }
                };
                item.add(showLatestTweets.add(new ContextImage("profileImg", user.getProfileImageUrl())));

                final List<JTweet> tweets = new ArrayList<JTweet>();
                int counter = 0;
                for (JTweet tw : user.getOwnTweets()) {
                    if (tweetsPerUser > 0 && counter >= tweetsPerUser)
                        break;

                    tweets.add(tw);
                    allTweets.put(tw.getTwitterId(), tw);
                    counter++;
                }
                ListView tweetView = new ListView("tweets", tweets) {

                    @Override
                    public void populateItem(final ListItem item) {
                        item.add(createOneTweet("oneTweet", toLanguage).init(item.getModel(), false));
                    }
                };
                item.add(tweetView);
            }
        };

        add(userView);
        WebResource export = new WebResource() {

            @Override
            public IResourceStream getResourceStream() {
                return new StringResourceStream(getTweetsAsString(), "text/plain");
            }

            @Override
            protected void setHeaders(WebResponse response) {
                super.setHeaders(response);
                response.setAttachmentHeader("tweets.txt");
            }
        };

        export.setCacheable(false);
        add(new ResourceLink("exportTsvLink", export));
        add(new Link("exportHtmlLink") {

            @Override
            public void onClick() {
                onHtmlExport();
            }
        });
    }

    public OneTweet createOneTweet(String id, final String lang) {
        return new OneTweet("oneTweet") {

            @Override
            public String getTextFromTranslateAllAction(long id) {
                if (translateAll && translateMap.isEmpty())
                    fillTranslateMap(allTweets.values(), lang);

                return translateMap.get(id);
            }

            @Override
            public Collection<JTweet> onReplyClick(long id, boolean retweet) {
                return onTweetClick(id, retweet);
            }

            @Override
            public void onUserClick(String screenName) {
                ResultsPanel.this.onUserClick(screenName, null);
            }

            @Override
            public void onFindSimilarClick(JTweet tweet, AjaxRequestTarget target) {
                ResultsPanel.this.onFindSimilar(tweet, target);
            }

            @Override
            public Collection<JTweet> onInReplyOfClick(long id) {
                return ResultsPanel.this.onInReplyOfClick(id);
            }

            @Override
            public void onRetweet(JTweet tweet, AjaxRequestTarget target) {
                ResultsPanel.this.onRetweet(tweet, target);
            }
            
        }.setLanguage(lang);
    }

    public void fillTranslateMap(Collection<JTweet> tweets, String toLang) {
        Map<Integer, Long> index2Id = new LinkedHashMap<Integer, Long>();
        String[] texts = new String[tweets.size()];
        Language[] froms = new Language[tweets.size()];
        Language[] tos = new Language[tweets.size()];

        try {
            Language toLanguage = Language.fromString(toLang);
            Iterator<JTweet> iter = tweets.iterator();
            for (int i = 0; i < texts.length; i++) {
                JTweet tweet = iter.next();
                index2Id.put(i, tweet.getTwitterId());
                texts[i] = tweet.getText();
                froms[i] = Language.AUTO_DETECT;
                tos[i] = toLanguage;
            }

            String newTxts[] = Helper.translateAll(texts, froms, tos);
            for (int i = 0; i < newTxts.length; i++) {
                Long twitterId = index2Id.get(i);
                if (twitterId != null)
                    translateMap.put(twitterId, newTxts[i]);
            }
//            System.out.println(translateMap);
        } catch (Exception ex) {
            logger.error("Couldn't translate all tweets", ex);
        }
    }

    Map<Long, String> getTranslateMap() {
        return translateMap;
    }

    public void onUserClick(String userName, String query) {
    }

    public void onFindSimilar(JTweet tweet, AjaxRequestTarget target) {
    }

    public void onSortClicked(AjaxRequestTarget target, String sortKey, String sortVal) {
    }

    public String getTweetsAsString() {
        return "";
    }

    public void onHtmlExport() {
    }

    public Collection<JTweet> onTweetClick(long id, boolean retweet) {
        return Collections.EMPTY_LIST;
    }

    public Collection<JTweet> onInReplyOfClick(long id) {
        return Collections.EMPTY_LIST;
    }

    public void clear() {
        allTweets.clear();
        translateAll = false;
        translateMap.clear();
        users.clear();
        queryMessage = "";
        queryMessageWarn = "";
    }

    public void setQueryMessage(String queryMessage) {
        this.queryMessage = queryMessage;
    }

    public void setQueryMessageWarn(String queryMessageWarn) {
        this.queryMessageWarn = queryMessageWarn;
    }

    public void setTweetsPerUser(int twPerUser) {
        tweetsPerUser = twPerUser;
    }

    public void add(JUser u) {
        users.add(u);       
    }

    public void setQuery(String visibleString) {
        query = visibleString;
        if (query == null || query.isEmpty())
            findOriginLink.setVisible(false);
    }

    public void setUser(String u) {
        user = u;
    }

    public void setHitsPerPage(int hits) {
        hitsPerPage = hits;
    }

    public void setSort(String sortKey, String sortVal) {
        if (sortKey == null || sortKey.isEmpty()) {
            _sortKey = ElasticTweetSearch.RELEVANCE;
            _sortVal = "desc";
        } else {
            _sortKey = sortKey;
            _sortVal = sortVal;
        }
    }

    public Link createHitLink(final int hits) {
        Link link = new AjaxFallbackLink("hits" + hits) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                PageParameters pp = new PageParameters();
                pp.add("hits", "" + hits);
                pp.add("user", user);
                pp.add("q", query);
                setResponsePage(TweetSearchPage.class, pp);
            }
        };

        link.add(new AttributeAppender("class", new Model() {

            @Override
            public Serializable getObject() {
                return hits == hitsPerPage ? "selected" : "";
            }
        }, " "));

        return link;
    }

    public AjaxFallbackLink createSortLink(String id, final String sortKey, final String sortVal) {
        AjaxFallbackLink link = new AjaxFallbackLink(id) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                if (target != null)
                    onSortClicked(target, sortKey, sortVal);
            }
        };
        link.add(new AttributeAppender("class", new Model() {

            @Override
            public Serializable getObject() {
                return sortKey.equals(_sortKey) && sortVal.equals(_sortVal) ? "selected" : "";
            }
        }, " "));

        return link;
    }

    @Override
    public void contribute(WiQueryResourceManager wiQueryResourceManager) {        
    }

    @Override
    public JsStatement statement() {
        return new JsStatement();
    }

    protected void onRetweet(JTweet tweet, AjaxRequestTarget target) {        
    }
}
