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

import de.jetwick.es.ElasticUserSearch;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
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
//        for (int i = 0; i < 256; i++) {
//            System.out.println(i + "" + (char) i);
//        }
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

                logger.info("... WAITING! " + count + " are too many tweets from twitter4j searching!");
                if (!myWait(20))
                    break MAIN;
            }

            LinkedBlockingDeque<SolrTweet> tmp = new LinkedBlockingDeque<SolrTweet>();
            SolrUser user = new SolrUser("user " + counter);
            int PER_PKG = 200;
            for (int i = 0; i < PER_PKG; i++) {
                // make id random because otherwise all tweets will be overwritten 
                // and not added for a new collector start
                SolrTweet tw = new SolrTweet(Math.abs(rand.nextLong()), 
                        counter * PER_PKG + i + " test " + createRandomWord(3) + " " + createRandomWord(4), 
                        user);
                int retweet = (int) Math.round(Math.abs(rand.nextGaussian() * 10));
                tw.setRt(retweet);
                int repliesNoRetweet = (int) Math.round(Math.abs(rand.nextGaussian() * 2));
                tw.setReply(retweet + repliesNoRetweet);
                tmp.add(tw);
            }

            tweetPackages.add(new TweetPackageList("fake:" + counter).init(MyTweetGrabber.idCounter.addAndGet(1), tmp));
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
}
