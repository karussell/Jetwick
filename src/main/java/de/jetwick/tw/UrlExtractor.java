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
import de.jetwick.data.UrlEntry;
import de.jetwick.snacktory.JResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public abstract class UrlExtractor extends Extractor {

    private List<UrlEntry> urlEntries = new ArrayList<UrlEntry>(1);

    @Override
    public String toLink(String url, String title) {
        return url;
    }

    @Override
    public UrlExtractor setText(String text) {
        super.setText(text);
        return this;
    }

    public Collection<UrlEntry> getUrlEntries() {
        return urlEntries;
    }

    @Override
    public UrlExtractor run() {
        int index = 0;
        urlEntries.clear();
        for (; (index = text.indexOf("http://", index)) >= 0; index++) {
            String subStr = text.substring(index);

            // url shorteners seems to have a "domain.de" shorter or equal to 11
            // the longest was tinyurl.com the shortest is t.co
            int index2 = subStr.indexOf("/", 7);
            if (index2 < 0)
                index2 = Math.max(0, subStr.indexOf(" ", 7));

            String domain = subStr.substring(0, index2);
            index2 = domain.lastIndexOf(".");
            StringBuilder tmpSb = new StringBuilder();
            int lastIndex = onNewRawUrl(index, tmpSb);

            if (lastIndex > 0) {
                String url = tmpSb.toString();
                JResult res = null;
                try {
                    res = getInfo(url, index2);
                } catch (Exception ex) {
//                    logger.info("Error while resolving:" + url, ex);
                    res = new JResult();
                }
                if (res.getUrl().isEmpty())
                    res.setUrl(url);

                UrlEntry entry = new UrlEntry(index, lastIndex, res.getUrl());
                if (res.getTitle().isEmpty())
                    entry.setResolvedTitle(url);
                else
                    entry.setResolvedTitle(res.getTitle());

                entry.setResolvedDomain(Helper.extractDomain(url));
                urlEntries.add(entry);
            }
        }

        return this;
    }

    public abstract JResult getInfo(String url, int timeout) throws Exception;
}
