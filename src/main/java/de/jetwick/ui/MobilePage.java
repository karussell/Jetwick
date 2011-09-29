/*
 * Copyright 2011 Peter Karich, jetwick_@_pannous_._info.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetwick.ui;

import com.google.inject.Inject;
import com.google.inject.Provider;
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.es.JetwickQuery;
import de.jetwick.es.TweetQuery;
import de.jetwick.tw.Extractor;
import de.jetwick.util.Helper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.elasticsearch.action.search.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class MobilePage extends WebPage {

    private static final long serialVersionUID = 1L;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Inject
    private Provider<ElasticTweetSearch> twindexProvider;
    private String queryString;
    private String remoteHost;

    public MobilePage(PageParameters pp) {
        add(new Label("title", "Jetwick Twitter Search . mobile"));
        Form form = new Form("searchform") {

            @Override
            public void onSubmit() {
                PageParameters params = new PageParameters();
                if (queryString != null && !queryString.isEmpty())
                    params.add("q", queryString);
                setResponsePage(MobilePage.class, params);
            }
        };
        form.setMarkupId("queryform");
        add(form);

        TextField textField = new TextField("textField", new PropertyModel(this, "queryString"));
        form.add(textField);

        remoteHost = getWebRequestCycle().getWebRequest().getHttpServletRequest().getRemoteHost();
        queryString = pp.getString("q");
        doSearch();
    }

    private void doSearch() {
        Collection<JUser> users = new LinkedHashSet<JUser>();        
        JetwickQuery query = new TweetQuery(queryString).
                setEscape(true).setSort(ElasticTweetSearch.RT_COUNT, "desc").
                addLatestDateFilter(24).addNoDupsFilter().addNoSpamFilter().
                addIsOriginalTweetFilter().setSize(10);
        
        long start = System.currentTimeMillis();
        long totalHits = 0;
        SearchResponse rsp = null;
        try {
            rsp = getTweetSearch().query(users, query);
            totalHits = rsp.getHits().getTotalHits();
            logger.info(addIP("[mstats] " + totalHits + " hits for: " + query.toString()));
        } catch (Exception ex) {
            logger.error("Error while searching " + query.toString(), ex);
        }

        String msg = "";
        if (totalHits > 0) {
            float time = (System.currentTimeMillis() - start) / 100.0f;
            time = Math.round(time) / 10f;
            msg = totalHits + " tweets, " + time + " s";
        } else
            msg = "Sorry, nothing found";

        add(new Label("msg", msg));

        ListView userView = new ListView("users", new ArrayList(users)) {

            @Override
            public void populateItem(final ListItem item) {
                final JUser user = (JUser) item.getModelObject();
                String twitterUrl = Helper.TURL + "/" + user.getScreenName();
                item.add(new ExternalLink("userUrl", twitterUrl, user.getScreenName()));


                item.add(new ListView("tweets", new ArrayList<JTweet>(user.getOwnTweets())) {

                    @Override
                    public void populateItem(final ListItem item) {
                        final JTweet tweet = (JTweet) item.getModelObject();
                        final Label label = new Label("tweet", new Model<String>() {

                            @Override
                            public String getObject() {
                                return new Extractor().setTweet(tweet).setText(tweet.getText()).run().toString();
//                                return tweet.getText();
                            }
                        });
                        label.setEscapeModelStrings(false);
                        item.add(label);
                        
                        item.add(new Label("tweetDate", Helper.toSimpleDateTime(tweet.getCreatedAt())));
                        item.add(new Label("retweets", "retweets: " + tweet.getRetweetCount()));
                    }
                });
            }
        };
//        for (JUser user : users) {
//            res += user.getScreenName() + "=" + user.getOwnTweets().iterator().next().getText();
//        }

        add(userView);
    }

    public ElasticTweetSearch getTweetSearch() {
        return twindexProvider.get();
    }

    String addIP(String str) {
        String q = "";
        if (getWebRequestCycle() != null)
            q = getWebRequestCycle().getWebRequest().getParameter("q");

        return str + " IP=" + remoteHost
                + " session=" + getWebRequestCycle().getSession().getId()
                + " q=" + q;
    }
}
