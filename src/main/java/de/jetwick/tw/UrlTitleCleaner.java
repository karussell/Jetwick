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

import de.jetwick.util.Helper;
import java.io.BufferedReader;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class UrlTitleCleaner extends StringCleaner {

    public UrlTitleCleaner(String blacklistFile) {
        super(blacklistFile);
    }

    public UrlTitleCleaner(BufferedReader reader) {
        super(reader);
    }

    public UrlTitleCleaner() {
    }

    @Override
    public boolean contains(String str) {
        return super.contains(Helper.trimNL(Helper.trimAll(str)));
    }

    @Override
    public boolean add(String str) {
        str = Helper.trimNL(Helper.trimAll(str));

        // WARNING if an url starts with '#' it won't be blacklisted!!??
        return super.add(str);
    }
}
