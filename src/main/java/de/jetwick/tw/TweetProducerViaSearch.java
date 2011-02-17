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

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;
import com.wideplay.warp.persist.WorkManager;
import de.jetwick.data.TagDao;
import de.jetwick.data.YTag;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.solr.SolrTweet;
import de.jetwick.tw.queue.AbstractTweetPackage;
import de.jetwick.tw.queue.TweetPackage;
import de.jetwick.tw.queue.TweetPackageList;
import de.jetwick.util.Helper;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import org.hibernate.StaleObjectStateException;
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
    @Inject
    private TagDao tagDao;
    protected BlockingQueue<TweetPackage> tweetPackages = new LinkedBlockingDeque<TweetPackage>();
    private PriorityQueue<YTag> tags = new PriorityQueue<YTag>();
    protected TwitterSearch twSearch;
    protected ElasticUserSearch userSearch;
    protected int maxFill = 2000;
    
    @Inject
    private WorkManager manager;    
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
        manager.beginWork();
        try {
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

                YTag tag = tags.poll();
                if (tag != null && tag.nextQuery()) {
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

                    float waitInSeconds = 0.1f;
                    try {
                        long maxId = 0;
                        LinkedBlockingDeque<SolrTweet> tmp = new LinkedBlockingDeque<SolrTweet>();
                        maxId = twSearch.search(tag.getTerm(), tmp, tag.getPages() * 100, tag.getLastId());

                        int hits = tmp.size();
                        tag.setLastId(maxId);
                        feededTweets += hits;
                        float tweetsPerSec = feededTweets / ((System.currentTimeMillis() - start) / 1000.0f);
                        logger.info("tweets/sec:" + tweetsPerSec + " \tqueue= " + count + " \t + "
                                + hits + " \t q=" + tag.getTerm() + " pages=" + tag.getPages());

                        tweetPackages.add(new TweetPackageList("search:" + tag.getTerm()).init(MyTweetGrabber.idCounter.addAndGet(1), tmp));

                        if (!tag.isTransient()) {
                            // TODO save only if indexing to solr was successful -> pkg.isIndexed()
                            updateTagInTA(tag, hits);
                        }
                    } catch (TwitterException ex) {
                        waitInSeconds = 1f;
                        logger.warn("Couldn't finish search for tag '" + tag.getTerm() + "': " + ex.getMessage());
                        if (ex.exceededRateLimitation())
                            waitInSeconds = ex.getRetryAfter();
                    } catch (StaleObjectStateException ex) {
                        initTags();
                    }

                    if (!myWait(waitInSeconds))
                        break;
                }
            }
        } finally {
            manager.endWork();
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

    @Transactional
    public void updateTagInTA(YTag tag, int hits) {
        tag.optimizeQueryFrequency(hits);
        tagDao.save(tag);
    }

    Collection<YTag> initTags() {
        Map<String, YTag> tmp = new LinkedHashMap<String, YTag>();
        for (YTag tag : tagDao.findAllSorted()) {
            tmp.put(tag.getTerm(), tag);
        }
        try {
            Collection<String> userQueryTerms = userSearch.getQueryTerms();
            int counter = 0;
            for (String str : userQueryTerms) {
                str = str.toLowerCase();
                YTag tag = tmp.get(str);
                if (tag == null) {
                    tmp.put(str, new YTag(str));
                    counter++;
                }
            }
            logger.info("Will add query terms " + counter + " of " + userQueryTerms);
        } catch (Exception ex) {
            logger.error("Couldn't query user index to feed tweet index with user queries:" + ex.getMessage());
        }

        tags = new PriorityQueue<YTag>(tmp.values());
        logger.info("Using " + tags.size() + " tags. first tag is: " + tags.peek());
        return tags;
    }

    @Override
    public void setUserSearch(ElasticUserSearch userSearch) {
        this.userSearch = userSearch;
    }
}
