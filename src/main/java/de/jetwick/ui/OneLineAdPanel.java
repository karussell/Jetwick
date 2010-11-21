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

import de.jetwick.data.AdEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class OneLineAdPanel extends Panel {

    private ListView userView;
    private List<AdEntry> ads = new ArrayList<AdEntry>();

    public OneLineAdPanel(String id) {
        super(id);

        userView = new ListView("users", new PropertyModel(this, "ads")) {

            @Override
            public void populateItem(final ListItem item) {
                final AdEntry searchObj = (AdEntry) item.getModelObject();
                Link link = new Link("profileUrl") {

                    @Override
                    public void onClick() {
                        Entry<String, String> e = searchObj.getQueryUserPairs().iterator().next();
                        PageParameters pp = new PageParameters();
                        pp.add("q", e.getKey());
                        pp.add("u", e.getValue());
                        setResponsePage(HomePage.class, pp);
                    }
                };
                link.add(new AttributeAppender("title", new Model(searchObj.getDescription()), " "));
                item.add(link.add(new ContextImage("profileImg", searchObj.getIconUrl())).
                        add(new Label("userName", searchObj.getTitle())));
            }
        };

        add(userView);
    }

    public void setAds(Collection<AdEntry> adList) {
        ads.clear();
        for (AdEntry e : adList) {
            ads.add(e);
        }
    }
}
