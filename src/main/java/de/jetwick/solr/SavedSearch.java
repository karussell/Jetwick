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

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.solr.client.solrj.SolrQuery;

/**
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class SavedSearch {

    private String name;
    private SolrQuery query;
    private Date lastQueryDate;

    public SavedSearch(SolrQuery query) {
        this.query = query;
    }

    public SavedSearch(String name, SolrQuery query) {
        this.name = name;
        this.query = query;
    }

    public Date getLastQueryDate() {
        return lastQueryDate;
    }

    public SolrQuery getQuery() {
        return query;
    }

    public SolrQuery getQueryWithoutDate() {
        SolrQuery tmp = query.getCopy();
        JetwickQuery.removeFilterQueries(tmp, SolrTweetSearch.DATE);
        return tmp;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        if (name == null)
            return query.toString();
        return name;
    }

    public Set<String> calcQueryTerms() {
        Set<String> qTerms = new LinkedHashSet<String>();

        if (getQuery().getQuery() != null && getQuery().getQuery().trim().length() > 0)
            for (String str : getQuery().getQuery().split(" ")) {
                if (str.trim().length() > 0)
                    qTerms.add(str.trim());
            }

        return qTerms;
    }

    @Override
    public String toString() {
        return getQuery().toString();
    }
}
