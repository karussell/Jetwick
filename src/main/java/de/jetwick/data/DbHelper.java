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

import java.util.Collection;
import twitter4j.Tweet;

/**
 * Cross-cutting-concern database manager interface.
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public interface DbHelper {

    UpdateResult update(Tweet tweet);

    UpdateResult update(Collection<? extends Tweet> twitterTweets);

    /** Especially useful to copy the full index back to the db */
    void addUsers(Collection<YUser> list);

    void setRemoveDays(int days);

    void setSearchForRTDays(int searchTweetsDays);
}
