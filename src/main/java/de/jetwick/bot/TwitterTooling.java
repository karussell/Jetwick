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
package de.jetwick.bot;

import de.jetwick.config.Configuration;
import de.jetwick.data.JUser;
import de.jetwick.tw.Credits;
import de.jetwick.tw.TwitterSearch;
import de.jetwick.util.AnyExecutor;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class should have been named TwitterFooling
 * 
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class TwitterTooling {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private final Set<String> ignoreUsers = new LinkedHashSet<String>();
    private final Set<String> ignoreUsersDM = new LinkedHashSet<String>();
    private BufferedWriter writer;
    private BufferedWriter writerDM;
    private int MAX_USERS = 30;

    public static void main(String[] args) throws Exception {
        String ignoreFile = "ignore-users.txt";
        new TwitterTooling(ignoreFile).sendAllFollowersDM("Hey USER, we replaced our old account with @JetslideApp http://ow.ly/5JqpJ We would be happy to see you there :)");

//        new TwitterTooling(ignoreFile).autoUnfollow();
//        // not many unknown users: new TwitterTooling(ignoreFile).autoFollow("gotrapit", false);       
//        // not many unknown users: new TwitterTooling(ignoreFile).autoFollow("aepiotblog", false);
//        // not many unknown users: new TwitterTooling(ignoreFile).autoFollow("xydoapp", false);
//        // not many unknown users: new TwitterTooling(ignoreFile).autoFollow("trunklyapp", false);   
//        
//        // we need to search very deep:
////        new TwitterTooling(ignoreFile).autoFollow("twttimes", false);           
//        new TwitterTooling(ignoreFile).autoFollow("evri", false);  
//        new TwitterTooling(ignoreFile).autoFollow("popurls", false);
//        
//        new TwitterTooling(ignoreFile).autoFollow("postposting", false);        
//        new TwitterTooling(ignoreFile).autoFollow("summify", false);        
////        new TwitterTooling(ignoreFile).autoFollow("techcrunch", false);
////        new TwitterTooling(ignoreFile).autoFollow("curatedby", false);        
//        new TwitterTooling(ignoreFile).autoFollow("refynr", false);                
////        new TwitterTooling(ignoreFile).autoFollow("genieoTweets", false);
//        new TwitterTooling(ignoreFile).autoFollow("my6sense", false);   
//        new TwitterTooling(ignoreFile).autoFollow("Curatedby", false);           
    }

    public TwitterTooling(String ignoreUserFile) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(ignoreUserFile));
            String line = null;
            while ((line = reader.readLine()) != null) {
                ignoreUsers.add(line.trim().toLowerCase());
            }
            writer = new BufferedWriter(new FileWriter(ignoreUserFile, true));

            // write for direct message ignore list
            reader = new BufferedReader(new FileReader(ignoreUserFile + ".dm"));
            while ((line = reader.readLine()) != null) {
                ignoreUsersDM.add(line.trim().toLowerCase());
            }
            writerDM = new BufferedWriter(new FileWriter(ignoreUserFile + ".dm", true));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public String getThisUser() {
        return "jetwick";
    }

    public void autoFollow(final String userName, boolean getFriends) {
        TwitterSearch twitterSearch = getTwitterSearch();
        final Set<String> friendCollection = new LinkedHashSet<String>();
        twitterSearch.getFriends(getThisUser(), new AnyExecutor<JUser>() {

            @Override
            public JUser execute(JUser u) {
                friendCollection.add(u.getScreenName().toLowerCase());
                return u;
            }
        });

        final Map<String, JUser> users = new LinkedHashMap<String, JUser>();
        twitterSearch.getFriendsOrFollowers(userName, new AnyExecutor<JUser>() {

            int seekCounter = 0;

            @Override
            public JUser execute(JUser user) {
                float factor = ((float) user.getFollowersCount() / user.getFriendsCount());
                // if factor is too low => probably spammy or something
                // if factor is too high=> probably won't follow back ;)

                if (user.getFollowersCount() > 50 && factor > 0.8 && factor < 6) {
                    String lower = user.getScreenName().toLowerCase();
                    if (friendCollection.contains(lower))
                        logger.info("Already following:" + lower);
                    else if (ignoreUsers.contains(lower))
                        logger.info("In ignore list:" + lower);
                    else {
                        users.put(lower, user);
                        if (users.size() >= MAX_USERS)
                            return null;
                        else if (users.size() % 100 == 0)
                            logger.info("Grabbed " + users.size() + " users ...");
                    }
                }

                if (++seekCounter % 50 == 0)
                    logger.info("Still seeking for followers. User: " + userName
                            + " Counter:" + seekCounter + " collected users:" + users.size());

                return user;
            }
        }, getFriends);

        String friendStr = "friends";
        if (!getFriends)
            friendStr = "followers";

        System.out.println("userName:" + userName + " has " + users.size() + " "
                + friendStr + " with a 'high enough score' not too spammy or too hip");

        int counter = 0;
        for (JUser user : users.values()) {
            try {
                System.out.println(++counter + " " + user.getScreenName() + " follower:"
                        + user.getFollowersCount() + " foll/friend=" + ((float) user.getFollowersCount() / user.getFriendsCount()));
                twitterSearch.follow(user);

                // now write to ignore list so that when auto or manually 
                // unfollowing them, we still know the visited people
                writer.write(user.getScreenName().toLowerCase().trim() + "\n");
                writer.flush();
            } catch (Exception ex) {
                logger.error("cannot follow: " + user.getScreenName() + " " + TwitterSearch.getMessage(ex));
                continue;
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException ex) {
                logger.error("cannot wait!", ex);
                break;
            }
        }
    }

    private void autoUnfollow() throws Exception {
        TwitterSearch twitterSearch = getTwitterSearch();
        Collection<JUser> users = twitterSearch.getFriendsNotFollowing(getThisUser());
        System.out.println("api: " + twitterSearch.getUser() + " friends not following:" + users.size());
        int counter = users.size();
        for (JUser user : users) {
            for (int i = 0; i < 3; i++) {
                try {
                    Thread.sleep(1000);
                    twitterSearch.unfollow(user.getScreenName());
                    logger.info("unfollowed:" + user.getScreenName() + " remaining:" + --counter);
                    break;
                } catch (Exception ex) {
                    logger.error("Cannot unfollow:" + user + " " + TwitterSearch.getMessage(ex));
                    continue;
                }
            }
        }
    }

    private TwitterSearch getTwitterSearch() {
        // reminder to me: look in local .jetwick/config.properties to avoid
        // grabbing token + tokenSecure again
        TwitterSearch twitterSearch = new TwitterSearch();
        Credits credits = new Configuration().getTwitterSearchCredits();
        twitterSearch.setConsumer(credits.getConsumerKey(), credits.getConsumerSecret());        
        // read into https://dev.twitter.com/apps/1041604 to get access token + secret
//        try {
//            RequestToken rt = twitterSearch.doDesktopLogin();
//            String pin = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();
//            System.out.println("read PIN:" + pin);
//            AccessToken accessToken = twitterSearch.getToken4Desktop(rt, pin);
//            System.out.println(accessToken.getToken() + " " + accessToken.getTokenSecret());
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }

        twitterSearch.initTwitter4JInstance(credits.getToken(), credits.getTokenSecret(), true);
        if (twitterSearch.getRateLimit() < 10)
            logger.info("minutes until reset:" + twitterSearch.getSecondsUntilReset() / 60f);

        try {
            JUser u = twitterSearch.getUser();
            logger.info("followers:" + u.getFollowersCount() + " following:" + u.getFriendsCount());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return twitterSearch;
    }

    public void sendAllFollowersDM(final String string) {
        final TwitterSearch twitterSearch = getTwitterSearch();
        twitterSearch.getFollowers("jetwick", new AnyExecutor<JUser>() {

            int counter = 0;

            @Override
            public JUser execute(JUser user) {
                try {
                    String lower = user.getScreenName().toLowerCase().trim();
                    if (ignoreUsersDM.contains(lower)) {
                        logger.info("In ignore list:" + lower);
                        return user;
                    }

                    counter++;
                    String msg = string.replaceAll("USER", user.getRealName().trim());
                    System.out.println(counter + " " + user.getScreenName() + " " + msg);
                    twitterSearch.sendDMTo(lower, msg);
                    writerDM.write(lower + "\n");
                    writerDM.flush();
                    Thread.sleep(500);
                } catch (Exception ex) {
                    logger.info("Exception:" + TwitterSearch.getMessage(ex));
                }
                return user;
            }
        });
    }
}
