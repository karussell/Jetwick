/*
 *  Copyright 2010 Peter Karich jetwick_@_pannous_._info
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetwick.tw.queue;

import de.jetwick.solr.SolrTweet;
import java.io.Serializable;
import java.util.concurrent.BlockingQueue;

/**
 * Used to transport tweets from UI to queue
 *
 * The package can be made of
 * 1. tweets from a search, with credits
 * 2. tweets from a user search
 * 3. a simple list
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public interface TweetPackage extends Serializable {

    public TweetPackage retrieveTweets(BlockingQueue<SolrTweet> result);

    int getMaxTweets();

    int getProgress();

    TweetPackageStatus getStatus();

    boolean isAlive();

    TweetPackage doCancel();

    boolean isCanceled();

    TweetPackage doAbort(Exception ex);

    boolean isAborted();

    Exception getException();

    TweetPackage doFinish();

    boolean isFinished();

//    TweetPackage setEndHook(AnyExecutor<?> exector);

    int getProcessedTweets();
}
