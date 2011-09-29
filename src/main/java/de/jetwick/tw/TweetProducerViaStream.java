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
import de.jetwick.data.JTweet;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.es.ElasticTagSearch;
import de.jetwick.util.Helper;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterStream;

/**
 * fills the tweets queue via twitter searchAndGetUsers (does not cost API calls)
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetProducerViaStream extends MyThread implements TweetProducer {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected BlockingQueue<JTweet> resultTweets = new LinkedBlockingQueue<JTweet>();
    protected TwitterSearch twSearch;
    protected ElasticTagSearch tagSearch;
    private long newStreamInterval = 3 * 60 * 1000;
    private double tweetsPerSecLimit = 0.5;

    public TweetProducerViaStream() {
        super("tweet-producer-stream");
    }

    @Override
    public void setQueue(BlockingQueue<JTweet> packages) {
        this.resultTweets = packages;
    }

    @Override
    public void run() {
        TwitterStream stream = null;
        TwitterStream oldStream = null;
        // we cannot detect frequency of all terms but detect + remove high frequent disturbers
        Map<String, Integer> termFreq = new LinkedHashMap<String, Integer>();

        while (true) {
            try {
                // stream only LESS FREQUENT tags! leave popular tags only for search                
                Collection<String> input = initTags(termFreq);
                termFreq.clear();
                if (input.isEmpty()) {
                    logger.error("No less frequent tags found! Frequency limit:" + tweetsPerSecLimit);
                    if (!myWait(10))
                        break;
                    continue;
                }
                int counter = 0;
                logger.info("Starting over with " + input.size()
                        + " tags. indexed tweets:" + counter
                        + " tweetsPerSecLimit:" + tweetsPerSecLimit
                        + " " + input);
                if (stream != null)
                    oldStream = stream;

                // use a separate collection here to let the listener release when doing garbage collection
                // (the listener which is added in the streamingTwitter method)
                BlockingQueue<JTweet> queue = new LinkedBlockingQueue<JTweet>(1000);
                stream = twSearch.streamingTwitter(input, queue);

                // shutdown old stream
                if (oldStream != null) {
                    oldStream.shutdown();
//                    oldStream.cleanUp();
                }

                long start = System.currentTimeMillis();
                while (true) {
                    JTweet tw = queue.take();
                    String matchingTerm = null;
                    String txt = tw.getLowerCaseText();
                    for (String term : input) {
                        if (txt.contains(term)) {
                            matchingTerm = term;
                            break;
                        }
                    }
                    resultTweets.put(tw.setFeedSource("from stream:" + matchingTerm));
                    Integer integ = termFreq.put(matchingTerm, 1);
                    if (integ != null)
                        termFreq.put(matchingTerm, integ + 1);

                    counter++;
                    // UPDATE tags after a while
                    if ((System.currentTimeMillis() - start) > newStreamInterval)
                        break;
                }
            } catch (Exception ex) {
                logger.error("!! Error while getting tweets via streaming API. Waiting and trying again.", ex);
                if (!myWait(60 * 5))
                    break;
            }
        }

        logger.info(getName() + " finished");
    }

    @Override
    public void setTwitterSearch(TwitterSearch tws) {
        this.twSearch = tws;
    }

    public Collection<String> initTags(Map<String, Integer> termFreq) {
        Map<String, JTag> tags = new LinkedHashMap<String, JTag>();
        try {
            for (JTag tag : tagSearch.findLowFrequent(0, 1000, tweetsPerSecLimit)) {
                if (tag.getTerm() != null) {
                    // information in index is based on old search data check if 'realtime' tweetsPerSec is also ok
                    Integer counts = termFreq.get(tag.getTerm());
                    if (counts != null && counts / (newStreamInterval / 1000f) > tweetsPerSecLimit) {
                        logger.info("Detected tag with a too high frequency (based on stream data):"
                                + tag + " stream-counts:" + counts);
                        continue;
                    }
                    int spaces = Helper.countChars(tag.getTerm(), ' ');
                    if (spaces > 7) {
                        logger.info("Skipping term " + tag.getTerm() + " because too many spaces:" + spaces);
                        continue;
                    }

                    if (tag.getTerm().contains(" OR ")) {
                        logger.warn("Hmmh somewhere the OR came into the tag index!?");
                        continue;
                    }

                    tags.put(tag.getTerm(), tag);
                }
            }
        } catch (Exception ex) {
            logger.info("Couldn't query tag index", ex);
        }
        // TODO further remove overlapping tags like 'wicket' and 'apache wicket'
        Set<String> input = new LinkedHashSet<String>();
        int MAX_TAGS = 400;
        MAIN:
        for (JTag t : tags.values()) {
            String term = t.getTerm();
            if (input.size() >= MAX_TAGS) {
                logger.error("Too many Tags - Cannot further add tags!" + input.size());
                break MAIN;
            }
            // filter by links only does NOT work!!
            input.add(term.trim());// + " " + TwitterSearch.LINK_FILTER);
        }
        return input;
    }

    @Override
    public void setTagSearch(ElasticTagSearch tagSearch) {
        this.tagSearch = tagSearch;
    }

    @Override
    public void setUserSearch(ElasticUserSearch userSearch) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setNewStreamInterval(long millis) {
        newStreamInterval = millis;
    }

    public void setTweetsPerSecLimit(double tweetsPerSecLimit) {
        this.tweetsPerSecLimit = tweetsPerSecLimit;
    }
}
