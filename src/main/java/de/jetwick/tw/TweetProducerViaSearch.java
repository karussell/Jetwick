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
import de.jetwick.data.JUser;
import de.jetwick.es.ElasticTagSearch;
import de.jetwick.es.JetwickQuery;
import de.jetwick.util.AnyExecutor;
import de.jetwick.util.Helper;
import de.jetwick.util.MyDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
    protected BlockingQueue<JTweet> resultTweets = new LinkedBlockingQueue<JTweet>();
    private PriorityQueue<JTag> tags = new PriorityQueue<JTag>();
    protected TwitterSearch twSearch;
    protected ElasticTagSearch tagSearch;
    protected ElasticUserSearch userSearch;

    public TweetProducerViaSearch() {
        super("tweet-producer-search");
    }

    @Override
    public void setQueue(BlockingQueue<JTweet> packages) {
        this.resultTweets = packages;
    }

    public BlockingQueue<JTweet> getQueue() {
        return resultTweets;
    }

    @Override
    public void run() {
        long findNewTagsTime = -1;
        Collection<JTweet> tmpColl = new ArrayList<JTweet>(500);
        while (!isInterrupted()) {
            if (tags.isEmpty()) {
                initTags();
                if (tags.isEmpty()) {
                    logger.warn("No tags found in db! Either add some via script ./utils/es-import-tags.sh "
                            + "or track a keyword with rss button when logged in");
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
            long lastMillis = tag.getLastMillis();
            if (tag != null && tag.nextQuery()) {
                String term = tag.getTerm();
                if (term == null) {
                    // TODO use user search later on
                    logger.warn("TODO skipping tags with empty terms for now:" + tag);
                    continue;
                }

                if (term.isEmpty() || JetwickQuery.containsForbiddenChars(term))
                    continue;

                float waitInSeconds = 1f;
                try {
                    int pages = tag.getPages();
                    tmpColl.clear();
                    long newMaxCreateTime = twSearch.search(term + " " + TwitterSearch.LINK_FILTER, tmpColl, pages * 100, 0);

                    // calc tweets per sec with 'floating mean'
                    double lastTweetsPerSec = tag.getTweetsPerSec();
                    int newTweets = guessNewTweets(tmpColl, tag.getMaxCreateTime());
                    lastTweetsPerSec = lastTweetsPerSec + newTweets / ((System.currentTimeMillis() - lastMillis) / 1000.0);
                    tag.setTweetsPerSec(lastTweetsPerSec / 2);
                    tag.setMaxCreateTime(newMaxCreateTime);
                    logger.info("searched: " + tag + "\t=> tweets:" + tmpColl.size() + "\t newTweets:" + newTweets);
                    for (JTweet tw : tmpColl) {
                        try {
                            resultTweets.put(tw.setFeedSource("search:" + term));
                        } catch (InterruptedException ex) {
                            logger.error("Cannot put article into queue:" + tw + " " + ex.getMessage());
                            break;
                        }
                    }
//                    resultTweets.add(new JTweet(123, "something http://t.co/BVDTqCO", new JUser("timetabling")));

                    updateTag(tag, tmpColl.size());
                } catch (TwitterException ex) {
                    waitInSeconds = 3f;
                    logger.warn("Couldn't finish search for tag '" + term + "': " + Helper.getMsg(ex));
                    if (ex.exceededRateLimitation())
                        waitInSeconds = ex.getRetryAfter();
                }

                if (!myWait(waitInSeconds))
                    break;
            }
        }
        logger.info(getName() + " finished");
    }

    @Override
    public void setTwitterSearch(TwitterSearch tws) {
        this.twSearch = tws;
    }

    public void updateTag(JTag tag, int hits) {
        tag.optimizeQueryFrequency(hits);
        tagSearch.queueObject(tag);
    }
    private long lastDelete = -1;
    private int hours = 3;

    Collection<JTag> initTags() {
        Map<String, JTag> tmpTags = new LinkedHashMap<String, JTag>();
        try {
            for (JTag tag : tagSearch.findSorted(0, 1000)) {
                tmpTags.put(tag.getTerm(), tag);
            }
            long start = System.currentTimeMillis();
            if (lastDelete < 0 || start > lastDelete + hours * MyDate.ONE_HOUR) {
                logger.info("Delete tags older than " + hours + " hours");
                tagSearch.deleteOlderThan(hours);
                lastDelete = start;
                tagSearch.refresh();
            }
        } catch (Exception ex) {
            logger.info("Couldn't query tag index", ex);
        }
        try {
            final Collection<String> userQueryTerms = userSearch.getQueryTerms();
            // TODO execute in separate thread but separate tags by 'OR'
            userSearch.executeForAll(new AnyExecutor<JUser>() {

                @Override
                public JUser execute(JUser u) {
                    userQueryTerms.addAll(u.getTopics());
                    return u;
                }
            }, 1000);
            int counter = 0;
            for (String termAsStr : userQueryTerms) {
                termAsStr = JTag.toLowerCaseOnlyOnTerms(termAsStr).trim();
                if (Helper.isEmpty(termAsStr) || JetwickQuery.containsForbiddenChars(termAsStr))
                    continue;

                for (String tmpTerm : termAsStr.split(" OR ")) {
                    if (Helper.isEmpty(termAsStr) || JetwickQuery.containsForbiddenChars(termAsStr))
                        continue;

                    JTag tag = tmpTags.get(tmpTerm);
                    if (tag == null) {
                        tag = tagSearch.findByTerm(tmpTerm);
                        if (tag == null)
                            tag = new JTag(tmpTerm);
                        tmpTags.put(tmpTerm, tag);
                        counter++;
                    }
                }
            }
            logger.info("Will add query terms " + counter + " of " + userQueryTerms);
        } catch (Exception ex) {
            logger.error("Couldn't query user index to feed tweet index with user queries:" + Helper.getMsg(ex));
        }

        tags.clear();
        tags.addAll(tmpTags.values());
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

    public int guessNewTweets(Collection<JTweet> tweets, long maxTime) {
        int counter = 0;
        for (JTweet tw : tweets) {
            if (tw.getCreatedAt().getTime() > maxTime - 1000)
                counter++;
        }
        // the problem araise when we have a lot of tags which are waiting too long
        if (counter > 98)
            return 200;

        return counter;
    }
}
