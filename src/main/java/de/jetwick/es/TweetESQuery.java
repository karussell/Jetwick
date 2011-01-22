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

import de.jetwick.solr.SolrTweet;
import de.jetwick.tw.cmd.TermCreateCommand;
import de.jetwick.util.MyDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.elasticsearch.index.query.xcontent.DisMaxQueryBuilder;
import org.elasticsearch.index.query.xcontent.FilterBuilders;
import org.elasticsearch.index.query.xcontent.QueryBuilders;
import org.elasticsearch.index.query.xcontent.RangeFilterBuilder;
import org.elasticsearch.index.query.xcontent.XContentQueryBuilder;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetESQuery {

    private final static double MM_BORDER = 0.7;
    private SearchRequestBuilder builder;
    private XContentQueryBuilder qb;

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
        Set<String> set = new LinkedHashSet<String>();
        String[] termsArray =  new String[terms.size()];
        int counter = 0;
        for (Entry<String, Integer> entry : terms) {
            termsArray[counter++] = Solr2ElasticTweet.cleanupQuery(entry.getKey());
        }

        int mmTweets = (int) Math.round(terms.size() * MM_BORDER);
        // maximal 6 terms
        mmTweets = Math.min(6, mmTweets);
        // minimal 4 terms
        mmTweets = Math.max(4, mmTweets);
        qb = QueryBuilders.termsQuery(ElasticTweetSearch.TWEET_TEXT, termsArray).minimumMatch(mmTweets);
//        qb = QueryBuilders.queryString(sb.toString()).field(ElasticTweetSearch.TWEET_TEXT);

        qb = QueryBuilders.filteredQuery(qb, FilterBuilders.termsFilter(ElasticTweetSearch.IS_RT, "false"));
        qb = new DisMaxQueryBuilder().add(qb);
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
}
