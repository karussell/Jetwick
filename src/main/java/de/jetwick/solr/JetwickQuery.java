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

import org.apache.solr.client.solrj.SolrQuery;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class JetwickQuery extends SolrQuery {

    public JetwickQuery(String queryStr) {
        init(queryStr);
    }

    public JetwickQuery() {
        init("");
    }

    public JetwickQuery setSort(String sortParam) {
        return (JetwickQuery) setSort(this, sortParam);
    }

    public static SolrQuery setSort(SolrQuery resQuery, String sortParam) {
        resQuery.set("sort", sortParam);
        return resQuery;
    }

    public static SolrQuery createIdQuery(long id) {
        return new SolrQuery().addFilterQuery("id:" + id).setRows(2);
    }

    public JetwickQuery init(String queryStr) {
        if (queryStr == null)
            queryStr = "";
        queryStr = queryStr.replaceAll("--", "-").trim();
        if (queryStr.equals("*") || queryStr.isEmpty())
            queryStr = "";
        setQuery(queryStr);
        return attachFacetibility();
    }

    public JetwickQuery attachFacetibility() {
        return this;
    }

    public JetwickQuery addUserFilter(String userName) {
        if (userName != null && !userName.trim().isEmpty()) {
            userName = trimUserName(userName);
            if (userName.contains(" "))
                userName = "\"" + userName + "\"";

            addFilterQuery(SolrTweetSearch.FILTER_KEY_USER + userName);
        }
        return this;
    }

    public static String extractQueryString(SolrQuery query) {
        String visibleString = query.getQuery();
        if (visibleString != null && visibleString.length() > 0)
            return visibleString;

        return "";
    }

    public static String extractUserName(SolrQuery query) {
        String tmp = SolrTweetSearch.FILTER_KEY_USER;
        if (query.getFilterQueries() != null)
            for (String f : query.getFilterQueries()) {
                if (f.startsWith(tmp)) {
                    tmp = f.substring(tmp.length()).trim();
                    if (tmp.length() > 1 && tmp.startsWith("\"") && tmp.endsWith("\""))
                        tmp = tmp.substring(1, tmp.length() - 1);
                    return trimUserName(tmp);
                }
            }

        return "";
    }

    public static String trimUserName(String userName) {
        userName = userName.toLowerCase();
        if (userName.startsWith("@"))
            userName = userName.substring(1);
        return userName;
    }
}
