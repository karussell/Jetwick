/*
 * Copyright 2011 Peter Karich, jetwick_@_pannous_._info.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetwick.es;

import de.jetwick.data.DbObject;
import de.jetwick.util.MyDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import org.elasticsearch.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class was necessary to properly support versioning, because
 * failed objects need to re-added but at a later time.
 * 
 * To be migrated into AbstractElasticSearch so that all data objects can support versioning
 * 
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public abstract class AbstractElasticSearchQueueEnabled<T extends DbObject>
        extends AbstractElasticSearch<T> {

    private Logger logger = LoggerFactory.getLogger(getClass());
    protected int removeOlderThanMinutes = Integer.MAX_VALUE;
    private BlockingQueue<T> todoObjects;
    private BlockingDeque<FailedObject<T>> failedObjects = new LinkedBlockingDeque<FailedObject<T>>();
    private int bulkUpdateSize = 200;
    private transient long bulkIndexingWait = 3 * 1000L;
    private Thread todoObjectsThread;
    private Thread failedObjsThread;
    private AtomicInteger todoCount = new AtomicInteger(0);

    public AbstractElasticSearchQueueEnabled(String url) {
        super(url);
        ensureQueueStartet();
    }

    public AbstractElasticSearchQueueEnabled(Client client) {
        super(client);
        ensureQueueStartet();
    }

    public AbstractElasticSearchQueueEnabled() {
        ensureQueueStartet();
    }

    public void setBulkIndexingWait(long bulkIndexingWait) {
        this.bulkIndexingWait = bulkIndexingWait;
    }

    @Override
    public void setTesting(boolean testing) {
        this.testing = testing;
        if (testing) {
            bulkIndexingWait = 1;
        }
    }

    public void setBatchSize(int batchSize) {
        this.bulkUpdateSize = batchSize;
    }

    public int getBatchSize() {
        return bulkUpdateSize;
    }

    public void setRemoveOlderThanDays(int removeDays) {
        setRemoveOlderThanHours(removeDays * 24);
    }

    public void setRemoveOlderThanHours(int removeHours) {
        removeOlderThanMinutes = removeHours * 60;
    }

    public MyDate createRemoveOlderThan() {
        return new MyDate().minusMinutes(removeOlderThanMinutes).castToHour();
    }

    public abstract void innerAdd(T tw);

    public abstract void innerThreadMethod() throws InterruptedException;

    public void queueFailedObject(T o) {
        getRawFailedObjects().offer(new FailedObject<T>(System.currentTimeMillis(), o));
    }

    protected int getTodoObjectsSize() {
        return 1000;
    }

    public synchronized BlockingQueue<T> getTodoObjects() {
        if (todoObjects == null)
            todoObjects = new LinkedBlockingDeque<T>(getTodoObjectsSize());

        return todoObjects;
    }

    private synchronized BlockingDeque<FailedObject<T>> getRawFailedObjects() {
        if (failedObjects == null)
            failedObjects = new LinkedBlockingDeque<FailedObject<T>>();

        return failedObjects;
    }

    public void queueObject(T o) {
        queueObjects(Collections.singletonList(o));
    }

    public void queueObjects(Collection<T> objs) {        
        todoCount.addAndGet(objs.size());
        try {
            int cap = getTodoObjects().remainingCapacity();
            long start = System.currentTimeMillis();
            for (T t : objs) {
                getTodoObjects().put(t);
            }
            float secs = (System.currentTimeMillis() - start) / 1000f;
            if (secs > 1) {
                logger.error("ES too slow? Putting " + objs.size()
                        + " objects into queue took too long (" + secs + " secs)"
                        + " Capacity is:" + getTodoObjects().remainingCapacity()
                        + " and was before queueing:" + cap);
            }
        } catch (Exception ex) {
            logger.error("problem while queueObjects", ex);
        }
    }

    void skipAlreadyQueued(Collection<T> newObjs, Collection<T> existing) {
        // TODO PERFORMANCE use separate set which stores queued objects!
        Iterator<T> iter = newObjs.iterator();
        while (iter.hasNext()) {
            T newT = iter.next();
            for (T existingT : existing) {
                if (existingT.equals(newT)) {
                    // TODO
//                    existingT.updateFrom(newT);
                    iter.remove();
                    break;
                }
            }
        }
    }

    @Override
    public boolean hasVersionSupport() {
        return true;
    }

    protected void ensureQueueStartet() {
        if (todoObjectsThread == null || !todoObjectsThread.isAlive() && !todoObjectsThread.isInterrupted()) {
            if (todoObjectsThread != null) {
                todoObjectsThread.interrupt();
                todoObjectsThread = null;
            }

            if (failedObjsThread != null) {
                failedObjsThread.interrupt();
                failedObjsThread = null;
            }

            failedObjsThread = new Thread(getClass().getSimpleName() + "-Queue-FailedObj") {

                @Override
                public void run() {
                    while (true) {
                        try {
                            FailedObject<T> oldestFailedObject = getRawFailedObjects().take();
                            if (oldestFailedObject == null)
                                break;

                            long delta = oldestFailedObject.getQueuedTime() + bulkIndexingWait * 2 - System.currentTimeMillis();
                            if (delta <= 0)
                                queueObject(oldestFailedObject.getObject());
                            else {
                                if (!getRawFailedObjects().offerFirst(oldestFailedObject))
                                    logger.error("Failed objects are too many skipped:" + oldestFailedObject.getObject());
                                //System.out.println("delta " + delta + " failedObj:" + oldestFailedObject);
                                Thread.sleep(delta + 10);
                            }
                        } catch (Exception ex) {
                            logger.error(getName() + " was interrupted!!", ex);
                            break;
                        }
                    }
                }
            };
            failedObjsThread.start();

            todoObjectsThread = new Thread(getClass().getSimpleName() + "-Queue") {

                @Override
                public void run() {
                    logger.info(getName() + " started with batchSize " + bulkUpdateSize);
                    // force 'init'
                    getTodoObjects();
                    while (!isInterrupted()) {
                        try {
                            // use 'take' and this while loop to make sure that 
                            // all of the objects added via todoObjects.addAll 
                            // will be updated in one batch (via innerThreadMethod)
                            int counter = 0;
                            while (true) {
                                T obj = todoObjects.take();
                                innerAdd(obj);
                                counter++;
                                if (todoCount.decrementAndGet() <= 0)
                                    break;

                                if (counter >= bulkUpdateSize)
                                    break;
                            }
                            innerThreadMethod();

                            // now trigger refresh for tests
                            synchronized (todoObjects) {
                                todoObjects.notifyAll();
                            }

                            logger.info("Alive with entries:" + todoObjects.size() + " failedQueueSize:" + getRawFailedObjects().size()
                                    + " updated:" + counter + " ... going to sleep");
//                            logger.info("Failed:" + getRawFailedObjects());
                            for (int i = 0; i < 10 && todoObjects.size() < bulkUpdateSize; i++) {
                                Thread.sleep(bulkIndexingWait);
                            }
                        } catch (Exception ex) {
                            logger.error(getName() + " was interrupted!!", ex);
                            break;
                        }
                    }
                    logger.info(getName() + " finished");
                }
            };
            todoObjectsThread.start();
        }
    }

    public Collection<T> getFailedObjects() {
        List<T> list = new ArrayList<T>();
        for (FailedObject<T> o : getRawFailedObjects()) {
            list.add(o.getObject());
        }
        return list;
    }

    /**
     * Blocks until queue gets empty. Use only for tests!
     * 
     * @return true if queue was really empty. At least for some microseconds ;)
     */
    public boolean forceEmptyQueueAndRefresh() {
        return forceEmptyQueueAndRefresh(100);
    }

    public boolean forceEmptyQueueAndRefresh(long maxWaitTime) {
        synchronized (todoObjects) {
            try {
                do {
                    if (maxWaitTime > 0)
                        todoObjects.wait(maxWaitTime);
                    else
                        todoObjects.wait();
                } while (todoObjects.size() > 0);

                refresh();
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    public void finish() {
        if (todoObjectsThread != null)
            todoObjectsThread.interrupt();
        if (failedObjsThread != null)
            failedObjsThread.interrupt();
    }
}
