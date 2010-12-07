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
import de.jetwick.ui.util.LabeledLink;

import de.jetwick.util.MapEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.ajax.markup.html.IndicatingAjaxFallbackLink;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import static de.jetwick.tw.TweetDetector.*;
import static de.jetwick.solr.SolrTweetSearch.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class FacetPanel extends Panel {

    private Map<String, String> tr = new LinkedHashMap<String, String>();
    private Set<String> alreadyFiltered = new LinkedHashSet<String>();
    private List<Entry<String, List<FacetHelper>>> normalFacetFields = new ArrayList<Entry<String, List<FacetHelper>>>();
    private ListView tagView;
    private String dtKey = "dt";
    private String langKey = "lang";    

    public FacetPanel(String id) {
        super(id);

        // No Massive Tweeter
        tr.put("loc", "Location");
        tr.put("-" + USER, "Spam");
        tr.put(dtKey, "Date");
        tr.put(langKey, "Language");
        tr.put(IS_RT, "Content");
        tr.put(IS_RT + ":true", "retweet");
        tr.put(IS_RT + ":false", "original");

        tr.put("url_i", "Links");
        tr.put(FILTER_URL_ENTRY, "with links");
        tr.put(FILTER_NO_URL_ENTRY, "without links");

        tr.put(RT_COUNT, "Retweets");
        tr.put(RT_COUNT + ":0", "no retweets");
        tr.put(RT_COUNT + ":[1 TO 10]", "few retweets");
        tr.put(RT_COUNT + ":[11 TO *]", "many retweets");

        tr.put(QUALITY, "Spam");
        tr.put(FILTER_NO_SPAM, "No Spam");
        tr.put(FILTER_SPAM, "Only Spam");

        tr.put(langKey + ":" + UNKNOWN_LANG, "Other");
        tr.put(langKey + ":" + DE, "Deutsch");
        tr.put(langKey + ":" + EN, "English");
        tr.put(langKey + ":" + NL, "Nederlandse");
        tr.put(langKey + ":" + RU, "Pусский");
        tr.put(langKey + ":" + SP, "Español");

        tagView = new ListView("filterNames", normalFacetFields) {

            @Override
            public void populateItem(final ListItem item) {
                final Entry<String, List<FacetHelper>> entry = (Entry<String, List<FacetHelper>>) item.getModelObject();

                String dtVal = translate(entry.getKey());
                String filter = getFilterName(entry.getKey());
                if (filter != null) {
                    item.add(new LabeledLink("filterName", "< " + dtVal) {

                        @Override
                        public void onClick(AjaxRequestTarget target) {
                            onFacetChange(target, entry.getKey(), true);
                        }
                    }.add(new AttributeAppender("title", new Model("Remove all filters from '" + dtVal + "'"), " ")));
                } else
                    item.add(new Label("filterName", dtVal));

                item.add(new ListView("filterValues", entry.getValue()) {

                    @Override
                    protected void populateItem(ListItem li) {
                        FacetHelper h = (FacetHelper) li.getModelObject();

                        final String filter = h.getFilter();
                        final boolean selected = alreadyFiltered.contains(filter);

                        Link link = new IndicatingAjaxFallbackLink("filterValueLink") {

                            @Override
                            public void onClick(AjaxRequestTarget target) {
                                onFacetChange(target, filter, !selected);
                            }
                        };
                        // change style if filter is selected
                        if (selected)
                            link.add(new AttributeAppender("class", new Model("filter-rm"), " "));
                        else
                            link.add(new AttributeAppender("class", new Model("filter-add"), " "));

                        link.add(new Label("filterValue", h.displayName));

                        // not clickable if filter would result in 0 docs
                        if (h.count == 0) {
                            link.setEnabled(false);
                            link.add(new AttributeAppender("class", new Model("gray"), " "));
                        }

                        li.add(new Label("filterCount", " (" + h.count + ")"));
                        li.add(link);
                    }
                });
            }
        };

        add(tagView);
    }

    public String getFilterName(String name) {
        name += ":";
        for (String filter : alreadyFiltered) {
            if (filter.startsWith(name)) {
                return filter.substring(name.length());
            }
        }
        return null;
    }

    public void update(QueryResponse rsp, SolrQuery query) {
        normalFacetFields.clear();
        if (rsp != null) {
            long numFound = rsp.getResults().getNumFound();
            for (Entry<String, List<FacetHelper>> entry : createFacetsFields(rsp)) {
                if (entry != null) {
                    if (USER.equals(entry.getKey())) {
                        Entry e = computeSpamExcludeLink(entry.getValue(), numFound);
                        if (e == null)
                            continue;
                        else
                            entry = e;
                    }

                    normalFacetFields.add(entry);
                }
            }
        }
        alreadyFiltered = new LinkedHashSet<String>();
        if (query.getFilterQueries() != null)
            for (String f : query.getFilterQueries()) {
                alreadyFiltered.add(f);
            }
    }

    public Entry<String, List<FacetHelper>> computeSpamExcludeLink(List<FacetHelper> list, long numFound) {
        if (list.size() == 0)
            return null;
        long startCount = list.get(0).count / 2;
        String users = "";
        long diff = 0;
        for (FacetHelper fh : list) {
            if (fh.count <= startCount)
                break;

            users += fh.value + " ";
            diff += fh.count;
        }
        if (users.isEmpty())
            return null;
        users = "(" + users.trim() + ")";
        return new MapEntry<String, List<FacetHelper>>("-" + USER,
                Arrays.asList(new FacetHelper("-" + USER, users, "No Mass Tweeters", numFound - diff)));
    }

    public void onFacetChange(AjaxRequestTarget target, String filter, boolean selected) {
    }

    public boolean isAlreadyFiltered(String filter) {
        return alreadyFiltered.contains(filter);
    }

    public String translate(String str) {
        String val = tr.get(str);
        if (val == null)
            return str;

        return val;
    }

    /**
     * Make sure that the facets appear in the order we defined via filterToIndex
     */
    public List<Entry<String, List<FacetHelper>>> createFacetsFields(QueryResponse rsp) {
        final int MAX_VAL = 4;
        Map<String, Integer> filterToIndex = new LinkedHashMap<String, Integer>() {

            {                
                put(IS_RT, 2);
                put(langKey, 1);
                put(QUALITY, 3);
            }
        };
        List<Entry<String, List<FacetHelper>>> ret = new ArrayList<Entry<String, List<FacetHelper>>>();
        for (int ii = 0; ii < MAX_VAL + 1; ii++) {
            ret.add(null);
        }

        Integer integ;
        Map<String, Integer> facetQueries = null;
        Integer count = null;

        if (rsp != null) {
            List<FacetField> facetFields = rsp.getFacetFields();
            if (facetFields != null)
                for (FacetField ff : facetFields) {
                    integ = filterToIndex.get(ff.getName());
                    if (integ != null && ff.getValues() != null) {
                        List<FacetHelper> list = new ArrayList<FacetHelper>();
                        String key = ff.getName();
                        for (Count cnt : ff.getValues()) {
                            // exclude smaller zero?
                            list.add(new FacetHelper(key, "\"" + cnt.getName() + "\"",
                                    translate(cnt.getAsFilterQuery()), cnt.getCount()));
                        }
                        ret.set(integ, new MapEntry(ff.getName(), list));
                    }
                }

            facetQueries = rsp.getFacetQuery();
            if (facetQueries != null)
                for (Entry<String, Integer> entry : facetQueries.entrySet()) {
                    if (entry == null)
                        continue;

                    int firstIndex = entry.getKey().indexOf(":");
                    if (firstIndex < 0)
                        continue;

                    String key = entry.getKey().substring(0, firstIndex);
                    if (dtKey.equals(key))
                        continue;

                    String val = entry.getKey().substring(firstIndex + 1);

                    // exclude smaller zero?
                    count = entry.getValue();
                    if (count == null)
                        count = 0;

                    Integer index = filterToIndex.get(key);
                    if (index == null)
                        continue;

                    Entry<String, List<FacetHelper>> facetEntry = ret.get(index);
                    List<FacetHelper> list;
                    if (facetEntry == null) {
                        facetEntry = new MapEntry(key, new ArrayList<FacetHelper>());
                        ret.set(index, facetEntry);
                    }

                    list = facetEntry.getValue();
                    list.add(new FacetHelper(key, val, translate(entry.getKey()), count));
                }
        }
        return ret;
    }
}
