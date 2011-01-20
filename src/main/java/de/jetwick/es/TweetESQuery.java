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
import java.util.Collection;
import java.util.Map.Entry;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.xcontent.DisMaxQueryBuilder;
import org.elasticsearch.index.query.xcontent.FilterBuilders;
import org.elasticsearch.index.query.xcontent.QueryBuilders;
import org.elasticsearch.index.query.xcontent.RangeFilterBuilder;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetESQuery {

    private SearchRequestBuilder builder;
    private QueryBuilder qb;

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
        builder.setQuery(new DisMaxQueryBuilder().add(QueryBuilders.termsQuery("xy", "value")));
        return this;
    }

    public SearchRequestBuilder getRequestBuilder() {
        return builder;
    }
    
    public TweetESQuery addLatestDateFilter(int hours) {
        //RangeFilterBuilder rfb = FilterBuilders.rangeFilter(DATE);
        return this;
    }
}
