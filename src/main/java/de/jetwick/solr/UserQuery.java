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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.jetwick.solr;

import org.elasticsearch.index.query.xcontent.QueryBuilders;
import org.elasticsearch.index.query.xcontent.XContentQueryBuilder;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class UserQuery extends JetwickQuery {

    private static final long serialVersionUID = 1L;
    public UserQuery() {
    }

    public UserQuery(String queryStr) {
        super(queryStr, true);
    }

    @Override
    public UserQuery attachFacetibility() {
        return (UserQuery) addFacetField("tag").addFacetField("lang");
    }
        
    @Override
    protected XContentQueryBuilder createQuery(String queryStr) {
        XContentQueryBuilder qb;
        if (queryStr == null || queryStr.isEmpty())
            qb = QueryBuilders.matchAllQuery();
        else {
            // fields can also contain patterns like so name.* to match more fields
            return QueryBuilders.queryString(escapeQuery(queryStr)).
                    field("name", 10).field("tag", 2).field("bio").field("realName").
                    allowLeadingWildcard(false).useDisMax(true);
//            return QueryBuilders.termQuery("name", queryStr);
        }
        return qb;
    }
}
