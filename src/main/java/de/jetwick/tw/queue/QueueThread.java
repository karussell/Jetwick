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

import de.jetwick.util.AnyExecutor;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class QueueThread implements Runnable {

    private int progress;
    private TweetPackageStatus status = TweetPackageStatus.STARTED;
    private Exception exception;
    private String name;
    private AnyExecutor<?> endHook = AnyExecutor.EMPTY_EXECUTOR;

    public QueueThread() {
    }

    public QueueThread(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getProgress() {
        return progress;
    }

    public QueueThread setProgress(int progress) {
        this.progress = progress;
        return this;
    }

    public TweetPackageStatus getStatus() {
        return status;
    }

    public boolean isAlive() {
        return !isCanceled() && !isFinished() && !isAborted();
    }

    public QueueThread doCancel() {
        status = TweetPackageStatus.CANCELED;
        progress = 100;
        endHook.execute(null);
        return this;
    }

    public QueueThread doAbort(Exception ex) {
        status = TweetPackageStatus.ABORTED;
        progress = 100;
        endHook.execute(null);
        return this;
    }

    public QueueThread doFinish() {
        status = TweetPackageStatus.FINISHED;
        progress = 100;
        endHook.execute(null);
        return this;
    }

    public boolean isCanceled() {
        return status == TweetPackageStatus.CANCELED;
    }

    public boolean isFinished() {
        return status == TweetPackageStatus.FINISHED;
    }

    public boolean isAborted() {
        return status == TweetPackageStatus.ABORTED;
    }

    public Exception getException() {
        return exception;
    }

    public void setEndHook(AnyExecutor<?> endHook) {
        this.endHook = endHook;
    }

    @Override
    public void run() {
    }
}
