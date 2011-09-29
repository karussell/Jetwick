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

import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class MyThread extends Thread {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public MyThread(String name) {
        super(name);
    }

    protected synchronized boolean myWait(float seconds) {
        try {
            if (seconds <= 0)
                return true;
            wait((int) (seconds * 1000.0));
            return true;
        } catch (InterruptedException ex) {
            return false;
        }
    }

//    public Integer tooManyObjectsWait(Collection<?> tweets,
//            int fill, String info, float wait, boolean log) {
//
//        while (true) {
//            if (tweets.size() < fill)
//                break;
//
//            // log not too often
//            if (log)
//                logger.info("WAITING! " + tweets.size() + " are too many objects from " + info + "!");
//            if (!myWait(wait))
//                return null;
//        }
//
//        return tweets.size();
//    }
}
