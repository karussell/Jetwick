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

import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Random;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class OneLineAdPanel extends Panel {

    private Random rand = new Random();
    private ListView tweetView;
    private ArrayList<SolrTweet> tweets = new ArrayList<SolrTweet>();

    public OneLineAdPanel(String id) {
        super(id);
        // TODO
        final String LANGUAGE = "de";
        tweetView = new ListView<SolrTweet>("tweets", tweets) {

            @Override
            protected void populateItem(ListItem<SolrTweet> item) {
                SolrTweet tweet = item.getModelObject();
                final SolrUser user = tweet.getFromUser();
                final OneTweet oneTweetPanel = createOneTweetPanel("oneTweet").init(new Model<SolrTweet>(tweet), false).setLanguage(LANGUAGE);
                Link showLatestTweets = new Link("profileUrl") {

                    @Override
                    public void onClick() {
                        oneTweetPanel.onUserClick(user.getScreenName());
                    }
                };
                item.add(showLatestTweets.add(new ContextImage("profileImg", user.getProfileImageUrl())));
                item.add(oneTweetPanel);
            }
        };
        add(tweetView);
    }

    public OneTweet createOneTweetPanel(String id) {
        throw new UnsupportedOperationException("overwrite this");
    }

    public void setAds(Collection<SolrTweet> adList) {
        tweets.clear();
        if (adList.size() > 0) {
            int index = rand.nextInt(adList.size());
            int rt = adList.iterator().next().getRetweetCount();
            Iterator<SolrTweet> iter = adList.iterator();
            for (int i = 0; i < adList.size(); i++) {
                SolrTweet tw = iter.next();
                if (i == index || tw.getRetweetCount() != rt) {
                    tweets.add(tw);
                    break;
                }
            }
        }
    }
}
