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
package de.jetwick.bot;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import de.jetwick.config.Configuration;
import de.jetwick.config.DefaultModule;
import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
import static de.jetwick.es.ElasticTweetSearch.*;
import de.jetwick.tw.Credits;
import de.jetwick.tw.TwitterSearch;
import de.jetwick.tw.cmd.TermCreateCommand;
import de.jetwick.util.Helper;
import de.jetwick.util.MaxBoundSet;
import de.jetwick.util.MyDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import org.apache.solr.client.solrj.SolrQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterException;

/**
 * Idea: either twitterbot or own UI to show trends!
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class Jetwot {

    public static void main(String[] args) {
        Map<String, String> params = Helper.parseArguments(args);
        long interval = 10 * 1000L;
        try {
            String str = params.get("interval");
            char unit = str.charAt(str.length() - 1);
            str = str.substring(0, str.length() - 1);
            if (unit == 'h') {
                // in hours
                interval = Long.parseLong(str) * 60 * 60 * 1000L;
            } else if (unit == 'm') {
                // in minutes
                interval = Long.parseLong(str) * 60 * 1000L;
            }
        } catch (Exception ex) {
            logger.warn("Cannot parse interval parameter:" + ex.getMessage());
        }
        int minRT = 15;
        try {
            minRT = Integer.parseInt(params.get("minRT"));
        } catch (Exception ex) {
            logger.warn("Cannot parse interval parameter:" + ex.getMessage());
        }

        new Jetwot().setMinRT(minRT).start(-1, interval);
    }
    private static Logger logger = LoggerFactory.getLogger(Jetwot.class);
    protected ElasticTweetSearch tweetSearch;
    protected TwitterSearch tw4j;
    private int minRT = 25;
    private MaxBoundSet<SolrTweet> tweetCache = new MaxBoundSet<SolrTweet>(50, 100).setMaxAge(3 * 24 * 3600 * 1000L);
    private TermCreateCommand command = new TermCreateCommand();
    private Random rand = new Random();

    public void init() {
        Configuration cfg = new Configuration();
        Credits credits = cfg.getJetwotCredits();
        Module module = new DefaultModule();
        Injector injector = Guice.createInjector(module);
        tweetSearch = injector.getInstance(ElasticTweetSearch.class);
        tw4j = new TwitterSearch().setConsumer(credits.getConsumerKey(), credits.getConsumerSecret());
        tw4j.initTwitter4JInstance(credits.getToken(), credits.getTokenSecret());

        try {
            for (SolrTweet tw : tw4j.getTweets(tw4j.getUser(), new ArrayList<SolrUser>(), 20)) {
                command.calcTermsWithoutNoise(tw);
                addToCaches(tw);
            }
        } catch (Exception ex) {
            logger.error("Couldn't initialize id cache", ex);
        }
    }

    public void start(int cycles, long interval) {
        init();

        for (int i = 0; cycles < 0 || i < cycles; i++) {
            logger.info("tweet cache:" + tweetCache.size());
            Collection<SolrTweet> newSearchedTweets = search();
            SolrTweet selectedTweet = null;

            for (SolrTweet newSearchTw : newSearchedTweets) {
                command.calcTermsWithoutNoise(newSearchTw);
                if (newSearchTw.getTextTerms().size() >= 4) {
                    float maxJc = -1;
                    for (SolrTweet twInCache : tweetCache.values()) {
                        float jcIndex = (float) TermCreateCommand.calcJaccardIndex(twInCache.getTextTerms(), newSearchTw.getTextTerms());
                        if (maxJc < jcIndex)
                            maxJc = jcIndex;
                    }

                    if (maxJc < 0.2 || maxJc == -1) {
                        selectedTweet = newSearchTw;
                        logger.info("new  tweet with    max jacc index= " + maxJc + ":" + newSearchTw.getText());
                        break;
                    }

                    logger.info("skip tweet because max jacc index= " + maxJc + ":" + newSearchTw.getText());
                } else {
                    logger.info("skip tweet because too less terms= " + newSearchTw.getTextTerms().size() + "  :" + newSearchTw.getText());
                }
            }

            if (selectedTweet != null) {
                try {
                    tw4j.doRetweet(selectedTweet.getTwitterId());

                    addToCaches(selectedTweet);
                    logger.info("=> retweeted:" + selectedTweet.getText() + " " + selectedTweet.getTwitterId());
                } catch (Exception ex) {
                    logger.error("Couldn't retweet tweet:" + selectedTweet + " " + ex.getMessage());
                    if (ex instanceof TwitterException) {
                        TwitterException ex2 = ((TwitterException) ex);
                        if (ex2.exceededRateLimitation()) {
                            logger.error("Remaining hits:" + ex2.getRateLimitStatus().getRemainingHits()
                                    + " wait some seconds:" + ex2.getRateLimitStatus().getResetTimeInSeconds());
                        }
                    }
                }
            }

            // Create tweet for Trending URLS?
            // every 15 minutes check for new trending url. put title + url into cache
            // or even better facet over dt (every 20 minutes) and pick up the docs!
            // f.dest_title_1_s.facet.limit=20
            // from this, calculate trend -> up coming urls (new tweets per hour that link to this url)
            // every 2 hours post a new trending url from cache with the highest up rate + over a certain number of tweets
            // do no overuse ratelimit !
            // twitter.postTweet("'Title ABOUT XY' short.url/test");

            try {
                // add some noise when waiting to avoid being identified or filtered out as bot ;-)
                long tmp = (long) (interval + interval * rand.nextDouble() * 0.3);

                logger.info("wait " + (tmp / 60f / 1000f) + " minutes => next tweet on: " + new MyDate().plusMillis(tmp));
                Thread.sleep(tmp);
            } catch (InterruptedException ex) {
                logger.warn("Interrupted " + ex.getMessage());
                break;
            }
        }
    }

    public Collection<SolrTweet> search() {
        SolrQuery query = new SolrQuery(). // should be not too old
                addFilterQuery(DATE + ":[" + new MyDate().minusDays(6).toLocalString() + " TO *]").
                // should be high quality
                addFilterQuery(QUALITY + ":[90 TO *]").
                // should be the first tweet with this content
                addFilterQuery(DUP_COUNT + ":0").
                // only tweets which were already tweeted minRT-times
                addFilterQuery(RT_COUNT + ":[" + minRT + " TO *]").
                // only original tweets
                addFilterQuery(IS_RT + ":false").
                // for english our spam + dup detection works ok
                addFilterQuery("lang:(en OR de OR sp)").
                setSortField(RT_COUNT, SolrQuery.ORDER.desc).
                setRows(50);

        logger.info(query.toString());
        int TRIALS = 2;
        for (int trial = 0; trial < TRIALS; trial++) {
            try {
                return tweetSearch.collectTweets(tweetSearch.search(query));
            } catch (Exception ex) {
                logger.error(trial + "| Couldn't query twindex: " + ex.getMessage());
            }
        }
        return Collections.EMPTY_LIST;
    }

    public Jetwot setMinRT(int minRT) {
        this.minRT = minRT;
        return this;
    }

    protected void addToCaches(SolrTweet selectedTweet) {
        tweetCache.add(selectedTweet);
    }
}
