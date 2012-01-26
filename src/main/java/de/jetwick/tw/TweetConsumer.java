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
import de.jetwick.data.JTweet;
import de.jetwick.data.UrlEntry;
import de.jetwick.snacktory.JResult;
import de.jetwick.util.GenericUrlResolver;
import de.jetwick.util.StopWatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.elasticsearch.common.collect.MapMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * stores the tweets from the queue into the dbHelper and solr
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetConsumer extends Thread {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private List<QueueInfo<JTweet>> inputQueues = new ArrayList<QueueInfo<JTweet>>();
    @Inject
    protected GenericUrlResolver resolver;
    private Map<Long, Object> tweetCache;
    private static final Object OBJECT = new Object();
    private UrlExtractor urlExtractor;

    public TweetConsumer() {
        super("tweet-consumer");
    }

    public GenericUrlResolver getResolver() {
        return resolver;
    }    

    @Override
    public void run() {
        initTweetCache();
        urlExtractor = new UrlExtractor() {

            @Override
            public JResult getInfo(String originalUrl, int timeout) throws Exception {
                JResult res = UrlEntry.createSimpleResult(originalUrl);
                return res;
            }
        };

        int counter = 0;
        StopWatch sw = new StopWatch();
        while (true) {
            counter++;
            sw.start();
            int feeded = executeOneBatch();
            sw.stop();
            if (feeded < 10) {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ex) {
                    logger.error(getName() + " interrupted while sleeping: " + ex.getLocalizedMessage());
                    break;
                }
            }

            // print stats
            if (counter % 1000 == 0) {
                logger.info("time of polling:\t" + sw.getSeconds());
                sw = new StopWatch();

                logger.info("tweetCache size:\t" + tweetCache.size());
                logger.info("tweetTodo size:\t" + resolver.getInputQueue().size());
                for (QueueInfo<JTweet> qi : inputQueues) {
                    logger.info(qi.toString());
                }
            }
        }
        logger.warn(getName() + " finished");
    }

    public void setResolver(GenericUrlResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * @param queueName the identifier of the input queue     
     * @param capacity  the number of elements which should fit into the input queue.
     * This should be at least twice times bigger than batchSize.
     * @param batchSize the number of elements to feed at once into main output queue.      
     * @return the newly registered queue
     */
    public BlockingQueue<JTweet> register(String queueName, int capacity, int batchSize) {
        BlockingQueue q = new LinkedBlockingQueue<JTweet>(capacity);
        QueueInfo qInfo = new QueueInfo(queueName, q);
        for (QueueInfo<JTweet> qi : inputQueues) {
            if (qi.getName().equals(queueName))
                throw new IllegalStateException("cannot register queue. Queue " + queueName + " already exists");
        }

        qInfo.setBatchSize(batchSize);
        inputQueues.add(qInfo);

        int sum = 0;
        for (QueueInfo<JTweet> qi : inputQueues) {
            sum += qi.getBatchSize();
        }

        int mainCapacity = resolver.getInputQueue().remainingCapacity() + resolver.getInputQueue().size();
        if (sum * 2 > mainCapacity)
            throw new IllegalStateException("cannot register queue " + queueName + " because it"
                    + " would increas capacity of all input queues too much (" + sum + ") and "
                    + " can block main queue too often, where the capacity is only:" + mainCapacity);
        return qInfo.getQueue();
    }

    public int executeOneBatch() {
        int feeded = 0;
        for (QueueInfo<JTweet> qi : inputQueues) {
            int batchSize = qi.getBatchSize();
            Queue<JTweet> queue = qi.getQueue();
            int newTweets = 0;
            for (; newTweets < batchSize; newTweets++) {
                JTweet tw = queue.poll();
                if (tw == null)
                    break;

                if (!tw.isPersistent() && tweetCache != null && tweetCache.put(tw.getTwitterId(), OBJECT) != null) {
                    newTweets--;
                    continue;
                }

                if (urlExtractor != null) {
                    for (UrlEntry ue : ((UrlExtractor) urlExtractor.setTweet(tw).run()).getUrlEntries()) {
                        tw.addUrlEntry(ue);
                    }
                }
                feeded++;
                resolver.queueObject(tw);
            }

        }
        return feeded;
    }

    public void initTweetCache() {
        if (tweetCache == null)
            tweetCache = new MapMaker().concurrencyLevel(20).
                    maximumSize(50000).expireAfterWrite(6 * 60, TimeUnit.MINUTES).makeMap();
    }

    public static class QueueInfo<JTweet> {

        private final String name;
        private long lastMeasureTime = System.currentTimeMillis();
        private final BlockingQueue<JTweet> queue;
        private int batchSize = 200;
        private int outputCount;
        private float outputFrequency;

        public QueueInfo(String name, BlockingQueue<JTweet> queue) {
            this.name = name;
            this.queue = queue;
        }

        public BlockingQueue<JTweet> getQueue() {
            return queue;
        }

        public String getName() {
            return name;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public void setOutputFrequency(float outputFrequency) {
            this.outputFrequency = outputFrequency;
        }

        public float getOutputFrequency() {
            return outputFrequency;
        }

        public void setLastMeasureTime(long lastMeasureTime) {
            this.lastMeasureTime = lastMeasureTime;
        }

        public long getLastMeasureTime() {
            return lastMeasureTime;
        }

        public int getOutputCount() {
            return outputCount;
        }

        public void setOutputCount(int outputCount) {
            this.outputCount = outputCount;
        }

        @Override
        public String toString() {
            return getName() + "\t size:" + getQueue().size() + "\t count:" + outputCount + "\t oFreq.:" + getOutputFrequency();
        }
    }
}
