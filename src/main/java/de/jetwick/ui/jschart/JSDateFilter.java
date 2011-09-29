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
package de.jetwick.ui.jschart;

import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.ui.util.FacetHelper;
import de.jetwick.ui.util.LabeledLink;
import de.jetwick.util.Helper;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxFallbackLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.facet.range.RangeFacet;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class JSDateFilter extends Panel {

    private List<FacetHelper> facetList = new ArrayList<FacetHelper>();
    private String dtKey = ElasticTweetSearch.DATE;
    private final float MAX_HEIGHT_IN_PX = 50.0f;
    private long max = 1;
    private long totalHits = 0;

    public JSDateFilter(String id) {
        super(id);

        final String dtVal = "Date Filter";
        List<String> dateFilterList = new ArrayList<String>();
        dateFilterList.add(dtKey);

        // TODO WICKET update dateFilter even if we call only update(rsp)
        ListView dateFilter = new ListView("dateFilterParent", dateFilterList) {

            @Override
            public void populateItem(final ListItem item) {
                String filter = getFilterName(dtKey);
                if (filter != null) {
                    item.add(new LabeledLink("dateFilter", "Click to remove custom date filter") {

                        @Override
                        public void onClick(AjaxRequestTarget target) {
                            onFilterChange(target, dtKey, null);
                        }
                    }.add(new AttributeAppender("title", new Model("Remove all filters from '" + dtVal + "'"), " ")));
                } else {
                    String str = "";
                    if (totalHits > 0)
                        str = "Select a date to filter results";
                    Label label = new Label("dateFilter", str);
                    label.add(new AttributeAppender("class", new Model("gray"), " "));
                    item.add(label);
                }
            }
        };
        add(dateFilter);

        ListView items = new ListView("items", facetList) {

            @Override
            public void populateItem(final ListItem item) {
                float zoomer = MAX_HEIGHT_IN_PX / max;
                final FacetHelper entry = (FacetHelper) item.getModelObject();

                Label bar = new Label("itemSpan");
                String additionalDateInfo = entry.count + " tweets";
                String displayName = entry.displayName;
                try {
                    Date date = Helper.toDate(displayName);
                    int index = displayName.indexOf("T");
                    if (index > 0)
                        additionalDateInfo += " on " + Helper.getMonthDay(date);
                    
                    displayName = Helper.getWeekDay(date);
                } catch (Exception ex) {
                }

                AttributeAppender app = new AttributeAppender("title", new Model(additionalDateInfo), " ");
                bar.add(app).add(new AttributeAppender("style", new Model("height:" + (int) (zoomer * entry.count) + "px"), " "));
                final boolean selected = isAlreadyFiltered(entry.key, entry.value);
                Link link = new /*Indicating*/ AjaxFallbackLink("itemLink") {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        JSDateFilter.this.onFilterChange(target, entry.getFilter(), !selected);
                    }
                };
                link.add(app);
                Label label = new Label("itemLabel", displayName);
                link.add(bar).add(label);
                if (entry.count == 0) {
                    link.setEnabled(false);
                    link.add(new AttributeAppender("class", new Model("gray"), " "));
                }

                if (selected)
                    link.add(new AttributeAppender("class", new Model("filter-rm"), " "));
                else
                    link.add(new AttributeAppender("class", new Model("filter-add"), " "));
                item.add(link);
            }
        };

        add(items);
    }

    protected void onFilterChange(AjaxRequestTarget target, String filter, Boolean selected) {
    }

    protected boolean isAlreadyFiltered(String key, Object value) {
        return false;
    }

    public String getFilterName(String name) {
        return name;
    }

    public void update(SearchResponse rsp) {
        facetList.clear();
        if (rsp == null)
            return;

        if (rsp != null && rsp.facets() != null) {
            RangeFacet rf = rsp.facets().facet(ElasticTweetSearch.DATE_FACET);
            if (rf != null) {
                int counter = 0;
                for (RangeFacet.Entry e : rf.entries()) {
                    String display = e.getFromAsString();
                    String fromStr = e.getFromAsString();
                    String toStr = e.getToAsString();
                    counter++;
                    if (counter == rf.entries().size()) {
                        display = "Older";
                        fromStr = "*";
                    } else if (counter == 1) {
                        display = "Last 8h";
                        toStr = "*";
                    } else if (counter == 2) {
                        display = "Today";     
                    }
                    
                    String filter = "[" + fromStr + " TO " + toStr + "]";
//                    System.out.println(display + " " + filter + " " + e.getCount());
                    facetList.add(new FacetHelper(dtKey, filter, display, e.getCount()));
                }
            }
        }
        max = 1;
        for (FacetHelper h : facetList) {
            if (h.count > max)
                max = h.count;
        }
        totalHits = rsp.hits().getTotalHits();
    }

    public List<FacetHelper> getFacetList() {
        return facetList;
    }

    public static Integer getFacetQueryCount(Map<String, Integer> facetQueries, String entry) {
        if (facetQueries != null)
            return facetQueries.get(entry);
        return null;
    }
}
