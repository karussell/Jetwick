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

import de.jetwick.solr.SolrTweet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class HashtagExtractor extends Extractor {

    Set<String> tags = new LinkedHashSet<String>();

    @Override
    public boolean onNewHashTag(int index, String tag) {
        tags.add(tag.toLowerCase());
        return true;
    }

    public Set<String> getHashtags() {
        return tags;
    }

    @Override
    public HashtagExtractor setText(String text) {
        super.setText(text);
        return this;
    }

    @Override
    public HashtagExtractor setTweet(SolrTweet tweet) {
        super.setTweet(tweet);
        return this;
    }

    @Override
    public HashtagExtractor run() {
        super.run();
        return this;
    }
}
