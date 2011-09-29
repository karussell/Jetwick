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

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder.Operator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Date;
import de.jetwick.util.StrEntry;
import org.elasticsearch.search.facet.AbstractFacetBuilder;
import de.jetwick.util.Helper;
import de.jetwick.util.MyDate;
import org.elasticsearch.search.facet.FacetBuilders;
import org.elasticsearch.search.facet.range.RangeFacetBuilder;
import java.util.Map.Entry;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import java.util.Collection;
import static de.jetwick.es.ElasticTweetSearch.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetQuery extends JetwickQuery {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final long serialVersionUID = 1L;

    public TweetQuery() {
        super();
    }

    public TweetQuery(boolean init) {
        super(null, init);
    }

    public TweetQuery(String queryStr) {
        super(queryStr, true);
    }

    public TweetQuery(String queryStr, boolean init) {
        super(queryStr, init);
    }
    private transient FilterBuilder dateFilter = null;

    @Override
    public SearchRequestBuilder initRequestBuilder(SearchRequestBuilder srb) {
        // the dateFilter should not apply to the date facets!
        dateFilter = null;
        srb = super.initRequestBuilder(srb);

        if (dateFilter != null)
            srb.setFilter(dateFilter);

        if (isDateFacets()) {
            // too much work to convert the generic case with all the date math
            // so cheat for our case:
            String name = ElasticTweetSearch.DATE_FACET;
            RangeFacetBuilder rfb = FacetBuilders.rangeFacet(name).field(ElasticTweetSearch.DATE);
            MyDate date = new MyDate();

            // latest
            rfb.addUnboundedTo(Helper.toLocalDateTime(date.clone().minusHours(8).castToHour().toDate()));
            // first day            
            rfb.addUnboundedTo(Helper.toLocalDateTime(date.castToDay().toDate()));

            for (int i = 0; i < 7; i++) {
                // 'from' must be smaller than 'to'!
                Date oldDate = date.toDate();
                rfb.addRange(Helper.toLocalDateTime(date.minusDays(1).toDate()),
                        Helper.toLocalDateTime(oldDate));
            }

            // oldest
            rfb.addUnboundedFrom(Helper.toLocalDateTime(date.toDate()));
            srb.addFacet(rfb);
        }

        return srb;
    }

    @Override
    public FilterBuilder fromFilterQuery(Entry<String, Object> entry) {
        FilterBuilder tmp = super.fromFilterQuery(entry);
        if (entry.getKey().equals(ElasticTweetSearch.DATE)) {
            if (dateFilter != null)
                dateFilter = FilterBuilders.andFilter(dateFilter, tmp);
            else
                dateFilter = tmp;
            return null;
        } else
            return tmp;
    }

    @Override
    public AbstractFacetBuilder fromFacetField(String ff, int limit) {
        AbstractFacetBuilder facetBuilder;
//        if (ff.equals(ElasticTweetSearch.FIRST_URL_TITLE) || ff.equals(ElasticTweetSearch.TAG)) {
        // hmmh no real differences ... strange
//            facetBuilder = FacetBuilders.termsStatsFacet(ff).keyField(ff).valueScript("doc.score").order(ComparatorType.TOTAL).size(limit);
//                    fb = FacetBuilders.termsStats(ff).keyField(ff).valueScript("doc.relevance.value").order(ComparatorType.TOTAL);//.size(15);
//                    fb = FacetBuilders.termsStats(ff).keyField(ff).valueScript("doc.relevance.value").order(ComparatorType.COUNT).size(15);
//        } else        
        facetBuilder = super.fromFacetField(ff, limit);

        if (dateFilter != null)
            facetBuilder.facetFilter(dateFilter);

        return facetBuilder;
    }

    @Override
    public AbstractFacetBuilder fromFacetQuery(StrEntry e) {
        AbstractFacetBuilder facetBuilder = super.fromFacetQuery(e);
        if (dateFilter != null)
            facetBuilder.facetFilter(dateFilter);
        return facetBuilder;
    }

    @Override
    public TweetQuery attachFacetibility() {
//        setDateFacets(true).
                addFacetField(TAG, 15).addFacetField(LANG).
                // originality
                addFacetField(IS_RT);
//                addFacetField(FIRST_URL_TITLE);

//        // latest
//        q.addFacetQuery(FILTER_ENTRY_LATEST_DT);
//        // archive
//        q.addFacetQuery(FILTER_ENTRY_OLD_DT);

        addFacetQuery(RT_COUNT, "[5 TO *]");
        addFacetQuery(RT_COUNT, "[20 TO *]");
        addFacetQuery(RT_COUNT, "[50 TO *]");

        addFacetQuery(DUP_COUNT, "0");
        addFacetQuery(DUP_COUNT, "[1 TO *]");

        // spam
//        q.addFacetQuery(FILTER_SPAM);
//        q.addFacetQuery(FILTER_NO_SPAM);

        // links
        addFacetQuery(URL_COUNT, "[1 TO *]");
        addFacetQuery(URL_COUNT, "0");

        return this;
    }

    public TweetQuery createFriendsQuery(Collection<String> friends) {
        return (TweetQuery) super.createFriendsQuery("user", friends);
    }

    @Override
    protected QueryBuilder createQuery(String queryStr) {
        QueryBuilder qb;
        if (queryStr == null || queryStr.isEmpty())
            qb = QueryBuilders.matchAllQuery();
        else {
            // fields can also contain patterns like so name.* to match more fields
            qb = QueryBuilders.queryString(queryStr).defaultOperator(Operator.AND).
                    field(ElasticTweetSearch.TWEET_TEXT).field(ElasticTweetSearch.TITLE).field(ElasticTweetSearch.USER, 0).
                    allowLeadingWildcard(false).analyzer(getDefaultAnalyzer()).useDisMax(true);
        }

        return qb;
//        return QueryBuilders.customScoreQuery(qb).script("_score * doc['relevancy'].value").lang("js");

//        long time = new MyDate().castToHour().getTime();
//        return customScoreQuery(qb)
//                .script(
//                "var boost = _score;"
//                + "if(doc['tw_i'].value <= 30) boost *= 0.1;"
//                + "if(doc['quality_i'].value <= 65) boost *= 0.1;"
//                + "var retweet = doc['retw_i'].value;"
//                + "var scale = 10000;"// time vs. retweet -> what should be more important? +0.1 because boost should end up to be 0 for 0 retweets
//                + "if(retweet <= 100) boost *= 0.1 + retweet / scale; else boost *= 0.1 + 100 / scale;"
//                + "boost / (3.6e-9 * (mynow - doc['dt'].value) + 1);"                
//                ).
//                lang("js").param("mynow", time);
    }

    @Override
    public JetwickQuery addLatestDateFilter(int hours) {
        return addLatestDateFilter(new MyDate().minusHours(hours).castToHour());
    }

    @Override
    public JetwickQuery addLatestDateFilter(MyDate date) {
        addFilterQuery(DATE, "[" + date.toLocalString() + " TO *]");
        return this;
    }
}
