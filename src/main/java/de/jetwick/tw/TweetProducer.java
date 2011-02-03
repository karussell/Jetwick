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

import de.jetwick.es.ElasticUserSearch;
import de.jetwick.tw.queue.TweetPackage;
import java.util.concurrent.BlockingQueue;

/**
 * fills the tweets queue via twitter searchAndGetUsers (does not cost API calls)
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public interface TweetProducer extends Runnable {

    BlockingQueue<TweetPackage> getQueue();

    @Override
    void run();

    void setMaxFill(int maxFill);

    void setTwitterSearch(TwitterSearch tws);

    /**
     * For initialization of all the tags
     */
    void setUserSearch(ElasticUserSearch userSearch);
}
