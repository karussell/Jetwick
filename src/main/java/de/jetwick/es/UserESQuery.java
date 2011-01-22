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

import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.elasticsearch.index.query.xcontent.XContentQueryBuilder;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class UserESQuery {

    private SearchRequestBuilder builder;
    private XContentQueryBuilder qb;

    public UserESQuery(SearchRequestBuilder builder) {
        this.builder = builder;
        builder.setSearchType(SearchType.QUERY_THEN_FETCH);//QUERY_AND_FETCH would return too many results
    }

    public SearchRequestBuilder getRequestBuilder() {
        // TODO move filter creation to this point?        
        builder.setQuery(qb);
        return builder;
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
