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
package de.jetwick.tw;

import de.jetwick.es.ElasticTagSearch;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
import de.jetwick.tw.queue.AbstractTweetPackage;
import de.jetwick.tw.queue.TweetPackage;
import de.jetwick.tw.queue.TweetPackageList;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * fills the tweets queue via twitter searchAndGetUsers (does not cost API calls)
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetProducerOffline extends MyThread implements TweetProducer {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private BlockingQueue<TweetPackage> tweetPackages = new LinkedBlockingDeque<TweetPackage>();
    private int maxFill;
    private Random rand = new Random();

    public TweetProducerOffline() {
        super("fake tweet-producer");
    }

    @Override
    public BlockingQueue<TweetPackage> getQueue() {
        return tweetPackages;
    }

    @Override
    public void run() {
        logger.info("tweet number batch:" + maxFill);
        int counter = 0;
        MAIN:
        while (!isInterrupted()) {
            counter++;
            // do not add more tweets to the pipe if consumer cannot process it
            int count = 0;
            while (true) {
                count = AbstractTweetPackage.calcNumberOfTweets(tweetPackages);
                if (count < maxFill)
                    break;

                logger.info("WAITING! " + count + " are too many tweets from twitter4j searching!");
                if (!myWait(20))
                    break MAIN;
            }

            LinkedBlockingDeque<JTweet> tmp = new LinkedBlockingDeque<JTweet>();
            int TWEETS_PER_USER = 5;
            int USER_PER_PKG = 40;
            for (int userCounter = 0; userCounter < USER_PER_PKG; userCounter++) {
                JUser user = new JUser("user " + userCounter * USER_PER_PKG + counter);

                for (int i = 0; i < TWEETS_PER_USER; i++) {
                    // make id random because otherwise all tweets will be overwritten 
                    // and not added for a new collector start
                    JTweet tw = new JTweet(Math.abs(rand.nextLong()),
                            createRandomWord(3) + " " + createRandomWord(4),
                            user);
                    int retweet = (int) Math.round(Math.abs(rand.nextGaussian() * 10));
                    tw.setRt(retweet);
                    int repliesNoRetweet = (int) Math.round(Math.abs(rand.nextGaussian() * 2));
                    tw.setReply(retweet + repliesNoRetweet);
                    tmp.add(tw);
                }
            }

            tweetPackages.add(new TweetPackageList("fake:" + counter).init(tmp));
        }

        logger.info(getClass().getSimpleName() + " successfully finished");
    }

    String createRandomWord(int chars) {
        String word = "";
        for (int i = 0; i < chars; i++) {
            word = word + (char) (rand.nextInt(58) + 65);
        }
        return word;
    }

    @Override
    public void setMaxFill(int maxFill) {
        this.maxFill = maxFill;
    }

    @Override
    public void setTwitterSearch(TwitterSearch tws) {
        // skip
    }

    @Override
    public void setUserSearch(ElasticUserSearch userSearch) {
        // skip
    }

    @Override
    public void setTagSearch(ElasticTagSearch tagSearch) {
        // skip
    }
}
