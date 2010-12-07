/*
 *  Copyright 2010 Peter Karich jetwick_@_pannous_._info
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetwick.solr;

import de.jetwick.util.Helper;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import org.apache.solr.client.solrj.SolrQuery;

/**
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class SavedSearch implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String SS_KEY = "{!key=ss:";
    private String name;
    private SolrQuery query;
    private Date lastQueryDate;
    private final long id;

    public SavedSearch(long id, SolrQuery query) {
        this(id, null, query);
    }

    SavedSearch(long id, String name, SolrQuery query) {
        this.name = name;
        this.id = id;
        this.query = query.getCopy();
        JetwickQuery.removeFilterQueries(this.query, SolrTweetSearch.DATE);
        JetwickQuery.removeFacets(this.query);
    }

    public static boolean isSavedSearch(String str) {
        return str.startsWith(SS_KEY);
    }

    public Date getLastQueryDate() {
        return lastQueryDate;
    }

    public void setLastQueryDate(Date lastQueryDate) {
        this.lastQueryDate = lastQueryDate;
    }

    public SolrQuery getCleanQuery() {
        return query;
    }

    public SolrQuery getQuery(Collection<SavedSearch> savedSearchesColl) {
        SolrQuery tmpQ = query.getCopy();
        TweetQuery.attachFacetibility(tmpQ);
        TweetQuery.updateSavedSearchFacets(tmpQ, savedSearchesColl);
        if (lastQueryDate != null)
            tmpQ.addFilterQuery(getLastQueryDateFilter());
        lastQueryDate = new Date();
        return tmpQ;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        if (name == null)
            return query.getQuery();
        return name;
    }

    public String getQueryTerm() {
        return query.getQuery();
    }

    public long getId() {
        return id;
    }

    public String calcFacetQuery() {
        //sort => remove
        //q=>  tw:(Apache AND Lucene) OR dest_title_t:(xy AND xy)
        //fq=> user:(timetabling)

        // in tweet index we are using dismax so transform into OR query
        String qStr = "";
        if (query.getQuery() != null) {
            int counter = 0;
            for (String term : query.getQuery().split(" ")) {
                if (counter > 0)
                    qStr += " AND ";

                qStr += term;
                counter++;
            }
        }
        String facetQuery = "";
        if (qStr.isEmpty())
            facetQuery += "*:*";
        else
            facetQuery += "tw:(" + qStr + ") OR dest_title_t:(" + qStr + ")";

        // recognize lang, quality and crt_b
        if (query.getFilterQueries() != null) {
            int counter = 0;
            for (String fq : query.getFilterQueries()) {
                // apply only lastQueryDate
                if (fq.startsWith(SolrTweetSearch.DATE + ":"))
                    continue;
                if (fq.startsWith(SolrTweetSearch.FIRST_URL_TITLE + ":"))
                    continue;

                if (counter == 0)
                    facetQuery = "(" + facetQuery + ")";

                facetQuery += " AND " + fq;
                counter++;
            }
        }
        if (lastQueryDate != null)
            facetQuery += " AND " + getLastQueryDateFilter();

        return SS_KEY + id + "}" + facetQuery;
    }

    private String getLastQueryDateFilter() {
        return SolrTweetSearch.DATE + ":[" + Helper.toLocalDateTime(lastQueryDate) + " TO *]";
    }

    @Override
    public String toString() {
        return getCleanQuery().toString();
    }
}
