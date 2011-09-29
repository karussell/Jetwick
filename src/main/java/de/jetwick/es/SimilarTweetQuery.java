/*
 * Copyright 2011 Peter Karich, jetwick_@_pannous_._info.
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

import de.jetwick.data.JTweet;
import de.jetwick.tw.cmd.TermCreateCommand;
import de.jetwick.util.Helper;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class SimilarTweetQuery extends TweetQuery {

    private double mmBorder = 0.7;
    private JTweet tweet;

    /* for tests */
    public SimilarTweetQuery() {
    }

    public SimilarTweetQuery(JTweet tweet, boolean facets) {
        super(facets);
        this.tweet = tweet;
        if (this.tweet == null)
            throw new IllegalArgumentException("Tweet cannot be null");

        new TermCreateCommand().calcTermsWithoutNoise(tweet);
        getFilterQueries().clear();
        addFilterQuery(ElasticTweetSearch.IS_RT, false);
    }

    public double getMmBorder() {
        return mmBorder;
    }

    /**
     * Set minimal match (percentage) for similar tweet detection when querying
     */
    public SimilarTweetQuery setMmBorder(double mmBorder) {
        this.mmBorder = mmBorder;
        return this;
    }

    public Collection<String> calcTerms() {
        Set<String> res = new LinkedHashSet<String>();
        for (Entry<String, Integer> e : getTerms()) {
            res.add(e.getKey());
        }
        return res;
    }

    Collection<Entry<String, Integer>> getTerms() {
        return tweet.getTextTerms().getSortedTermLimited(8);
    }

    @Override
    protected QueryBuilder createQuery(String queryStr) {
        // use configured stemmer, but querying seems to be slower!
//        BoolQueryBuilder bqb = QueryBuilders.boolQuery().minimumNumberShouldMatch(minMatchNumber);
//        for (Entry<String, Integer> entry : terms) {
//            bqb.should(QueryBuilders.queryString(ElasticTweetSearch.TWEET_TEXT + ":" + Solr2ElasticTweet.escapeQuery(entry.getKey())));
//        }
//
//        qb = bqb;        

        Collection<Entry<String, Integer>> terms = getTerms();
        int minMatchNumber = (int) Math.round(terms.size() * mmBorder);
        // maximal 6 terms
        minMatchNumber = Math.min(6, minMatchNumber);
        // minimal 4 terms
        minMatchNumber = Math.max(4, minMatchNumber);

        // do we need to escape the terms when querying?
        Collection<String> coll = doSnowballTermsStemming(terms);
        return QueryBuilders.termsQuery(ElasticTweetSearch.TWEET_TEXT,
                Helper.toStringArray(coll)).
                minimumMatch(minMatchNumber);
    }
}
