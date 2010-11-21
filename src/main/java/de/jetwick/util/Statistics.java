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
import com.google.inject.Module;
import com.google.inject.Provider;
import com.wideplay.warp.persist.WorkManager;
import de.jetwick.config.Configuration;
import de.jetwick.config.DefaultModule;
import de.jetwick.data.AdEntry;
import de.jetwick.data.TagDao;
import de.jetwick.data.UserDao;
import de.jetwick.data.YTag;
import de.jetwick.data.YUser;
import de.jetwick.hib.HibernateUtil;
import de.jetwick.solr.SolrAdSearch;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrTweetSearch;
import de.jetwick.solr.SolrUser;
import de.jetwick.solr.TweetQuery;
import de.jetwick.tw.StringCleaner;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Execute eg. via
 * ./myjava  de.jetwick.util.Statistics exportNoiseWords=solr/conf/stopwords.txt
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class Statistics {

    private static Logger logger = LoggerFactory.getLogger(Statistics.class);

    public static void main(String[] args) throws Exception {
        Map<String, String> map = Helper.parseArguments(args);
        logger.info("arguments:" + map);
        if (args.length == 0)
            map.put("print", "timetabling");

        new Statistics().start(map);
    }

    @Inject
    private SolrTweetSearch tweetSearch;
    @Inject
    private SolrAdSearch adSearch;
    @Inject
    private TagDao tagDao;
    @Inject
    private UserDao userDao;
    @Inject
    private Provider<WorkManager> wmProvider;
    @Inject
    private Configuration config;

    public Statistics() {
        Module module = new DefaultModule();
        Guice.createInjector(module).injectMembers(this);
    }

    public void start(Map<String, String> map) throws Exception {
        // now process config for stuff which does NOT require the DB
        String argStr = map.get("createSchema");
        if (argStr != null) {
            if ("liquibase".equals(argStr)) {
                HibernateUtil.recreateSchemaFromLiquibase(map.get("file"));
            } else
                HibernateUtil.recreateSchemaFromMapping();

            return;
        }

        argStr = map.get("importAds");
        if (argStr != null) {
            Collection<AdEntry> ads = adSearch.importFromFile(argStr);
            logger.info("added ads:" + ads);
            return;
        }

        argStr = map.get("listTweets");
        if (argStr != null) {
            if ("true".equals(argStr))
                argStr = "**:*";

            List<SolrUser> list = new ArrayList<SolrUser>();
            long ret = tweetSearch.search(list, new TweetQuery(argStr)).
                    getResults().getNumFound();
            logger.info("Found: " + ret + " users. Returned: " + list.size());
            print(list);
            return;
        }

        // specify file via exportNoiseWords=stopwords.txt
        argStr = map.get("exportNoiseWords");
        if (argStr != null) {
            write(new TreeSet<String>(SolrTweet.NOISE_WORDS.keySet()), argStr);
            return;
        }

        WorkManager workManager = wmProvider.get();
        workManager.beginWork();
        try {
            argStr = map.get("getInfo");
//            if (argStr != null) {
//                List<YUser> users = new ArrayList<YUser>();
//                // get tweets for user
//                for (String userName : argStr.split(",")) {
//                    userName = userName.trim().toLowerCase();
//                    users.add(new YUser(userName));
//                    List<? extends Tweet> tweets = twitSearch.getTweets(userName);
//                    int counter = 0;
//                    for (Tweet tw : tweets) {
//                        counter++;
//                        if (counter > 10)
//                            break;
//
//                        System.out.println(tw);
//                        System.out.println(" lang:" + tw.getIsoLanguageCode()
//                                + " geo:" + tw.getGeoLocation() + " " + tw.getLocation()
//                                + "\n");
//                    }
//                }
//
//                twitSearch.updateUserInfo(users);
//                for (YUser u : users) {
//                    System.out.println(u.getLang() + " " + u.getLocation());
//                }
//            }

            argStr = map.get("deleteEmptyUsers");
            if (argStr != null) {
                System.out.println("try to delete " + userDao.countEmptyUsers() + " empty users!");
                Set<String> removeUserList = new LinkedHashSet<String>();
                for (YUser u : userDao.findEmptyUsers()) {
                    removeUserList.add(u.getScreenName());
                    userDao.delete(u);
                }
            }

            argStr = map.get("deleteUsers");
            if (argStr != null) {
                Set<String> removeUserList;
                if (argStr.equals("true")) {
                    logger.info("Now deleting from user black list!");
                    StringCleaner cleaner = new StringCleaner(config.getUserBlacklist());
                    removeUserList = cleaner.getBlacklist();
                } else
                    removeUserList = new LinkedHashSet<String>(Arrays.asList(argStr.split(",")));

                tweetSearch.deleteUsers(removeUserList);
                tweetSearch.commit();
                logger.info("RESTART tweet collector! Deleted: " + removeUserList);
            }

            argStr = map.get("printUsersFromDB");
            if (argStr != null) {
                for (String userStr : argStr.split(",")) {
                    YUser u = userDao.findByName(userStr.toLowerCase());
                    System.out.println("\n" + u.getScreenName() + "\n");
                }
            }

            argStr = map.get("print");
            if (argStr != null)
                statistics(argStr, userDao, tagDao);

            argStr = map.get("deleteTag");
            if (argStr != null) {
                List<String> list = Arrays.asList(argStr.split(" "));
                logger.info("Delete: " + list);
                for (String str : list) {
                    tagDao.deleteByName(str);
                }
            }

            argStr = map.get("exportTags");
            if (argStr != null)
                exportTags(map.get("tagFile"));

            argStr = map.get("readStopAndClear");
            if (argStr != null)
                readStopwords(SolrTweet.class.getResourceAsStream("noise_words_sp.txt"));

            argStr = map.get("importTags");
            if (argStr != null)
                importTags(map.get("tagFile"));

//            argStr = map.get("getUserInfo");
//            if (argStr != null)
//                getUserInfo(argStr);
        } finally {
            workManager.endWork();
        }
    }

    private void sout(Object o) {
        System.out.println(o);
    }

    private void sout1(Object o) {
        System.out.print(o);
    }

    public void statistics(String user, UserDao userDao, TagDao tagDao) {
        YUser oneUser = userDao.findByName(user);

        if (oneUser != null) {
            sout(user + ":");
//            sout("  user.ownTweets:" + oneUser.getOwnTweets().size());
//            sout("  tweetsOf:" + userDao.getTweetsOf(oneUser.getScreenName()).size());
//            sout("");
        }
        StringBuilder sb = new StringBuilder();
        tweetSearch.getInfo(sb);
        sout(sb);
        sout("\n====== DB ======\n");
        sb = new StringBuilder();
        userDao.getInfo(sb);
        sout(sb);
        tagDao.getInfo(sb);
        sout("\n===== Tags =====\n");

        for (YTag t : tagDao.findAll()) {
            sout1(t.getTerm());
            sout1("\t");
            sout1(t.getSearchCounter());
            sout1("\t");
            sout1(t.getLastId());
            sout1("\t");
            sout1(t.getQueryInterval());
            sout("\n");
        }
    }

    public void importTags(String file) throws IOException {
        Set<String> newTags = new TreeSet<String>();
        for (String str : Helper.readFile(file)) {
            if (str.trim().length() > 1)
                newTags.add(str.trim());
        } // do only delete those where we don't have a new one
        // do only update tags which are new
        for (YTag tag : tagDao.findAll()) {
            if (!newTags.contains(tag.getTerm()))
                tagDao.deleteByName(tag.getTerm());
            else
                newTags.remove(tag.getTerm());
        }

        tagDao.addAll(newTags);
    }

    public void exportTags(String file) throws IOException {
        BufferedWriter writer = Helper.createBuffWriter(new File(file));
        for (YTag tag : tagDao.findAll()) {
            writer.append(tag.getTerm());
            writer.append("\n");
        }
        writer.close();
    }

    public void print(List list) {
        for (Object o : list) {
            System.out.println(o);
        }
    }

    public void write(Set<String> words, String file) throws Exception {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), Helper.UTF8));
        writer.write("# Written from YTweet via Statistics class! " + new Date());
        for (String str : words) {
            writer.write(str);
            writer.write("\n");
        }
        writer.close();
    }

//    public void getUserInfo(String argStr) {
//        try {
//            final BufferedWriter writer = Helper.createBuffWriter(new File("out.txt"));
//            logger.info("Start retrieving followers from user '" + argStr + "'");
//            twitSearch.getFollowers(argStr, new AnyExecutor<YUser>() {
//
//                int counter = 0;
//
//                @Override
//                public YUser execute(YUser user) {
//                    try {
//                        logger.info(counter++ + " " + user.toString());
//                        writer.write(user.getScreenName() + "\t" + user.getRealName() + "\t" + user.getLocation() + "\n");
//                        // make sure that we save it (e.g. if exception occured)
//                        writer.flush();
//                        return user;
//                    } catch (Exception ex) {
//                        throw new RuntimeException(ex);
//                    }
//                }
//            });
//            writer.close();
//        } catch (Exception ex) {
//            throw new RuntimeException(ex);
//        }
//    }
    public void readStopwords(InputStream is) throws Exception {
        List<String> list = Helper.readFile(Helper.createBuffReader(is));
        Set<String> set = new TreeSet<String>();
        for (String str : list) {
            if (str.isEmpty() || str.startsWith("//"))
                continue;

            str = str.toLowerCase();
            if (str.contains(" "))
                for (String tmp : str.split(" ")) {
                    set.add(tmp.trim());
                }
            else
                set.add(str.trim());
        }

        for (String str : set) {
            System.out.println(str);
        }
    }
}
