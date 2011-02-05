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
package de.jetwick.util;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import de.jetwick.config.Configuration;
import de.jetwick.config.DefaultModule;
import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.rmi.RMIClient;
import de.jetwick.solr.SolrUser;
import de.jetwick.solr.SolrUserSearch;
import de.jetwick.tw.Credits;
import de.jetwick.tw.MyTweetGrabber;
import de.jetwick.tw.TwitterSearch;
import de.jetwick.tw.queue.QueueThread;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class Util {

    private static Logger logger = LoggerFactory.getLogger(Util.class);
    private final String url;
    private ElasticUserSearch userSearch;
    private int userCounter;
    private Configuration config = new Configuration();

    public static void main(String[] args) throws SolrServerException {
        Map<String, String> map = Helper.parseArguments(args);

        String url = map.get("url");
        String cmd = map.get("cmd");

        cmd = "showFollowers";

        System.out.println("cmd=" + cmd);

        Util util = new Util(url);
        if ("deleteAll".equals(cmd)) {
            util.deleteAll();
        } else if ("fillFrom".equals(cmd)) {
            final String fromUrl = map.get("url.from");
            util.fillFrom(fromUrl);
        } else if ("copyStaticTweets".equals(cmd)) {
            util.copyStaticTweets();
        } else if ("copyUsers".equals(cmd)) {
            util.copyUsers();
        } else if ("showFollowers".equals(cmd)) {
            util.showFollowers("jetwick");
        }
    }

    public Util(String url) {
        if (url == null || url.isEmpty())
            url = config.getTweetSearchUrl();

        this.url = url;
    }

    public void deleteAll() {
        userSearch = createUserSearch();
        // why don't we need to set? query.setQueryType("simple")
        userSearch.deleteAll();
        userSearch.refresh();
        logger.info("Successfully finished deleteAll of " + url);
    }

    private void copyStaticTweets() throws SolrServerException {
        Module module = new DefaultModule();
        Injector injector = Guice.createInjector(module);
        Provider<RMIClient> rmiProvider = injector.getProvider(RMIClient.class);
        Configuration cfg = injector.getInstance(Configuration.class);
        TwitterSearch twSearch = injector.getInstance(TwitterSearch.class);
        twSearch.initTwitter4JInstance(cfg.getTwitterSearchCredits().getToken(), cfg.getTwitterSearchCredits().getTokenSecret());
        ElasticTweetSearch fromUserSearch = new ElasticTweetSearch(injector.getInstance(Configuration.class));
        SolrQuery query = new SolrQuery().addFilterQuery(ElasticTweetSearch.UPDATE_DT + ":[* TO *]");
        query.setFacet(true).addFacetField("user").setFacetLimit(2000).setRows(0).setFacetSort("count");
        SearchResponse rsp = fromUserSearch.search(query);

        TermsFacet tf = (TermsFacet) rsp.getFacets().facet("user");
        logger.info("found: " + tf.entries().size() + " users with the specified criteria");
        int SLEEP = 30;
        int counter = 0;
        for (TermsFacet.Entry tmpUser : tf.entries()) {
            if (tmpUser.getCount() < 20)
                break;

            while (twSearch.getRateLimit() <= 3) {
                try {
                    logger.info("sleeping " + SLEEP + " seconds to avoid ratelimit violation");
                    Thread.sleep(1000 * SLEEP);
                } catch (InterruptedException ex) {
                    throw new IllegalStateException(ex);
                }
            }

            logger.info(counter++ + "> feed pipe from " + tmpUser.getTerm() + " with " + tmpUser.getCount() + " tweets");

            MaxBoundSet boundSet = new MaxBoundSet<String>(0, 0);
            // try updating can fail so try max 3 times
            for (int trial = 0; trial < 3; trial++) {
                MyTweetGrabber grabber = new MyTweetGrabber().setMyBoundSet(boundSet).
                        init(null, null, tmpUser.getTerm()).setTweetsCount((int) tmpUser.getCount()).
                        setRmiClient(rmiProvider).setTwitterSearch(twSearch);
                QueueThread pkg = grabber.queueTweetPackage();
                Thread t = new Thread(pkg);
                t.start();
                try {
                    t.join();
                    if (pkg.getException() == null)
                        break;

                    logger.warn(trial + "> Try again feeding of user " + tmpUser.getTerm() + " for tweet package " + pkg);
                } catch (InterruptedException ex) {
                    logger.warn("interrupted", ex);
                    break;
                }
            }
        }

        // TODO send via RMI
    }

    public void fillFrom(final String fromUrl) {
        userSearch = createUserSearch();
        ElasticTweetSearch fromUserSearch = new ElasticTweetSearch(fromUrl, null, null);
        SolrQuery query = new SolrQuery().setQuery("*:*");
        query.setQueryType("simple");
        long maxPage = 1;
        int hitsPerPage = 300;
        Set<SolrUser> users = new LinkedHashSet<SolrUser>();
        Runnable optimizeOnExit = new Runnable() {

            @Override
            public void run() {
                userSearch.refresh();
                logger.info(userCounter + " users pushed to " + url + " from " + fromUrl);
            }
        };
        Runtime.getRuntime().addShutdownHook(new Thread(optimizeOnExit));

        for (int page = 0; page < maxPage; page++) {
            fromUserSearch.attachPagability(query, page, hitsPerPage);
            users.clear();

            SearchResponse rsp;
            try {
                rsp = fromUserSearch.search(users, query);
            } catch (Exception ex) {
                logger.warn("Error while searching!", ex);
                continue;
            }
            if (maxPage == 1) {
                maxPage = rsp.getHits().getTotalHits() / hitsPerPage + 1;
                logger.info("Paging though query:" + query.toString());
                logger.info("Set numFound to " + rsp.getHits().getTotalHits());
            }

            for (SolrUser user : users) {
                userSearch.save(user, false);
            }
            userCounter += users.size();
            logger.info("Page " + page + " out of " + maxPage + " hitsPerPage:" + hitsPerPage);

            if (page * hitsPerPage % 100000 == 0) {
                logger.info("Commit ...");
                userSearch.refresh();
            }
        }
    }

    public void copyUsers() throws SolrServerException {
        String solrUrl = "http://www.pannous.info/uindex";

        System.out.println("create solrusersearch");
        SolrUserSearch solrUserSearch = new SolrUserSearch(solrUrl,
                config.getTweetSearchLogin(), config.getTweetSearchPassword(), false);

        System.out.println("query user index");
        Set<SolrUser> users = new LinkedHashSet<SolrUser>();
        solrUserSearch.search(users, new SolrQuery().setRows(100));

        System.out.println("create usersearch");
        userSearch = createUserSearch();
        System.out.println("count before:" + userSearch.countAll() + " found:" + users);

        userSearch.update(users);
        userSearch.refresh();

        System.out.println("count now:" + userSearch.countAll());
        System.out.println("pannous account? " + userSearch.findByScreenName("pannous"));
    }

    ElasticUserSearch createUserSearch() {
        return new ElasticUserSearch(url, null, null);
    }

    public void showFollowers(String user) throws SolrServerException {
//        ElasticUserSearch uSearch = createUserSearch();
//        Set<SolrUser> jetwickUsers = new LinkedHashSet<SolrUser>();
//        uSearch.search(jetwickUsers, new SolrQuery().setRows(10000));
        final Set<String> set = new TreeSet<String>();
//        for (SolrUser u : jetwickUsers) {
//            set.add(u.getScreenName());
//        }
        Credits credits = config.getTwitterSearchCredits();
        TwitterSearch tw4j = new TwitterSearch().setConsumer(credits.getConsumerKey(), credits.getConsumerSecret());
        tw4j.initTwitter4JInstance(credits.getToken(), credits.getTokenSecret());
        tw4j.getFollowers(user, new AnyExecutor<SolrUser>() {

            @Override
            public SolrUser execute(SolrUser o) {
//                if (set.contains(o.getScreenName()))
                set.add(o.getScreenName());
                return null;
            }
        });
        for (String u : set) {
            System.out.println(u);
        }
    }
}
