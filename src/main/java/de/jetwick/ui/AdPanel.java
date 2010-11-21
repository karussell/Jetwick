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

import de.jetwick.data.YUser;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.basic.Label;
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
public class AdPanel extends Panel {

    private ListView userView;

    public AdPanel(String id) {
        super(id);

        // todo later fetch from different solr index
        userView = new ListView("users", getUsers()) {

            @Override
            public void populateItem(final ListItem item) {
                final SearchEntry searchObj = (SearchEntry) item.getModelObject();

                String name = searchObj.title;

                Link link = new Link("profileUrl") {

                    @Override
                    public void onClick() {
                        PageParameters pp = new PageParameters();
                        pp.add("u", searchObj.user);
                        pp.add("q", searchObj.query);
                        setResponsePage(HomePage.class, pp);
                    }
                };
                link.add(new AttributeAppender("title", new Model(name), " "));
                item.add(link.add(new ContextImage("profileImg", searchObj.imageUrl)).
                        add(new Label("userName", name)));
            }
        };

        add(userView);
    }

    private List<SearchEntry> getUsers() {
        List<SearchEntry> ret = new ArrayList<SearchEntry>();

        ret.add(new SearchEntry("", "kindernothilfe", "http://a0.twimg.com/profile_images/470123432/logoknh-twitter2_normal.jpg", "Kindernothilfe"));
        ret.add(new SearchEntry("", "palestine", "http://a2.twimg.com/profile_images/53290258/800px-Flag_of_Palestine.square_normal.png", "Palestine"));
        ret.add(new SearchEntry("", "wikileaks", "http://a3.twimg.com/profile_images/75414639/WL_Hour_Glass_Bottom_normal.jpg", "Wikileaks"));
        ret.add(new SearchEntry("\"bright eyes\"", "", "img/lifted.jpg", "Bright Eyes"));
        ret.add(new SearchEntry("child soldier", "", "http://a3.twimg.com/profile_images/899865123/logoBW_normal.jpg", "Child Soldier"));
        ret.add(new SearchEntry("\"one laptop per child\"", "", "img/OLPC_logo.png", "One Laptop Per Child"));
        ret.add(new SearchEntry("", "pannous", "http://a2.twimg.com/profile_images/74242702/African_Daisy_normal.gif", "Pannous GmbH"));
        ret.add(new SearchEntry("", "jetwick", "http://a2.twimg.com/profile_images/939144526/jetwick_normal.png", "Jetwick"));

        return ret;
    }

    private YUser newUser(String name, String profile) {
        YUser u = new YUser(name);
        u.setProfileImageUrl(profile);
        return u;
    }

    private static class SearchEntry implements Serializable {

        String query;
        String user;
        String imageUrl;
        String title;

        public SearchEntry(String query, String user, String image, String title) {
            this.query = query;
            this.user = user;
            this.imageUrl = image;
            this.title = title;
        }
    }
}
