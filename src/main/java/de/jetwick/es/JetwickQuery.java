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
package de.jetwick.es;

import java.util.LinkedHashMap;
import java.util.Map;
import de.jetwick.data.JTweet;
import java.util.Collection;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.elasticsearch.index.query.xcontent.FilterBuilders;
import org.elasticsearch.index.query.xcontent.RangeFilterBuilder;
import org.elasticsearch.index.query.xcontent.XContentFilterBuilder;
import org.elasticsearch.index.query.xcontent.XContentQueryBuilder;
import org.elasticsearch.search.facet.AbstractFacetBuilder;
import org.elasticsearch.search.facet.FacetBuilders;
import de.jetwick.util.Helper;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.search.sort.SortOrder;
import de.jetwick.util.MapEntry;
import de.jetwick.util.MyDate;
import de.jetwick.util.StrEntry;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.elasticsearch.index.query.xcontent.QueryBuilders;
import static de.jetwick.es.ElasticTweetSearch.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public abstract class JetwickQuery implements Serializable {

    private static final Logger logger = LoggerFactory.getLogger(JetwickQuery.class);
    private static final long serialVersionUID = 1L;
    public final static String SAVED_SEARCHES = "ss";
    private int from;
    private int size = 10;
    private String queryString;
    private boolean escape = false;
    private List<StrEntry> sortFields = new ArrayList<StrEntry>();
    private List<Entry<String, Object>> filterQueries = new ArrayList<Entry<String, Object>>();
    private Map<String, Integer> facetFields = new LinkedHashMap<String, Integer>();
    private List<StrEntry> facetQueries = new ArrayList<StrEntry>();
    private boolean dateFacets = false;

    public JetwickQuery() {
        this(null, false);
    }

    public JetwickQuery(boolean facets) {
        this(null, facets);
    }

    public JetwickQuery(String queryStr, boolean facets) {
        init(queryStr, facets);
    }

    public SearchRequestBuilder initRequestBuilder(SearchRequestBuilder srb) {
        Integer rows = getSize();

        if (rows == null)
            rows = 10;

        Integer start = getFrom();
        if (start == null)
            start = 0;

        srb.setSearchType(SearchType.QUERY_THEN_FETCH).//QUERY_AND_FETCH would return too many results
                setFrom(start).setSize(rows);//.setExplain(true);

        for (StrEntry e : getSortFields()) {
            if ("asc".equals(e.getValue()))
                srb.addSort(e.getKey(), SortOrder.ASC);
            else if ("desc".equals(e.getValue()))
                srb.addSort(e.getKey(), SortOrder.DESC);
        }

        XContentQueryBuilder qb = createQuery(getQuery());
        qb = processFilterQueries(qb);
        processFacetFields(srb);
        processFacetQueries(srb);
        srb.setQuery(qb);

        return srb;
    }

    public void processFacetFields(SearchRequestBuilder srb) {
        for (Entry<String, Integer> ff : getFacetFields().entrySet()) {
            srb.addFacet(fromFacetField(ff.getKey(), ff.getValue()));
        }
    }

    protected void processFacetQueries(SearchRequestBuilder srb) {
        for (StrEntry e : getFacetQueries()) {
            srb.addFacet(fromFacetQuery(e));
        }
    }

    public XContentQueryBuilder processFilterQueries(XContentQueryBuilder qb) {
        XContentFilterBuilder fb = null;
        for (Entry<String, Object> entry : getFilterQueries()) {
            XContentFilterBuilder tmp = fromFilterQuery(entry);
            if (tmp == null)
                continue;
            if (fb != null)
                fb = FilterBuilders.andFilter(fb, tmp);
            else
                fb = tmp;
        }

        if (fb != null)
            return QueryBuilders.filteredQuery(qb, fb);

        return qb;
    }

    public XContentFilterBuilder fromFilterQuery(Entry<String, Object> entry) {
        return filterQuery2Builder(entry.getKey(), entry.getValue());
    }

    public AbstractFacetBuilder fromFacetField(String ff, int limit) {
        return FacetBuilders.termsFacet(ff).field(ff).size(limit);
    }

    public AbstractFacetBuilder fromFacetQuery(StrEntry e) {
        String name = e.getKey() + ":" + e.getValue();
        return FacetBuilders.filterFacet(name, filterQuery2Builder(e.getKey(), e.getValue()));
    }

    public static Object getTermValue(String val) {
        Object newVal = val;
        if (val.startsWith("\"") && val.endsWith("\""))
            newVal = val.substring(1, val.length() - 1);

        // guess the type            
        try {
            newVal = Long.parseLong(val);
        } catch (Exception ex) {
            try {
                newVal = Double.parseDouble(val);
            } catch (Exception ex2) {
                // necessary?
//                    try {
//                        retVal = Boolean.parseBoolean(val);
//                    } catch (Exception ex3) {
//                    }
            }
        }
//        if (newVal instanceof String)
//            newVal = escapeQuery((String) newVal);

        return newVal;
    }

    public String getDefaultAnalyzer() {
        return "search_analyzer";
    }

    protected XContentQueryBuilder createQuery(String queryStr) {
        return QueryBuilders.matchAllQuery();
    }

    public static XContentFilterBuilder filterQuery2Builder(String key, Object input) {
        String val = input.toString();
        if (val.contains(" OR ")) {
            // handle field:(val OR val2 OR ...)
            if (val.startsWith("(") && val.endsWith(")"))
                val = val.substring(1, val.length() - 1);

            String[] res = val.split(" OR ");
            Object[] terms = new Object[res.length];
            for (int i = 0; i < res.length; i++) {
                terms[i] = getTermValue(res[i]);
            }

            return FilterBuilders.termsFilter(key, terms);
        }

        if (val.startsWith("[NOW") || val.startsWith("[DAY")) {
            throw new UnsupportedOperationException("not yet implemented");
        } else if (val.startsWith("[")) {
            val = val.substring(1, val.length() - 1);
            int index1 = val.indexOf(" ");
            if (index1 < 0)
                throw new IllegalStateException("couldn't handle filter " + key + ":" + val);

            RangeFilterBuilder rfb = FilterBuilders.rangeFilter(key);
            Object from = null;
            Object to = null;

            if (!val.startsWith("*") && !val.startsWith("-Infinity")) {
                try {
                    from = Integer.parseInt(val.substring(0, index1));
                } catch (NumberFormatException ex) {
                    from = Helper.toDate(val.substring(0, index1));
                }
                rfb.from(from).includeLower(true);
            }

            if (!val.endsWith("*") && !val.endsWith("Infinity")) {
                String tmp = val.substring(index1 + " TO ".length());
                try {
                    to = Integer.parseInt(tmp);
                } catch (NumberFormatException ex) {
                    to = Helper.toDate(tmp);
                }

                if (from != null)
                    rfb.to(to).includeUpper(true);
                else
                    rfb.lte(to);
            }

            if (from == null && to == null)
                return FilterBuilders.existsFilter(val);

            return rfb;
        } else if (key.startsWith("-")) {
            return FilterBuilders.notFilter(FilterBuilders.termFilter(key.substring(1), getTermValue(val)));
        } else
            return FilterBuilders.termFilter(key, getTermValue(val));
    }

    public static XContentFilterBuilder filters2Builder(Collection<String> filterStrings) {
        List<XContentFilterBuilder> filters = new ArrayList<XContentFilterBuilder>();
        for (String tmpFq : filterStrings) {
            String strs[] = tmpFq.split("\\:");
            if (strs.length != 2)
                throw new UnsupportedOperationException("string split should result in 2 parts but didn't -> " + strs);

            filters.add(filterQuery2Builder(strs[0], strs[1]));
        }
        return FilterBuilders.orFilter(filters.toArray(new XContentFilterBuilder[filters.size()]));
    }

    public JetwickQuery setEscape(boolean b) {
        escape = b;
        return this;
    }

    public boolean isDateFacets() {
        return dateFacets;
    }

    public JetwickQuery setDateFacets(boolean dateFacet) {
        this.dateFacets = dateFacet;
        return this;
    }

    public int getFrom() {
        return from;
    }

    public JetwickQuery setFrom(int from) {
        this.from = from;
        return this;
    }

    public int getSize() {
        return size;
    }

    public JetwickQuery setSize(int size) {
        this.size = size;
        return this;
    }

    public String getQuery() {
        if (queryString == null || queryString.length() == 0)
            queryString = "";

        if (escape)
            return smartEscapeQuery(queryString);

        return queryString;
    }

    public JetwickQuery setQuery(String queryString) {
        this.queryString = queryString;
        return this;
    }

    public JetwickQuery addFacetField(String field, Integer limit) {
        facetFields.put(field, limit);
        return this;
    }

    public JetwickQuery addFacetField(String field) {
        return addFacetField(field, 10);
    }

    public JetwickQuery removeFacets() {
        facetFields.clear();
        facetQueries.clear();
        return this;
    }

    public JetwickQuery clearSort(String field, String order) {
        sortFields.clear();
        return this;
    }

    public JetwickQuery setSort(String sortKey, String sortVal) {
        sortFields.clear();
        addSort(sortKey, sortVal);
        return this;
    }

    private JetwickQuery addSort(String field, String order) {
        sortFields.add(new StrEntry(field, order));
        return this;
    }

    public List<StrEntry> getSortFields() {
        return sortFields;
    }

    public JetwickQuery init(String queryStr, boolean facets) {
        if (queryStr == null)
            queryStr = "";
        queryStr = queryStr.replaceAll("--", "-").trim();
        if (queryStr.isEmpty() || queryStr.equals("*:*") || queryStr.equals("*"))
            queryStr = "";
        setQuery(queryStr);
        if (facets)
            attachFacetibility();

        return this;
    }

    public JetwickQuery attachFacetibility() {
        return this;
    }

    public JetwickQuery attachUserFacets() {
        addFacetField(USER, 10);
        return this;
    }

    public JetwickQuery addUserFilter(String userName) {
        if (userName != null && !userName.trim().isEmpty()) {
            userName = trimUserName(userName);
//            if (userName.contains(" "))
//                userName = "\"" + userName + "\"";

            addFilterQuery(USER, userName);
        }
        return this;
    }

    public String getUserFilter() {
        for (Entry<String, Object> entry : getFilterQueries()) {
            if (entry.getKey().equals(USER))
                return entry.getKey() + ":" + entry.getValue();
        }

        return null;
    }

    public String extractUserName() {
        for (Entry<String, Object> e : getFilterQueries()) {
            if (USER.equals(e.getKey())) {
                String tmp = e.getValue().toString();
                if (tmp.contains(" OR "))
                    return "";
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

    public boolean containsFilter(String key, Object val) {
        String negateKey = "-" + key;
        for (Entry<String, Object> e : getFilterQueries()) {
            if ((e.getKey().equals(key) || e.getKey().equals(negateKey))
                    && e.getValue().equals(val))
                return true;
        }
        return false;
    }

    public boolean containsFilterKey(String filterKey) {
        return getFirstFilterQuery(filterKey) != null;
    }

    public String getFirstFilterQuery(String key) {
        for (Entry<String, Object> e : getFilterQueries()) {
            if (e.getKey().equals(key))
                return e.getValue().toString();
        }
        return null;
    }

    public boolean replaceFilterQuery(String filter) {
        int index = filter.indexOf(":");
        if (index < 0)
            return false;

        String filterKey = filter.substring(0, index);
        String val = filter.substring(index + 1);
        removeFilterQueries(filterKey);
        addFilterQuery(filterKey, val);
        return true;
    }

    /**
     * this method removes the specified filter from the first fq.
     * Example: reduceFilterQuery(q contains fq=test:hi OR test:me, test:hi);
     * after that q contains fq=test:me
     */
    public boolean reduceFilterQuery(String filter) {
        int index = filter.indexOf(":");
        if (index < 0)
            return false;
        String filterKey = filter.substring(0, index);
        String filterValueToReduce = getFirstFilterQuery(filterKey);
        
        if (filterValueToReduce == null)
            return false;

        index = filter.indexOf(filterValueToReduce);
        if (index < 0)
            return false;

        System.out.println("" + getFilterQueries());
        removeFilterQuery(filterKey, filterValueToReduce);
        System.out.println("" + getFilterQueries());
        filterValueToReduce = "";
        int alreadyAdded = 0;
        String res[] = filterValueToReduce.split(" OR ");
        for (int i = 0; i < res.length; i++) {
            if (filter.equals(res[i]))
                continue;

            if (alreadyAdded++ > 0)
                filterValueToReduce += " OR ";

            filterValueToReduce += res[i];
        }

        if (!filterValueToReduce.isEmpty())
            addFilterQuery(filterKey, filterValueToReduce);        

        return true;
    }

    public JetwickQuery addFilterQuery(String field, Object filter) {
        filterQueries.add(new MapEntry(field, filter));
        return this;
    }

    public JetwickQuery removeFilterQuery(String field, Object filter) {
        Iterator<Entry<String, Object>> iter = filterQueries.iterator();
        while (iter.hasNext()) {
            Entry e = iter.next();
            if (e.getKey().equals(field) && e.getValue().equals(filter)) {
                iter.remove();
                break;
            }
        }

        return this;
    }

    public JetwickQuery removeFilterQueries(String filterKey) {
        String negateFilterKey = "-" + filterKey;
        Iterator<Entry<String, Object>> iter = filterQueries.iterator();
        while (iter.hasNext()) {
            Entry e = iter.next();
            if (e.getKey().equals(filterKey) || e.getKey().equals(negateFilterKey))
                iter.remove();
        }

        return this;
    }

    public List<Entry<String, Object>> getFilterQueries() {
        return filterQueries;
    }

    public JetwickQuery getCopy() {
        JetwickQuery q = new TweetQuery().setQuery(queryString);
        q.setFrom(from).setSize(size);

        for (Entry<String, String> e : getSortFields()) {
            q.addSort(e.getKey(), e.getValue());
        }

        for (Entry<String, Object> fq : getFilterQueries()) {
            q.addFilterQuery(fq.getKey(), fq.getValue());
        }

        for (Entry<String, Integer> ff : getFacetFields().entrySet()) {
            q.addFacetField(ff.getKey(), ff.getValue());
        }

        for (Entry<String, String> fq : getFacetQueries()) {
            q.addFacetQuery(fq.getKey(), fq.getValue());
        }

        q.setDateFacets(dateFacets);

        return q;
    }

    public JetwickQuery addFacetQuery(String name, String query) {
        facetQueries.add(new StrEntry(name, query));
        return this;
    }

    public List<StrEntry> getFacetQueries() {
        return facetQueries;
    }

    public Map<String, Integer> getFacetFields() {
        return facetFields;
    }

    public void attachPagability(int page, int hitsPerPage) {
        setFrom(page * hitsPerPage).setSize(hitsPerPage);
    }

    public JetwickQuery addLatestDateFilter(int hours) {
        addFilterQuery(DATE, "[" + new MyDate().minusHours(hours).castToHour().toLocalString() + " TO *]");
        return this;
    }

    public JetwickQuery addNoSpamFilter() {
        addFilterQuery(QUALITY, "[" + (JTweet.QUAL_SPAM + 1) + " TO *]");
        return this;
    }

    public JetwickQuery addNoDupsFilter() {
        addFilterQuery(DUP_COUNT, 0);
        return this;
    }

    public JetwickQuery addIsOriginalTweetFilter() {
        addFilterQuery(IS_RT, false);
        return this;
    }

    @Override
    public String toString() {
        //q=algorithm&fq=quality_i%3A%5B27+TO+*%5D&fq=dups_i%3A%5B*+TO+0%5D&fq=crt_b%3A%22false%22&start=0&rows=15&sort=retw_i+desc
        String res = "";
        res += "start=" + from;
        res += "&rows=" + size;
        if (queryString != null)
            res += "&q=" + Helper.urlEncode(queryString);

        for (Entry<String, String> e : getSortFields()) {
            res += "&sort=" + Helper.urlEncode(e.getKey() + " " + e.getValue());
        }

        for (Entry<String, Object> fq : getFilterQueries()) {
            res += "&fq=" + Helper.urlEncode(fq.getKey() + ":" + fq.getValue().toString());
        }

        for (Entry<String, Integer> ff : getFacetFields().entrySet()) {
            res += "&facet=" + ff.getValue() + "|" + Helper.urlEncode(ff.getKey());
        }

        for (Entry<String, String> fq : getFacetQueries()) {
            res += "&facetQuery=" + Helper.urlEncode(fq.getKey() + ":" + fq.getValue());
        }

        res += "&dateFacets=" + dateFacets;
        return res;
    }

    public static TweetQuery parseQuery(String qString) {
        TweetQuery q = new TweetQuery(false);
        qString = Helper.urlDecode(qString);
        for (String str : qString.split("&")) {
            int index = str.indexOf("=");
            if (str.trim().isEmpty() || index < 0)
                continue;

            String key = str.substring(0, index);
            String val = str.substring(index + 1);

            try {
                if ("dateFacets".equals(key))
                    q.setDateFacets(Boolean.parseBoolean(val));
                else if ("start".equals(key))
                    q.setFrom(Integer.parseInt(val));
                else if ("rows".equals(key))
                    q.setSize(Integer.parseInt(val));
                else if ("q".equals(key))
                    q.setQuery(val);
                else if ("fq".equals(key)) {
                    String strs[] = val.split("\\:");
                    q.addFilterQuery(strs[0], getTermValue(strs[1]));
                } else if ("facetQuery".equals(key)) {
                    String strs[] = val.split("\\:");
                    q.addFacetQuery(strs[0], strs[1]);
                } else if ("facet".equals(key)) {
                    int index2 = val.indexOf("|");
                    if (index2 < 0)
                        q.addFacetField(val);
                    else {
                        int limit = 10;
                        try {
                            limit = Integer.parseInt(val.substring(0, index2));
                        } catch (Exception ex) {
                        }
                        q.addFacetField(val.substring(index2 + 1), limit);
                    }
                } else if ("sort".equals(key)) {
                    String strs[] = val.split(" ");
                    q.setSort(strs[0], strs[1]);
                }
            } catch (Exception ex) {
                logger.error("Couldn't parse " + key + ":" + val + " in query:" + qString, ex);
            }
        }

        return q;
    }

    public static String smartEscapeQuery(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '!' || c == '(' || c == ')' || c == ':' || c == '^'
                    || c == '[' || c == ']' || c == '{' || c == '}' || c == '~'
                    || c == '?' || c == '|' || c == '&' || c == ';') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static String escapeQuery(String str) {
        // copied from solrs' ClientUtils.escapeQueryChars        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\' || c == '+' || c == '-' || c == '!' || c == '(' || c == ')' || c == ':'
                    || c == '^' || c == '[' || c == ']' || c == '\"' || c == '{' || c == '}' || c == '~'
                    || c == '*' || c == '?' || c == '|' || c == '&' || c == ';'
                    || Character.isWhitespace(c)) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final JetwickQuery other = (JetwickQuery) obj;
        if (this.from != other.from)
            return false;
        if (this.size != other.size)
            return false;
        if ((this.queryString == null) ? (other.queryString != null) : !this.queryString.equals(other.queryString))
            return false;
        if (this.sortFields != other.sortFields && (this.sortFields == null || !this.sortFields.equals(other.sortFields)))
            return false;
        if (this.filterQueries != other.filterQueries && (this.filterQueries == null || !this.filterQueries.equals(other.filterQueries)))
            return false;
        if (this.facetFields != other.facetFields && (this.facetFields == null || !this.facetFields.equals(other.facetFields)))
            return false;
        if (this.facetQueries != other.facetQueries && (this.facetQueries == null || !this.facetQueries.equals(other.facetQueries)))
            return false;
        if (this.dateFacets != other.dateFacets)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 41 * hash + this.from;
        hash = 41 * hash + this.size;
        hash = 41 * hash + (this.queryString != null ? this.queryString.hashCode() : 0);
        hash = 41 * hash + (this.sortFields != null ? this.sortFields.hashCode() : 0);
        hash = 41 * hash + (this.filterQueries != null ? this.filterQueries.hashCode() : 0);
        hash = 41 * hash + (this.facetFields != null ? this.facetFields.hashCode() : 0);
        hash = 41 * hash + (this.facetQueries != null ? this.facetQueries.hashCode() : 0);
        hash = 41 * hash + (this.dateFacets ? 1 : 0);
        return hash;
    }

    public static String toString(ToXContent tmp) {
        try {
            return tmp.toXContent(JsonXContent.unCachedContentBuilder(), ToXContent.EMPTY_PARAMS).
                    prettyPrint().
                    string();
        } catch (Exception ex) {
            return "<ERROR:" + ex.getMessage() + ">";
        }
    }
}
