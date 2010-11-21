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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;


import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class StringCleaner {

    private Logger logger = LoggerFactory.getLogger(getClass());
    protected Set<String> blackList = new LinkedHashSet<String>();
    private String file;

    public StringCleaner() {
    }

    public StringCleaner(String blacklistFile) {
        readFile(blacklistFile);
    }

    public StringCleaner(BufferedReader reader) {
        readFile(reader);
    }

    public Set<String> getBlacklist() {
        return blackList;
    }

    public boolean contains(String str) {
        str = str.toLowerCase();
        return blackList.contains(str);
    }

    public void readFile(String blacklistFile) {
        try {
            file = blacklistFile;
            readFile(new BufferedReader(new InputStreamReader(new FileInputStream(file), Helper.UTF8)));
        } catch (IOException ex) {
            logger.info("Cannot read user black list " + ex.getLocalizedMessage());
        }
    }

    public void readFile(BufferedReader reader) {
        try {
            blackList = new LinkedHashSet<String>();
            String line = null;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line != null && line.length() > 0)
                    add(line);
            }
            reader.close();
        } catch (IOException ex) {
            logger.info("Cannot read user black list " + ex.getLocalizedMessage());
        }
    }

    public boolean add(String str) {
        if (!str.startsWith("#"))
            return blackList.add(str.toLowerCase());

        return false;
    }

    public int size() {
        return blackList.size();
    }

    @Override
    public String toString() {
        return blackList.toString();
    }
}
