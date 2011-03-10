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

import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.es.JetwickQuery;
import de.jetwick.util.MapEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.facet.termsstats.TermsStatsFacet;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TagCloudPanel extends Panel {

    private ListView tagListView;
    private List<Entry<String, Long>> tags = new ArrayList<Entry<String, Long>>();
    private long max;

    public TagCloudPanel(String id) {
        super(id);

        tagListView = new ListView("tags", tags) {

            @Override
            public void populateItem(final ListItem item) {
                final Entry<String, Long> tag = (Entry) item.getModelObject();
                Link link = new Link("tagLink") {

                    @Override
                    public void onClick() {
                        onTagClick(tag.getKey());
                    }
                };
                link.add(new AttributeAppender("class", new Model("var-size-" + toInt(tag.getValue())), " "));
                link.add(new AttributeAppender("title", new Model("Search '" + tag.getKey() + "'"), " "));
                link.add(new Label("tagLabel", tag.getKey()));
                item.add(link);

                // no ajax
                Link originLink = new Link("tagOrigin") {

                    @Override
                    public void onClick() {
                        onFindOriginClick(tag.getKey());
                    }
                };
                originLink.add(new AttributeAppender("title", new Model("Find origin of trend '" + tag.getKey() + "'"), " "));
                item.add(originLink);
            }
        };

        add(tagListView);
    }

    protected void onTagClick(String name) {
    }

    protected void onFindOriginClick(String tag) {
    }

    int toInt(long count) {
        if (count == 0)
            return 0;

        return (int) (10.0 * count / max);
    }

    public void update(SearchResponse rsp, JetwickQuery query) {
        tags.clear();
        Set<String> terms = new HashSet<String>();
        if (query.getQuery() != null)
            terms.addAll(Arrays.asList(query.getQuery().split(" ")));

        if (rsp != null && rsp.facets() != null) {
            TermsStatsFacet tf = (TermsStatsFacet) rsp.facets().facet(ElasticTweetSearch.TAG);
            if (tf != null) {
                max = 0;
                Map<String, Long> tmp = new TreeMap<String, Long>();
                for (TermsStatsFacet.Entry e : tf.entries()) {
                    if (terms.contains(e.getTerm()))
                        continue;

                    if (e.getCount() > max)
                        max = e.getCount();

                    tmp.put(e.getTerm(), (long) e.getCount());
                }

                for (Entry<String, Long> entry : tmp.entrySet()) {
                    tags.add(new MapEntry<String, Long>(entry.getKey(), entry.getValue()));
                }
            }
        }

        // hide if visible (do not show if not visible and tags.size > 0)
        if(isVisible())
            setVisible(tags.size() > 0);
    }
}
