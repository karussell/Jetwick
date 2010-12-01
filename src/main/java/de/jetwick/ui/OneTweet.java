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

import de.jetwick.ui.util.LabeledLink;
import com.google.api.translate.Language;
import com.google.api.translate.Translate;
import de.jetwick.data.YUser;
import de.jetwick.solr.SolrTweet;
import de.jetwick.tw.Extractor;
import de.jetwick.util.Helper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.IndicatingAjaxFallbackLink;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class OneTweet extends Panel {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private String language;
    private List<SolrTweet> tweets = new ArrayList<SolrTweet>();
    private boolean rtClicked = false;
    private boolean rpClicked = false;
    private boolean trClicked = false;
    private boolean inReplyOfClicked = false;

    public OneTweet(String id, IModel<SolrTweet> model) {
        this(id, model, false);
    }

    public OneTweet(String id, IModel<SolrTweet> model, boolean showUser) {
        super(id);
        setOutputMarkupId(true);
        final SolrTweet tweet = model.getObject();
        final YUser user = tweet.getFromUser();

        if (showUser) {
            LabeledLink userNameLink = new LabeledLink("userNameLink", user.getScreenName() + ":", false) {

                @Override
                public void onClick(AjaxRequestTarget target) {
                    onUserClick(user.getScreenName());
                }
            };
            add(userNameLink);
        } else
            add(new Label("userNameLink", ""));

        WebMarkupContainer spamIndicator = new WebMarkupContainer("spamIndicator");
        spamIndicator.setVisible(tweet.isSpam());
        add(spamIndicator);

        final String origText = new Extractor().setTweet(tweet).run().toString();
        final Label label = new Label("tweetText", origText);
        label.setEscapeModelStrings(false);
        label.setOutputMarkupId(true);
        add(label);

        ExternalLink dateLink = new ExternalLink("tweetDate",
                Helper.toTwitterHref(user.getScreenName(), tweet.getTwitterId()));

        String str = "status";
        if (tweet.getCreatedAt() != null)
            str = Helper.toSimpleDateTime(tweet.getCreatedAt());
        Label dateLabel = new Label("tweetDateLabel", str);
        dateLink.add(dateLabel);
        add(dateLink);

        add(new ExternalLink("tweetReply",
                Helper.toReplyHref(user.getScreenName(), tweet.getTwitterId())));

        add(new ExternalLink("tweetRetweet",
                Helper.toReplyStatusHref("RT @" + user.getScreenName() + ": " + tweet.getText(),
                user.getScreenName(), tweet.getTwitterId(), true)));
        IndicatingAjaxFallbackLink inReplyOfButton = new IndicatingAjaxFallbackLink("inreplyof") {

            @Override
            public void onClick(AjaxRequestTarget target) {
                if (target != null) {
                    tweets.clear();
                    target.addComponent(OneTweet.this);
                    if (!inReplyOfClicked)
                        tweets.addAll(onInReplyOfClick(tweet.getInReplyTwitterId()));

                    inReplyOfClicked = !inReplyOfClicked;
                }
            }
        };

        add(inReplyOfButton);
        if (SolrTweet.isDefaultInReplyId(tweet.getInReplyTwitterId()))
            inReplyOfButton.setVisible(false);

        IndicatingAjaxFallbackLink rtLink = new IndicatingAjaxFallbackLink("retweeters") {

            @Override
            public void onClick(AjaxRequestTarget target) {
                if (target != null) {
                    tweets.clear();
                    target.addComponent(OneTweet.this);
                    if (!rtClicked)
                        tweets.addAll(onReplyClick(tweet.getTwitterId(), true));

                    rtClicked = !rtClicked;
                }
            }
        };

        add(rtLink.add(new Label("retweetersLabel", "retweets " + tweet.getRetweetCount())));
        if (tweet.getRetweetCount() == 0)
            rtLink.setVisible(false);

        IndicatingAjaxFallbackLink replyLink = new IndicatingAjaxFallbackLink("replies") {

            @Override
            public void onClick(AjaxRequestTarget target) {
                if (target != null) {
                    tweets.clear();
                    target.addComponent(OneTweet.this);
                    if (!rpClicked)
                        tweets.addAll(onReplyClick(tweet.getTwitterId(), false));

                    rpClicked = !rpClicked;
                }
            }
        };

        add(replyLink.add(new Label("repliesLabel", "replies " + (tweet.getReplyCount() - tweet.getRetweetCount()))));
        if (tweet.getReplyCount() == tweet.getRetweetCount())
            replyLink.setVisible(false);

        add(new Link("similarLink") {

            @Override
            public void onClick() {
                onFindSimilarClick(tweet);
            }
        });

        add(new IndicatingAjaxFallbackLink("translateLink") {

            @Override
            public void onClick(AjaxRequestTarget target) {
                try {
                    if (target != null) {
                        Translate.setHttpReferrer("http://www.jetwick.com/");
                        // stupid workaround for http://groups.google.com/group/google-translate-general/browse_thread/thread/8cdc2b71f5213cf7
                        boolean replace = true;
                        String origText = tweet.getText();
                        if (origText.contains("# ") || origText.contains("@ "))
                            replace = false;
                        else {
                            // use letters so that google thinks the word after the '#' is related to those letters and won't translate or mix it up.
                            origText = origText.replaceAll("#", "XbllsHYBoPll").replaceAll("@", "XallsHYBoPll");
                        }

                        String trText;
                        if (!trClicked) {
                            trText = Translate.tr(origText, Language.AUTO_DETECT, language);
                            if (replace)
                                trText = trText.replaceAll("XbllsHYBoPll", "#").replaceAll("XallsHYBoPll", "@");
                        } else
                            trText = tweet.getText();

                        label.setDefaultModelObject(new Extractor().setText(trText).run().toString());
                        label.setEscapeModelStrings(false);
                        target.addComponent(label);

                        trClicked = !trClicked;
                    }
                } catch (Exception ex) {
                    logger.error(ex.getLocalizedMessage() + "! tweet:" + tweet);
                }
            }
        });

        ListView subTweets = new ListView("subtweets", new PropertyModel(this, "tweets")) {

            @Override
            public void populateItem(final ListItem item) {
                item.add(new OneTweet("suboneTweet", item.getModel(), true) {

                    @Override
                    public Collection<SolrTweet> onReplyClick(long id, boolean retweet) {
                        return OneTweet.this.onReplyClick(id, retweet);
                    }

                    @Override
                    public void onUserClick(String screenName) {
                        OneTweet.this.onUserClick(screenName);
                    }

                    @Override
                    public void onFindSimilarClick(SolrTweet tweet) {
                        OneTweet.this.onFindSimilarClick(tweet);
                    }

                    @Override
                    public Collection<SolrTweet> onInReplyOfClick(long id) {
                        return OneTweet.this.onInReplyOfClick(id);
                    }
                }.setLanguage(language));
            }
        };
        add(subTweets);
    }

    public OneTweet setLanguage(String lang) {
        language = lang;
        return this;
    }

    public Collection<SolrTweet> onReplyClick(long id, boolean retweet) {
        return Collections.EMPTY_LIST;
    }

    public Collection<SolrTweet> onInReplyOfClick(long id) {
        return Collections.EMPTY_LIST;
    }

    public void onUserClick(String screenName) {
    }

    public void onFindSimilarClick(SolrTweet tweet) {
    }
}
