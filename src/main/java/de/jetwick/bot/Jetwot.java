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

import de.jetwick.config.Configuration;
import de.jetwick.tw.Credits;
import de.jetwick.tw.TwitterSearch;

/**
 * Idea: either twitterbot or own UI to show trends!
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class Jetwot {

    public static void main(String[] args) {
        new Jetwot().start();
    }

    public TwitterSearch createJetwot() {
        Configuration cfg = new Configuration();
        Credits credits = cfg.getJetwotCredits();
        
//        AccessToken aToken = new AccessToken(credits.getToken(), credits.getTokenSecret());
//        Twitter tw = new TwitterFactory().getOAuthAuthorizedInstance(
//                credits.getConsumerKey(), credits.getConsumerSecret(), aToken);
//        try {
//            tw.verifyCredentials();
//        } catch (TwitterException ex) {
//            throw new RuntimeException(ex);
//        }

        // the bot can be feeded through @jetwot java => q=java
        // answer of jetwot: @xy thanks! I will try to find trends for 'java'
        // restriction: one user per day = one query
        return new TwitterSearch(credits);
    }

    public void start() {
        TwitterSearch tw = createJetwot();
        while (true) {
            // every 15 minutes check for new trending url. put title + url into cache
            // or even better facet over dt (every 20 minutes) and pick up the docs!
            // f.dest_title_1_s.facet.limit=20
            // from this, calculate trend -> up coming urls (new tweets per hour that link to this url)
            // every 2 hours post a new trending url from cache with the highest up rate + over a certain number of tweets
            // do no overuse ratelimit !
            // twitter.postTweet("'Title ABOUT XY' short.url/test");
        }
    }
}
