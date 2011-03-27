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
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import de.jetwick.config.Configuration;
import de.jetwick.config.DefaultModule;
import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.rmi.RMIClient;
import de.jetwick.es.JetwickQuery;
import de.jetwick.data.JUser;
import de.jetwick.es.CreateObjectsInterface;
import de.jetwick.es.TweetQuery;
import de.jetwick.tw.Credits;
import de.jetwick.tw.MyTweetGrabber;
import de.jetwick.tw.TwitterSearch;
import de.jetwick.tw.queue.QueueThread;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.xcontent.ExistsFilterBuilder;
import org.elasticsearch.index.query.xcontent.FilterBuilders;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class Util {

    private static Logger logger = LoggerFactory.getLogger(Util.class);
    @Inject
    private ElasticUserSearch userSearch;
    @Inject
    private ElasticTweetSearch tweetSearch;
    private int userCounter;
    private Configuration config = new Configuration();

    public static void main(String[] args) {
        Map<String, String> map = Helper.parseArguments(args);

        Util util = new Util();
        String argStr = "";
        if (!Helper.isEmpty(map.get("deleteAll"))) {
            util.deleteAll();
            return;
        }

        argStr = map.get("fillFrom");
        if (!Helper.isEmpty(argStr)) {
            String fromUrl = argStr;
            util.fillFrom(fromUrl);
            return;
        }

        argStr = map.get("clearUserTokens");
        if (!Helper.isEmpty(argStr)) {
            String newUserIndexName = argStr;
            util.clearUserTokens(newUserIndexName);
            return;
        }

        if (!Helper.isEmpty(map.get("copyStaticTweets"))) {
            util.copyStaticTweets();
            return;
        }

        argStr = map.get("showFollowers");
        if (!Helper.isEmpty(argStr)) {
            String user = argStr;
            util.showFollowers(user);
            return;
        }

        if (!Helper.isEmpty(map.get("optimize"))) {
            util.optimize();
            return;
        }

        // copyUserIndex=newtwindex
        argStr = map.get("copyUserIndex");
        if (!Helper.isEmpty(argStr)) {
            String newIndex = argStr;
            util.copyUserIndex(newIndex);
            return;
        }

        // copyUserIndex=newtwindex
        argStr = map.get("copyTweetIndex");
        if (!Helper.isEmpty(argStr)) {
            String newIndex = argStr;
            util.copyTweetIndex(newIndex);
            return;
        }

        // call after copyIndex:
        // removeOldTweetIndexAndAlias=newtwindex
        argStr = map.get("removeIndexAndAddAlias");
        if (!Helper.isEmpty(argStr)) {
            String oldIndex = argStr.split(",")[0];
            String newIndex = argStr.split(",")[1];
            util.removeIndexAndAddAlias(oldIndex, newIndex);
            return;
        }
    }

    public Util() {
        Module module = new DefaultModule();
        Guice.createInjector(module).injectMembers(this);
    }

    public void deleteAll() {
        // why don't we need to set? query.setQueryType("simple")
        userSearch.deleteAll();
        userSearch.refresh();
        logger.info("Successfully finished deleteAll");
    }

    private void copyStaticTweets() {
        Module module = new DefaultModule();
        Injector injector = Guice.createInjector(module);
        Provider<RMIClient> rmiProvider = injector.getProvider(RMIClient.class);
        Configuration cfg = injector.getInstance(Configuration.class);
        TwitterSearch twSearch = injector.getInstance(TwitterSearch.class);
        twSearch.initTwitter4JInstance(cfg.getTwitterSearchCredits().getToken(), cfg.getTwitterSearchCredits().getTokenSecret());
        ElasticTweetSearch fromUserSearch = new ElasticTweetSearch(injector.getInstance(Configuration.class));
        JetwickQuery query = new TweetQuery().addFilterQuery(ElasticTweetSearch.UPDATE_DT, "[* TO *]");
        // TODO set facetlimit to 2000
        query.addFacetField("user").setSize(0);
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
        ElasticTweetSearch fromTweetSearch = new ElasticTweetSearch(fromUrl, null, null);
        JetwickQuery query = new TweetQuery();
        long maxPage = 1;
        int hitsPerPage = 300;
        Set<JUser> users = new LinkedHashSet<JUser>();
        Runnable optimizeOnExit = new Runnable() {

            @Override
            public void run() {
                userSearch.refresh();
                logger.info(userCounter + " users pushed to default tweet search from " + fromUrl);
            }
        };
        Runtime.getRuntime().addShutdownHook(new Thread(optimizeOnExit));

        for (int page = 0; page < maxPage; page++) {
            query.attachPagability(page, hitsPerPage);
            users.clear();

            SearchResponse rsp;
            try {
                rsp = fromTweetSearch.search(users, query);
            } catch (Exception ex) {
                logger.warn("Error while searching!", ex);
                continue;
            }
            if (maxPage == 1) {
                maxPage = rsp.getHits().getTotalHits() / hitsPerPage + 1;
                logger.info("Paging though query:" + query.toString());
                logger.info("Set numFound to " + rsp.getHits().getTotalHits());
            }

            for (JUser user : users) {
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

    public void showFollowers(String user) {
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
        tw4j.getFollowers(user, new AnyExecutor<JUser>() {

            @Override
            public JUser execute(JUser o) {
//                if (set.contains(o.getScreenName()))
                set.add(o.getScreenName());
                return null;
            }
        });
        for (String u : set) {
            System.out.println(u);
        }
    }

    public void optimize() {
        tweetSearch.optimize();
    }

    public void copyTweetIndex(String newIndex) {
        try {
            logger.info("Old index has totalhits:" + tweetSearch.countAll());
            if (!tweetSearch.indexExists(newIndex)) {
                logger.info("New Index '" + newIndex + "' does not exist! create it before copy!");
                return;
            }

            ExistsFilterBuilder filter = FilterBuilders.existsFilter(ElasticTweetSearch.UPDATE_DT);
            logger.info("Now copy from " + tweetSearch.getIndexName() + " to " + newIndex + " with exist filter: " + filter);
            tweetSearch.mergeIndices(Arrays.asList(tweetSearch.getIndexName()), newIndex,
                    10000, true, tweetSearch, filter);

            tweetSearch.setIndexName(newIndex);
            logger.info("New index has totalhits:" + tweetSearch.countAll() + " Now optimize ...");
            tweetSearch.optimize();
        } catch (Exception ex) {
            logger.error("Exception while copyIndex", ex);
        }
    }

    public void copyUserIndex(String newIndex) {
        try {
            logger.info("Old index has totalhits:" + userSearch.countAll());
            if (!userSearch.indexExists(newIndex)) {
                logger.info("New Index '" + newIndex + "' does not exist! create it before copy!");
                return;
            }

            logger.info("Now copy from " + userSearch.getIndexName() + " to " + newIndex);
            userSearch.mergeIndices(Arrays.asList(userSearch.getIndexName()), newIndex,
                    10000, true, userSearch, null);

            userSearch.setIndexName(newIndex);
            logger.info("New index has totalhits:" + userSearch.countAll() + " Now optimize ...");
            userSearch.optimize();
        } catch (Exception ex) {
            logger.error("Exception while copyIndex", ex);
        }
    }

    public void removeIndexAndAddAlias(String oldIndex, String newIndex) {
        tweetSearch.deleteIndex(oldIndex);
        tweetSearch.addIndexAlias(newIndex, oldIndex);

        logger.info("added alias:" + newIndex + " for deleted:" + oldIndex);
    }

    public void clearUserTokens(String newIndex) {
        try {
            logger.info("Old index has totalhits:" + userSearch.countAll());
            if (!userSearch.indexExists(newIndex)) {
                logger.info("New Index '" + newIndex + "' does not exist! create it before copy!");
                return;
            }

            logger.info("Now copy from " + userSearch.getIndexName() + " to " + newIndex);
            userSearch.mergeIndices(Arrays.asList(userSearch.getIndexName()), newIndex, 10000, true,
                    new CreateObjectsInterface<JUser>() {

                        @Override
                        public List<JUser> collectObjects(SearchResponse rsp) {
                            List<JUser> users = userSearch.collectObjects(rsp);
                            for (JUser u : users) {
                                u.setTwitterToken(null);
                                u.setTwitterTokenSecret(null);
                            }
                            return users;
                        }
                    }, null);

            userSearch.setIndexName(newIndex);
            logger.info("New index has totalhits:" + userSearch.countAll() + " Now optimize ...");
            userSearch.optimize();
        } catch (Exception ex) {
            logger.error("Exception while copyIndex", ex);
        }
    }
}
