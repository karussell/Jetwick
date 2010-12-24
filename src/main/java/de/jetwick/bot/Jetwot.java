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
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrTweetSearch;
import de.jetwick.solr.SolrUser;
import static de.jetwick.solr.SolrTweetSearch.*;
import de.jetwick.tw.Credits;
import de.jetwick.tw.TwitterSearch;
import de.jetwick.tw.cmd.TermCreateCommand;
import de.jetwick.util.Helper;
import de.jetwick.util.MaxBoundSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import org.apache.solr.client.solrj.SolrQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Idea: either twitterbot or own UI to show trends!
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class Jetwot {

    public static void main(String[] args) {
        Map<String, String> params = Helper.parseArguments(args);
        long interval = 1 * 60 * 1000L;
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
    protected SolrTweetSearch tweetSearch;
    protected TwitterSearch tw4j;
    private int minRT = 15;
    private MaxBoundSet<Long> idCache = new MaxBoundSet<Long>(500, 1000);
    // two days
    private MaxBoundSet<String> termCache = new MaxBoundSet<String>(50, 100).setMaxAge(2 * 24 * 3600 * 1000L);

    public void init() {
        Configuration cfg = new Configuration();
        Credits credits = cfg.getJetwotCredits();
        Module module = new DefaultModule();
        Injector injector = Guice.createInjector(module);
        tweetSearch = injector.getInstance(SolrTweetSearch.class);
        tw4j = new TwitterSearch().setConsumer(credits.getConsumerKey(), credits.getConsumerSecret());
        tw4j.setTwitter4JInstance(credits.getToken(), credits.getTokenSecret());

        try {
            for (SolrTweet tw : tw4j.getTweets(tw4j.getUser(), new ArrayList<SolrUser>(), 30)) {
                idCache.add(tw.getTwitterId());
            }
        } catch (Exception ex) {
            logger.error("Couldn't initialize id cache", ex);
        }
    }

    public void start(int cycles, long interval) {
        init();

        TermCreateCommand command = new TermCreateCommand();
        Random rand = new Random();
        for (int i = 0; cycles < 0 || i < cycles; i++) {
            logger.info("id cache:" + idCache.size());
            logger.info("term cache:" + termCache.size());
            Collection<SolrTweet> tweets = search();
            SolrTweet selectedTweet = null;
            for (SolrTweet tw : tweets) {
                command.calcTermsWithoutNoise(tw);
                if (tw.getTextTerms().size() > 4 && !idCache.contains(tw.getTwitterId())) {
                    boolean containsTerm = false;

                    if (termCache.size() > 0)
                        for (String term : tw.getTextTerms().keySet()) {
                            if (termCache.contains(term)) {
                                containsTerm = true;
                                break;
                            }
                        }

                    if (!containsTerm) {
                        selectedTweet = tw;
                        break;
                    }
                }
            }

            if (selectedTweet != null) {
                try {
                    tw4j.doRetweet(selectedTweet.getTwitterId());

                    for (String term : selectedTweet.getTextTerms().keySet()) {
                        termCache.add(term);
                    }
                    idCache.add(selectedTweet.getTwitterId());
                    logger.info("retweeted:" + selectedTweet);
                } catch (Exception ex) {
                    logger.error("Couldn't retweet tweet:" + selectedTweet + " " + ex.getMessage());
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
                logger.info("wait " + (tmp / 1000f) + " sec");
                Thread.sleep(tmp);
            } catch (InterruptedException ex) {
                logger.warn("Interrupted " + ex.getMessage());
                break;
            }
        }
    }

    public Collection<SolrTweet> search() {
        SolrQuery query = new SolrQuery().addFilterQuery(FILTER_ENTRY_LATEST_DT).
                addFilterQuery(QUALITY + ":[90 TO *]").
                addFilterQuery(DUP_COUNT + ":0").
                addFilterQuery(RT_COUNT + ":[" + minRT + " TO *]").
                addFilterQuery(IS_RT + ":false").
                addFilterQuery("lang:en").
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
}
