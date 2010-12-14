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

import de.jetwick.ui.util.FacetHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.AjaxSelfUpdatingTimerBehavior;
import org.apache.wicket.ajax.markup.html.AjaxFallbackLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.ajax.markup.html.IndicatingAjaxFallbackLink;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SavedSearchPanel extends Panel {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private Map<String, String> tr = new LinkedHashMap<String, String>();
    private List<FacetHelper> savedSearches = new ArrayList<FacetHelper>();
    private ListView savedSearchesView;
    private String SAVED_SEARCHES = "ss";

    public SavedSearchPanel(String id) {
        super(id);

        tr.put(SAVED_SEARCHES, "Saved Searches");
        Link saveSearch = new AjaxFallbackLink("saveSearch") {

            @Override
            public void onClick(AjaxRequestTarget target) {
                SavedSearchPanel.this.onSave(target);
            }
        };
        add(saveSearch);

        AjaxFallbackLink link = new AjaxFallbackLink("saveInfo") {

            @Override
            public void onClick(AjaxRequestTarget target) {
                SavedSearchPanel.this.onSave(target);
            }

            @Override
            public boolean isVisible() {
                return savedSearches.size() == 0;
            }
        };
        add(link);

        savedSearchesView = new ListView("filterValues", savedSearches) {

            @Override
            protected void populateItem(ListItem li) {
                final FacetHelper h = (FacetHelper) li.getModelObject();
                long tmp = -1;
                try {
                    tmp = Long.parseLong(h.value);
                } catch (Exception ex) {
                    logger.error("onFacetChange", ex);
                }
                final long ssId = tmp;
                Link link = new IndicatingAjaxFallbackLink("filterValueLink") {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        SavedSearchPanel.this.onClick(target, ssId);
                    }
                };

                String name = h.displayName;
                if (name.length() > 20)
                    name = name.substring(0, 20) + "..";
                link.add(new Label("filterValue", name));
                link.add(new AttributeAppender("title", true, new Model(h.displayName), " "));
                Label label4count = new Label("filterCount", " (" + h.count + ")");
                if (h.count < 1) {
                    link.add(new AttributeAppender("class", new Model("gray"), " "));
                    label4count.add(new AttributeAppender("class", new Model("gray"), " "));
                }
                    //label4count.add(new AttributeModifier("class", "filter-count", false, new Model("")));

                li.add(label4count);
                li.add(link);

                Link removeLink = new AjaxFallbackLink("removeLink") {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        SavedSearchPanel.this.onRemove(target, ssId);
                    }
                };
                li.add(removeLink);
            }
        };
        add(savedSearchesView);

        add(new AjaxSelfUpdatingTimerBehavior(Duration.seconds(30)) {

            @Override
            protected void onPostProcessTarget(AjaxRequestTarget target) {
                updateSSCounts(target);
            }
        });
    }

    /**
     * Make sure that the facets appear in the order we defined via filterToIndex
     */
    public List<FacetHelper> createFacetsFields(QueryResponse rsp) {
        List<FacetHelper> list = new ArrayList<FacetHelper>();
        Map<String, Integer> facetQueries = null;
        Integer count = null;

        if (rsp != null) {
            facetQueries = rsp.getFacetQuery();
            if (facetQueries != null)
                for (Entry<String, Integer> entry : facetQueries.entrySet()) {
                    if (entry == null)
                        continue;

                    int firstIndex = entry.getKey().indexOf(SAVED_SEARCHES + ":");
                    if (firstIndex < 0)
                        continue;

                    String val = entry.getKey().substring(firstIndex + SAVED_SEARCHES.length() + 1);

                    // do not exclude smaller zero
                    count = entry.getValue();
                    if (count == null)
                        count = 0;

                    list.add(new FacetHelper(SAVED_SEARCHES, val, translate(val), count));
                }
        }
        return list;
    }
    private Comparator<FacetHelper> comp = new Comparator<FacetHelper>() {

        @Override
        public int compare(FacetHelper o1, FacetHelper o2) {
            if (o1.displayName == null)
                return -1;
            else if (o2.displayName == null)
                return 1;
            return o1.displayName.compareTo(o2.displayName);
        }
    };

    public void updateSSCounts(AjaxRequestTarget target) {
    }

    public void update(QueryResponse rsp) {
        savedSearches.clear();
        if (rsp != null) {
            for (FacetHelper helper : createFacetsFields(rsp)) {
                if (helper != null)
                    savedSearches.add(helper);
            }
        }
        Collections.sort(savedSearches, comp);
    }

    public void onClick(AjaxRequestTarget target, long ssId) {
    }

    public void onSave(AjaxRequestTarget target) {
    }

    public void onRemove(AjaxRequestTarget target, long ssId) {
    }

    public String translate(String str) {
        String val = tr.get(str);
        if (val == null)
            return str;

        return val;
    }
}
