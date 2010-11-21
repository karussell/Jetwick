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

package de.jetwick.data;

import de.jetwick.solr.SolrTweet;
import java.util.Comparator;

/**
 * tweets with bigger id comes first (ie. latest tweets first)
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TwitterIdComparator implements Comparator<SolrTweet> {

    @Override
    public int compare(SolrTweet t1, SolrTweet t2) {
        long o1 = t1.getTwitterId();
        long o2 = t2.getTwitterId();
        if (o2 > o1)
            return 1;
        else if (o2 < o1)
            return -1;
        else
            return 0;
    }
}
