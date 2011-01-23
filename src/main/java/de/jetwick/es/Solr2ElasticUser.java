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

import org.elasticsearch.index.query.xcontent.QueryBuilders;
import org.elasticsearch.index.query.xcontent.XContentQueryBuilder;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class Solr2ElasticUser extends Solr2ElasticTweet {

    @Override
    protected XContentQueryBuilder createQuery(String queryStr) {
        XContentQueryBuilder qb;
        if (queryStr == null || queryStr.isEmpty())
            qb = QueryBuilders.matchAllQuery();
        else {
            // fields can also contain patterns like so name.* to match more fields
            return QueryBuilders.queryString(escapeQuery(queryStr)).
                    field("name", 10).field("tag", 2).field("bio").field("realName").
                    allowLeadingWildcard(false).analyzer("search_analyzer").useDisMax(true);
        }
        return qb;
    }
}
