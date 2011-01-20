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

import de.jetwick.tw.cmd.TermCreateCommand;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.solr.client.solrj.SolrQuery;
import static de.jetwick.es.ElasticTweetSearch.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetQuery extends JetwickQuery {

    public TweetQuery() {
    }

    public TweetQuery(boolean init) {
        super(init);
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
                set("facet.date", DATE);
//                set("facet.date.start", DATE_START).
//                set("facet.date.end", "NOW/DAY+1DAY").
//                set("facet.date.gap", "+1DAY");

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

        // TODO ES
//        // latest
//        q.addFacetQuery(FILTER_ENTRY_LATEST_DT);
//        // archive
//        q.addFacetQuery(FILTER_ENTRY_OLD_DT);

        q.addFacetQuery(RT_COUNT + ":[5 TO *]");
        q.addFacetQuery(RT_COUNT + ":[20 TO *]");
        q.addFacetQuery(RT_COUNT + ":[50 TO *]");

        // TODO ES
//        q.addFacetQuery(FILTER_NO_DUPS);
//        q.addFacetQuery(FILTER_ONLY_DUPS);
//
//        // spam
//        q.addFacetQuery(FILTER_SPAM);
//        q.addFacetQuery(FILTER_NO_SPAM);
//
//        // links
//        q.addFacetQuery(FILTER_URL_ENTRY);
//        q.addFacetQuery(FILTER_NO_URL_ENTRY);

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
    private final static double MM_BORDER = 0.7;

    public TweetQuery createSimilarQuery(SolrTweet tweet) {
        new TermCreateCommand().calcTermsWithoutNoise(tweet);
        return createSimilarQuery(tweet.getTextTerms().getSortedTermLimited(8));
    }

    private TweetQuery createSimilarQuery(Collection<Entry<String, Integer>> terms) {
        Set<String> set = new LinkedHashSet<String>();

        for (Entry<String, Integer> entry : terms) {
            set.add(entry.getKey());
        }

        StringBuilder sb = new StringBuilder();
        for (String str : set) {
            sb.append(str);
            sb.append(" ");
        }

        // force dismax and specify required matching terms
        set("qf", TWEET_TEXT);
        set("defType", "dismax");
        // TODO can we use solr settings instead?
        int mmTweets = (int) Math.round(terms.size() * MM_BORDER);
        // maximal 6 terms
        mmTweets = Math.min(6, mmTweets);
        // minimal 4 terms
        mmTweets = Math.max(4, mmTweets);
        set("mm", "" + mmTweets);
        return (TweetQuery) setQuery(cleanupQuery(sb.toString())).addFilterQuery(IS_RT + ":\"false\"");
    }

    public static String cleanupQuery(String str) {
        return str.replaceAll("\\|\\|", " ");
    }
}
