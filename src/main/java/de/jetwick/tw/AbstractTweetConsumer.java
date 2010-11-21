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
import de.jetwick.config.Configuration;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrTweetSearch;
import de.jetwick.util.MyDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.Tweet;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class AbstractTweetConsumer extends MyThread {

    private final Logger logger = LoggerFactory.getLogger(getClass());     
    protected TweetProducer producer;
    @Inject
    protected SolrTweetSearch tweetSearch;
    protected StringCleaner tweetCleaner;
    private int removeDays = 8;

    public AbstractTweetConsumer(String name, Configuration cfg) {
        super(name);
        tweetCleaner = new StringCleaner(cfg.getUserBlacklist());
    }

    public Collection<SolrTweet> updateDbTweets(Queue<Tweet> tws, int batch) {
        Set<Tweet> tmpTweets = new LinkedHashSet(batch);
        for (int i = 0; i < batch; i++) {
            Tweet tw = tws.poll();
            if (tw == null)
                break;

            if (!tweetCleaner.contains(tw.getFromUser()))
                tmpTweets.add(tw);
        }

//        return updateDbTweetsInTA(tmpTweets);
        try {
            return tweetSearch.update(tmpTweets, new MyDate().minusDays(removeDays).toDate());
        } catch (Exception ex) {
            logger.error("Cannot update " + tmpTweets.size() + " tweets.", ex);
//            for (Tweet tw : tmpTweets) {
//                String str = "";
//                if (tw instanceof Twitter4JTweet)
//                    str = " irosi:" + ((Twitter4JTweet) tw).getInReplyToStatusId() + " ";
//
//                System.out.println(tw.getId() + " " + tw.getCreatedAt() + " " + tw.getFromUser() + " " + str + tw.getText());
//            }
            return Collections.EMPTY_LIST;
        }
    }
//    private StopWatch sw5 = new StopWatch(" 5");
//
//    @Transactional
//    private UpdateResult updateDbTweetsInTA(Collection<Tweet> tws) {
//        UpdateResult res = new UpdateResult();
//        for (Tweet tw : tws) {
//            UpdateResult tmp = dbHelper.update(tw);
//            res.addAll(tmp);
//        }
//
//        return res;
//    }

    public void setTweetProducer(TweetProducer produceTweetsThread) {
        this.producer = produceTweetsThread;
    }

    public void setTweetSearch(SolrTweetSearch tweetSearch) {
        this.tweetSearch = tweetSearch;
    }

    public void setRemoveDays(int solrRemoveDays) {
        removeDays = solrRemoveDays;
    }
}
