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

import de.jetwick.data.JTag;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.data.JTweet;
import de.jetwick.es.ElasticTagSearch;
import de.jetwick.tw.queue.AbstractTweetPackage;
import de.jetwick.tw.queue.TweetPackage;
import de.jetwick.tw.queue.TweetPackageList;
import de.jetwick.util.Helper;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterException;

/**
 * fills the tweets queue via twitter searchAndGetUsers (does not cost API calls)
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetProducerViaSearch extends MyThread implements TweetProducer {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected BlockingQueue<TweetPackage> tweetPackages = new LinkedBlockingDeque<TweetPackage>();
    private PriorityQueue<JTag> tags = new PriorityQueue<JTag>();
    protected TwitterSearch twSearch;
    protected ElasticTagSearch tagSearch;
    protected ElasticUserSearch userSearch;
    protected int maxFill = 2000;
    private long feededTweets = 0;
    private long start = System.currentTimeMillis();

    public TweetProducerViaSearch() {
        super("tweet-producer");
    }

    @Override
    public BlockingQueue<TweetPackage> getQueue() {
        return tweetPackages;
    }

    public void setQueue(BlockingQueue<TweetPackage> packages) {
        this.tweetPackages = packages;
    }

    @Override
    public void run() {
        logger.info("tweet number batch:" + maxFill);

        // to resolve even urls which requires cookies
        Helper.enableCookieMgmt();
        // to get the same title as a normal browser -> see Helper resolve method
        Helper.enableUserAgentOverwrite();

        long findNewTagsTime = -1;
        MAIN:
        while (!isInterrupted()) {
            if (tags.isEmpty()) {
                initTags();
                if (tags.size() == 0) {
                    logger.warn("No tags found in db! Exit");
                    break;
                }

                if (findNewTagsTime > 0 && System.currentTimeMillis() - findNewTagsTime < 2000) {
                    // wait 2 to 60 seconds. depends on the demand
                    int sec = Math.max(2, (int) tags.peek().getWaitingSeconds() + 1);
                    logger.info("all tags are pausing. wait " + sec + " seconds ");
                    myWait(sec);
                }

                findNewTagsTime = System.currentTimeMillis();
            }

            JTag tag = tags.poll();
            if (tag != null && tag.nextQuery()) {
                // do not add more tweets to the pipe if consumer cannot process it
                Integer count = tooManyTweetsWait(tweetPackages, maxFill, "twitter4j searching", 20, true);
                if(count == null)
                    break MAIN;                

                float waitInSeconds = 2f;
                try {                    
                    int pages = tag.getPages();
                    LinkedBlockingDeque<JTweet> tmp = new LinkedBlockingDeque<JTweet>();
                    long newLastMillis = twSearch.search(tag.getTerm(), tmp, pages * 100, tag.getMaxCreateTime());                    
                    tag.setMaxCreateTime(newLastMillis);
                    int hits = tmp.size();
                    feededTweets += hits;
                    float tweetsPerSec = feededTweets / ((System.currentTimeMillis() - start) / 1000.0f);
                    logger.info("tweets/sec:" + tweetsPerSec + " \tqueue= " + count + " \t + "
                            + hits + " \t q=" + tag.getTerm() + " pages=" + pages + " lastMillis=" + new Date(newLastMillis));

                    tweetPackages.add(new TweetPackageList("search:" + tag.getTerm()).init(tmp));

                    // TODO save only if indexing to solr was successful -> pkg.isIndexed()
                    updateTag(tag, hits);
                } catch (TwitterException ex) {
                    waitInSeconds = 1f;
                    logger.warn("Couldn't finish search for tag '" + tag.getTerm() + "': " + ex.getMessage());
                    if (ex.exceededRateLimitation())
                        waitInSeconds = ex.getRetryAfter();
                }

                if (!myWait(waitInSeconds))
                    break;
            }
        }
        logger.info(getName() + " successfully finished");
    }

    @Override
    public void setMaxFill(int maxFill) {
        this.maxFill = maxFill;
    }

    @Override
    public void setTwitterSearch(TwitterSearch tws) {
        this.twSearch = tws;
    }

    public void updateTag(JTag tag, int hits) {
        tag.optimizeQueryFrequency(hits);
        tagSearch.store(tag);
    }

    Collection<JTag> initTags() {
        Map<String, JTag> tmp = new LinkedHashMap<String, JTag>();
        try {
            for (JTag tag : tagSearch.findSorted(0, 1000)) {
                tmp.put(tag.getTerm(), tag);
            }
        } catch (Exception ex) {
            logger.info("Couldn't query tag index", ex);
        }
        try {
            Collection<String> userQueryTerms = userSearch.getQueryTerms();
            int counter = 0;
            for (String str : userQueryTerms) {
                JTag tag = tmp.get(str);
                if (tag == null) {
                    tmp.put(str, new JTag(str));
                    counter++;
                }
            }
            logger.info("Will add query terms " + counter + " of " + userQueryTerms);
        } catch (Exception ex) {
            logger.error("Couldn't query user index to feed tweet index with user queries:" + ex.getMessage());
        }

        tags = new PriorityQueue<JTag>(tmp.values());
        logger.info("Using " + tags.size() + " tags. first tag is: " + tags.peek());
        return tags;
    }

    @Override
    public void setUserSearch(ElasticUserSearch userSearch) {
        this.userSearch = userSearch;
    }

    @Override
    public void setTagSearch(ElasticTagSearch tagSearch) {
        this.tagSearch = tagSearch;
    }
}
