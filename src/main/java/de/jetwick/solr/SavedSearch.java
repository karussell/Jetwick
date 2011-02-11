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

import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.util.Helper;
import java.io.Serializable;
import java.util.Date;
import org.apache.solr.client.solrj.SolrQuery;

/**
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class SavedSearch implements Serializable {

    private static final long serialVersionUID = 1L;    
    private SolrQuery query;
    private Date lastQueryDate;
    private final long id;

    public SavedSearch(long id, SolrQuery query) {
        this.id = id;
        this.query = query.getCopy();
        JetwickQuery.removeFilterQueries(this.query, ElasticTweetSearch.DATE);
        JetwickQuery.removeFacets(this.query);
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

    public SolrQuery getQuery() {
        SolrQuery tmpQ = query.getCopy();
        TweetQuery.attachFacetibility(tmpQ);
        if (lastQueryDate != null)
            tmpQ.addFilterQuery(getLastQueryDateFilter());
        lastQueryDate = new Date();
        return tmpQ;
    }

    public String getName() {
        String qStr = query.getQuery();
        String userFilter = JetwickQuery.getUserFilter(query);
        if (userFilter != null) {
            if (qStr == null)
                qStr = "";

            if (!qStr.isEmpty())
                qStr += ", ";

            qStr += userFilter;
        }

        return qStr;
    }

    public String getQueryTerm() {
        return query.getQuery();
    }

    public long getId() {
        return id;
    }

    public String calcFacetQuery() {
        //sort => remove
        //q    =>  tw:(Apache AND Lucene) OR dest_title_t:(xy AND xy)
        //fq   => user:(timetabling)

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
            //facetQuery += "tw:(" + qStr + ") OR dest_title_t:(" + qStr + ")";
            facetQuery += qStr;

        // recognize lang, quality and crt_b
        if (query.getFilterQueries() != null) {
            int counter = 0;
            for (String fq : query.getFilterQueries()) {
                // apply only lastQueryDate
                if (fq.startsWith(ElasticTweetSearch.DATE + ":"))
                    continue;
                if (fq.startsWith(ElasticTweetSearch.FIRST_URL_TITLE + ":"))
                    continue;

                // Infinity cannot be passed to boolean query
                fq = fq.replaceAll("Infinity\\]", "*]");
                fq = fq.replaceAll("\\[-Infinity", "*]");

                if (counter == 0)
                    facetQuery = "(" + facetQuery + ")";

                facetQuery += " AND " + fq;
                counter++;
            }
        }
        if (lastQueryDate != null)
            facetQuery += " AND " + getLastQueryDateFilter();

        return facetQuery;
    }

    private String getLastQueryDateFilter() {        
        return ElasticTweetSearch.DATE + ":[" + Helper.toLocalDateTime(lastQueryDate) + " TO *]";
    }

    @Override
    public String toString() {
        return getCleanQuery().toString();
    }
}
