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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.AjaxSelfUpdatingTimerBehavior;
import org.apache.wicket.ajax.markup.html.AjaxFallbackLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.ajax.markup.html.IndicatingAjaxFallbackLink;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.time.Duration;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.facet.Facet;
import org.elasticsearch.search.facet.range.RangeFacet;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SavedSearchPanel extends Panel {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private Map<String, String> tr = new LinkedHashMap<String, String>();
    private List<FacetHelper<Long>> savedSearches = new ArrayList<FacetHelper<Long>>();
    private ListView savedSearchesView;
    private String SAVED_SEARCHES = "ss";
    private boolean isInitialized = false;

    public SavedSearchPanel(String id) {
        super(id);

        tr.put(SAVED_SEARCHES, "Saved Searches");
        Link saveSearch = new AjaxFallbackLink("saveSearch") {

            @Override
            public void onClick(AjaxRequestTarget target) {
                SavedSearchPanel.this.onSave(target, new Date().getTime());
            }
        };
        add(saveSearch);

        AjaxFallbackLink link = new AjaxFallbackLink("saveInfo") {

            @Override
            public void onClick(AjaxRequestTarget target) {
                SavedSearchPanel.this.onSave(target, new Date().getTime());
            }

            @Override
            public boolean isVisible() {
                return isInitialized && savedSearches.size() == 0;
            }
        };
        add(link.setOutputMarkupId(true));

        add(new WebMarkupContainer("indicatorImage") {

            @Override
            public boolean isVisible() {
                return !isInitialized;
            }
        }.setOutputMarkupId(true));

        // don't know how to utilize IAjaxIndicatorAware + getAjaxIndicatorMarkupId() { return indicator.getMarkupId();
        //add(indicator);

        savedSearchesView = new ListView("filterValues", savedSearches) {

            @Override
            protected void populateItem(ListItem li) {
                final FacetHelper<Long> h = (FacetHelper<Long>) li.getModelObject();
                long tmp = h.value;
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
                else if (name.length() < 1)
                    name = "<empty>";

                if (ssId == 0)
                    name = "last:" + name;

                link.add(new Label("filterValue", name));
                link.add(new AttributeAppender("title", true, new Model(h.displayName), " "));
                Label label4count = new Label("filterCount", " (" + h.count + ")");
                if (h.count < 1) {
                    link.add(new AttributeAppender("class", new Model("gray"), " "));
                    label4count.add(new AttributeAppender("class", new Model("gray"), " "));
                }

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

        // execute one time
        add(new AjaxSelfUpdatingTimerBehavior(Duration.seconds(0.5)) {

            @Override
            protected void onPostProcessTarget(AjaxRequestTarget target) {
                updateSSCounts(target);
                isInitialized = true;
                stop();
            }
        });

        // execute forever
        add(new AjaxSelfUpdatingTimerBehavior(Duration.seconds(30)) {

            @Override
            protected void onPostProcessTarget(AjaxRequestTarget target) {
                // update count of all queries
                updateSSCounts(target);
            }
        });
    }

    /**
     * Make sure that the facets appear in the order we defined via filterToIndex
     */
    public List<FacetHelper> createFacetsFields(SearchResponse rsp) {
        List<FacetHelper> list = new ArrayList<FacetHelper>();        
        Integer count = null;

        if (rsp != null) {
            List<Facet> facets = rsp.facets().facets();
            if (facets != null)
                for (Facet f : facets){                    
                    if(!(f instanceof TermsFacet)) 
                        continue;
                    
                    int firstIndex = f.getName().indexOf(SAVED_SEARCHES + ":");
                    if (firstIndex < 0)
                        continue;

                    long val = -1;
                    try {
                        val = Long.parseLong(f.getName().substring(firstIndex + SAVED_SEARCHES.length() + 1));
                    } catch (Exception ex) {
                    }

                    // TODO ES
                    // do not exclude smaller zero
//                    count = ((RangeFacet)f).getCount();
//                    if (count == null)
//                        count = 0;
//
//                    list.add(new FacetHelper<Long>(SAVED_SEARCHES, val, translate(val), count));
                }
        }
        return list;
    }
    private static Comparator<FacetHelper> comp = new Comparator<FacetHelper>() {

        @Override
        public int compare(FacetHelper o1, FacetHelper o2) {
            long val1 = (Long) o1.value;
            long val2 = (Long) o2.value;
            if (val1 < val2)
                return -1;
            else if (val1 > val2)
                return 1;
            else
                return 0;
        }
    };

    public void updateSSCounts(AjaxRequestTarget target) {
    }

    public void update(SearchResponse rsp) {
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

    public void onSave(AjaxRequestTarget target, long ssId) {
    }

    public void onRemove(AjaxRequestTarget target, long ssId) {
    }

    public String translate(long val) {
        return "" + val;
    }
}
