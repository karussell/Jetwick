/*
 * Copyright 2011 Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetwick.es;

import java.util.Iterator;
import org.apache.lucene.analysis.Tokenizer;
import org.slf4j.Logger;
import java.io.IOException;

import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import java.util.LinkedHashSet;
import java.util.Set;
import de.jetwick.solr.SavedSearch;
import de.jetwick.solr.SolrTweet;
import de.jetwick.tw.cmd.TermCreateCommand;
import de.jetwick.util.MyDate;
import java.util.Collection;
import java.util.Map.Entry;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.index.query.xcontent.FilterBuilders;
import org.elasticsearch.index.query.xcontent.QueryBuilders;
import org.elasticsearch.index.query.xcontent.RangeFilterBuilder;
import org.elasticsearch.index.query.xcontent.XContentQueryBuilder;
import org.elasticsearch.search.facet.FacetBuilders;
import org.slf4j.LoggerFactory;
import static org.elasticsearch.common.xcontent.XContentFactory.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetESQuery {

    private final static Logger logger = LoggerFactory.getLogger(TweetESQuery.class);
    private final static double MM_BORDER = 0.7;
    public final static String SAVED_SEARCHES = "ss";
    private SearchRequestBuilder builder;
    private XContentQueryBuilder qb;

    public TweetESQuery() {
    }

    public TweetESQuery(SearchRequestBuilder builder) {
        this.builder = builder;
        builder.setSearchType(SearchType.QUERY_THEN_FETCH);//QUERY_AND_FETCH would return too many results
    }

    public TweetESQuery createSimilarQuery(SolrTweet tweet) {
        new TermCreateCommand().calcTermsWithoutNoise(tweet);
        // getSortedTermLimited was 6
        createSimilarQuery(tweet.getTextTerms().getSortedTermLimited(8));
        return this;
    }

    private TweetESQuery createSimilarQuery(Collection<Entry<String, Integer>> terms) {
        int minMatchNumber = (int) Math.round(terms.size() * MM_BORDER);
        // maximal 6 terms
        minMatchNumber = Math.min(6, minMatchNumber);
        // minimal 4 terms
        minMatchNumber = Math.max(4, minMatchNumber);

        // do we need to escape the terms when querying?
        Collection<String> coll = doSnowballTermsStemming(terms);

        qb = QueryBuilders.termsQuery(ElasticTweetSearch.TWEET_TEXT,
                coll.toArray(new String[coll.size()])).
                minimumMatch(minMatchNumber);

        // use configured stemmer, but querying seems to be slower!
//        BoolQueryBuilder bqb = QueryBuilders.boolQuery().minimumNumberShouldMatch(minMatchNumber);
//        for (Entry<String, Integer> entry : terms) {
//            bqb.should(QueryBuilders.queryString(ElasticTweetSearch.TWEET_TEXT + ":" + Solr2ElasticTweet.escapeQuery(entry.getKey())));
//        }
//
//        qb = bqb;        

        qb = QueryBuilders.filteredQuery(qb, FilterBuilders.termsFilter(ElasticTweetSearch.IS_RT, "false"));
        return this;
    }

    private Collection<String> doSnowballTermsStemming(Collection<Entry<String, Integer>> terms) {
        final Iterator<Entry<String, Integer>> iter = terms.iterator();
        Tokenizer tokenizer = new TokenizerFromSet(new Iterator<String>() {

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public String next() {
                return iter.next().getKey();
            }

            @Override
            public void remove() {
                iter.remove();
            }
        });

        return doSnowballStemming(tokenizer);
    }

    public Set<String> doSnowballStemming(TokenStream ts) {
        Set<String> res = new LinkedHashSet<String>();
        ts = new SnowballFilter(ts, "English");
        try {
            while (ts.incrementToken()) {
                res.add(ts.getAttribute(TermAttribute.class).term());
            }
        } catch (IOException ex) {
            logger.error("Exception while stemming to snoball", ex);
        }

        return res;
    }

    public TweetESQuery createSavedSearchesQuery(Collection<SavedSearch> collSS) {
        for (SavedSearch ss : collSS) {
            builder.addFacet(FacetBuilders.queryFacet(SAVED_SEARCHES + ":" + ss.getId(), QueryBuilders.queryString(ss.calcFacetQuery())));
        }

        builder.setFrom(0).setSize(0);
        qb = QueryBuilders.matchAllQuery();
        return this;
    }

    public SearchRequestBuilder getRequestBuilder() {
        // TODO move filter creation to this point?        
        builder.setQuery(qb);
        return builder;
    }

    public TweetESQuery addLatestDateFilter(int hours) {
        RangeFilterBuilder rfb = FilterBuilders.rangeFilter(ElasticTweetSearch.DATE);
        rfb.gte(new MyDate().castToHour().toDate());
        QueryBuilders.filteredQuery(qb, rfb);

        return this;
    }
//    public TweetESQuery addFilter(String field, Object value) {
//        qb = QueryBuilders.filteredQuery(qb, FilterBuilders.termsFilter(field, value));
//        return this;
//    }
//    
//      public TweetESQuery addNotFilter(String field, Object value) {          
//        qb = QueryBuilders.filteredQuery(qb, FilterBuilders.notFilter(FilterBuilders.termsFilter(field, value)));
//        return this;
//    }

    @Override
    public String toString() {
        try {
            return qb.toXContent(jsonBuilder(), ToXContent.EMPTY_PARAMS).
                    prettyPrint().
                    string();
        } catch (Exception ex) {
            return "<ERROR:" + ex.getMessage() + ">";
        }
    }
}
