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

import com.google.inject.Inject;
import de.jetwick.tw.Credits;
import de.jetwick.tw.TwitterSearch;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public abstract class AbstractTweetPackage implements TweetPackage {

    private int id;
    private int progress;
    protected int processedTweets;
    private TweetPackageStatus status = TweetPackageStatus.STARTED;
    private Exception exception;
    private int maxTweets;
    @Inject
    private TwitterSearch twitterSearch;
//    private AnyExecutor<?> endHook;

    public AbstractTweetPackage() {
    }

    protected AbstractTweetPackage init(int id, int maxTweets) {
        this.id = id;
        this.maxTweets = maxTweets;
        return this;
    }

    @Override
    public int getProgress() {
        return progress;
    }

    public TweetPackage setProgress(int progress) {
        this.progress = progress;
        return this;
    }

    @Override
    public TweetPackageStatus getStatus() {
        return status;
    }

    @Override
    public boolean isAlive() {
        return !isCanceled() && !isFinished() && !isAborted();
    }

    @Override
    public TweetPackage doCancel() {
        status = TweetPackageStatus.CANCELED;
        progress = 100;
//        endHook.execute(null);
        return this;
    }

    @Override
    public TweetPackage doAbort(Exception ex) {
        status = TweetPackageStatus.ABORTED;
        progress = 100;
//        endHook.execute(null);
        return this;
    }

    @Override
    public TweetPackage doFinish() {
        status = TweetPackageStatus.FINISHED;
        progress = 100;
//        endHook.execute(null);
        return this;
    }

    @Override
    public boolean isCanceled() {
        return status == TweetPackageStatus.CANCELED;
    }

    @Override
    public boolean isFinished() {
        return status == TweetPackageStatus.FINISHED;
    }

    @Override
    public boolean isAborted() {
        return status == TweetPackageStatus.ABORTED;
    }

    @Override
    public Exception getException() {
        return exception;
    }

    @Override
    public int getMaxTweets() {
        return maxTweets;
    }

//    @Override
//    public TweetPackage setEndHook(AnyExecutor<?> exector) {
//        endHook = exector;
//        return this;
//    }
    @Override
    public int getProcessedTweets() {
        return processedTweets;
    }

    public TwitterSearch getTwitterSearch(Credits credits) {
        twitterSearch.setCredits(credits);
        twitterSearch.init();
        return twitterSearch;
    }

    @Override
    public String toString() {
        return "id:" + id + " maxTweets:" + maxTweets + " class:" + getClass().getSimpleName();
    }
}
