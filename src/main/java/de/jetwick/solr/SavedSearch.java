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
import java.util.Map.Entry;

/**
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class SavedSearch implements Serializable {

    private static final long serialVersionUID = 1L;
    private JetwickQuery query;
    private Date lastQueryDate;
    private final long id;

    public SavedSearch(long id, JetwickQuery query) {
        this.id = id;
        this.query = query.getCopy();
        this.query.removeFilterQueries(ElasticTweetSearch.DATE);
        this.query.removeFacets();
    }

    public Date getLastQueryDate() {
        return lastQueryDate;
    }

    public void setLastQueryDate(Date lastQueryDate) {
        this.lastQueryDate = lastQueryDate;
    }

    public JetwickQuery getCleanQuery() {
        return query;
    }

    public JetwickQuery getQuery() {
        JetwickQuery tmpQ = query.getCopy();
        tmpQ.attachFacetibility();
        if (lastQueryDate != null)
            tmpQ.addFilterQuery(ElasticTweetSearch.DATE, getLastQueryDateFilter());

        lastQueryDate = new Date();
        return tmpQ;
    }

    public String getName() {
        String qStr = query.getQuery();
        String userFilter = query.getUserFilter();
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
            for (Entry<String, Object> fq : query.getFilterQueries()) {
                // apply only lastQueryDate
                if (fq.getKey().equals(ElasticTweetSearch.DATE))
                    continue;
                if (fq.getKey().equals(ElasticTweetSearch.FIRST_URL_TITLE))
                    continue;

                // Infinity cannot be passed to boolean query
                String val = fq.getValue().toString();
                val = val.replaceAll("Infinity\\]", "*]");
                val = val.replaceAll("\\[-Infinity", "*]");

                if (counter == 0)
                    facetQuery = "(" + facetQuery + ")";

                facetQuery += " AND " + fq.getKey() + ":" + fq.getValue().toString();
                counter++;
            }
        }
        if (lastQueryDate != null)
            facetQuery += " AND " + ElasticTweetSearch.DATE + ":" + getLastQueryDateFilter();

        return facetQuery;
    }

    private String getLastQueryDateFilter() {
        return "[" + Helper.toLocalDateTime(lastQueryDate) + " TO *]";
    }

    @Override
    public String toString() {
        return getCleanQuery().toString();
    }
}
