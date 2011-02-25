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

package de.jetwick.tw.cmd;

import de.jetwick.data.JTweet;
import de.jetwick.util.AnyExecutor;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SerialCommandExecutor {

    private ArrayList<AnyExecutor> list = new ArrayList<AnyExecutor>();
    private Collection<JTweet> tweets;

    public SerialCommandExecutor(Collection<JTweet> tweets) {
        this.tweets = tweets;
    }

    public SerialCommandExecutor add(AnyExecutor... cmds) {
        for (AnyExecutor cmd : cmds) {
            list.add(cmd);
        }
        return this;
    }

    public SerialCommandExecutor add(AnyExecutor cmd) {
        list.add(cmd);
        return this;
    }

    public Collection<JTweet> execute() {
        for (AnyExecutor cmd : list) {
            for (JTweet tw : tweets) {
                cmd.execute(tw);
            }
        }
        return tweets;
    }
}
