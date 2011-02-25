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

import de.jetwick.data.JTweet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetUserExtractor extends Extractor {

    private Map<Integer, String> userMap = new LinkedHashMap<Integer, String>();

    @Override
    public TweetUserExtractor setText(String text) {
        return (TweetUserExtractor) super.setText(text);
    }

    @Override
    public TweetUserExtractor setTweet(JTweet tweet) {
        return (TweetUserExtractor) super.setTweet(tweet);
    }

    @Override
    public TweetUserExtractor run() {
        return (TweetUserExtractor) super.run();
    }

    @Override
    public boolean onNewUser(int index, String user) {
        userMap.put(index, user.toLowerCase());
        return true;
    }

    public Map<Integer, String> getUsers() {
        return userMap;
    }
}
