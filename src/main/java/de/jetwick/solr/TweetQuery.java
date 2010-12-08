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
package de.jetwick.solr;

import de.jetwick.tw.TweetDetector;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.solr.client.solrj.SolrQuery;
import static de.jetwick.solr.SolrTweetSearch.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetQuery extends JetwickQuery {

    public TweetQuery() {
    }

    public TweetQuery(String queryStr) {
        super(queryStr);
    }

    @Override
    public TweetQuery attachFacetibility() {
        return (TweetQuery) attachFacetibility(this);
    }

    public static SolrQuery attachFacetibility(SolrQuery q) {
        q.setFacet(true).
                // now date faceting of dt field:
                set("facet.date", "{!ex=dt}" + DATE).
                set("facet.date.start", DATE_START).
                set("facet.date.end", "NOW/DAY+1DAY").
                set("facet.date.gap", "+1DAY");

        q.setFacetSort("count").
                //show also empty facets to offer a more 'static' UI
                // setFacetMinCount(1).
                setFacetLimit(10).
                addFacetField(TAG).addFacetField("lang").
                // originality
                addFacetField(IS_RT).
                addFacetField(FIRST_URL_TITLE).
                //addFacetField(USER).set("f.user.facet.mincount", 1).set("f.user.facet.limit", 5).
                set("f.dest_title_1_s.facet.mincount", 1).
                set("f.dest_title_1_s.facet.limit", 12).
                set("f.tag.facet.mincount", 2).
                set("f.tag.facet.limit", 20);

        // latest
        q.addFacetQuery(FILTER_ENTRY_LATEST_DT);
        // archive
        q.addFacetQuery(FILTER_ENTRY_OLD_DT);

//        q.addFacetQuery(RT_COUNT + ":[11 TO *]");
//        q.addFacetQuery(RT_COUNT + ":[1 TO 10]");
//        q.addFacetQuery(RT_COUNT + ":0");

        // spam
        q.addFacetQuery(FILTER_SPAM);
        q.addFacetQuery(FILTER_NO_SPAM);

        // links
        q.addFacetQuery(FILTER_URL_ENTRY);
        q.addFacetQuery(FILTER_NO_URL_ENTRY);

        return q;
    }

    public static SolrQuery updateSavedSearchFacets(SolrQuery q, Collection<SavedSearch> sss) {
        String[] facQ = q.getFacetQuery();
        if (facQ != null)
            for (String str : facQ) {
                if (SavedSearch.isSavedSearch(str))
                    q.removeFacetQuery(str);
            }

        if (sss != null)
            for (SavedSearch ss : sss) {
                q.addFacetQuery(ss.calcFacetQuery());
            }
        return q;
    }

    public TweetQuery createSimilarQuery(SolrTweet tweet) {
        String text;
        if (tweet.isRetweet())
            text = tweet.extractRTText();
        else
            text = tweet.getText();

        StringBuilder sb = new StringBuilder();
        Set<String> set = new LinkedHashSet<String>();
        for (Entry<String, Integer> entry :
                new TweetDetector().runOne(text).getSortedTerms()) {

            set.add(entry.getKey());
        }

        for (String str : set) {
            sb.append(str);
            sb.append(" ");
        }

        return (TweetQuery) setQuery(sb.toString()).addFilterQuery(IS_RT + ":\"false\"");
    }
}
