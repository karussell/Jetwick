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

package de.jetwick.wikipedia;

import de.jetwick.wikipedia.WikiEntry;
import de.jetwick.wikipedia.Wikipedia;
import java.util.ArrayList;
import java.util.List;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class WikipediaPanel extends Panel {

    private ListView urlListView;
    private List<WikiEntry> urls = new ArrayList<WikiEntry>();

    public WikipediaPanel(String id) {
        super(id);

        urlListView = new ListView("urls", urls) {

            @Override
            public void populateItem(final ListItem item) {
                final WikiEntry url = (WikiEntry) item.getModelObject();

                String str = url.getTitle();
                if (str.length() > 40)
                    str = str.substring(0, 37).trim() + "..";

                ExternalLink link = new ExternalLink("urlLink", url.getUrl(), str);
                item.add(link);

                Label label = new Label("urlLabel", url.getText());
                label.setEscapeModelStrings(false);
                //label.add(new AttributeAppender("title", new Model(url.getText()), " "));
                item.add(label);
            }
        };

        add(urlListView);
    }

    protected void onUrlClick(String name) {
    }

    public void update(String query, String language) {
        urls.clear();
        if (query != null && !query.isEmpty())
            urls.addAll(new Wikipedia().query(query, language));
        setVisible(urls.size() > 0);
    }
}
