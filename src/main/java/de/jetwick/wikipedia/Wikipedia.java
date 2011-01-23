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

package de.jetwick.wikipedia;

import de.jetwick.util.Helper;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class Wikipedia {

    private static Logger logger = LoggerFactory.getLogger(Wikipedia.class);

    public Collection<WikiEntry> query(String query, String language) {
        return query(query, language, 3);
    }
    
    private boolean empty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * http://en.wikipedia.org/w/api.php
     *
     * @return url, title, text
     */
    public Collection<WikiEntry> query(String query, String language, int hits) {
        List<WikiEntry> result = new ArrayList<WikiEntry>();
        if (language == null || language.length() != 2)
            language = "en";

        String wikiUrl = "http://" + language + ".wikipedia.org/w/api.php?action=query&list=search&srsearch="
                + Helper.urlEncode(query) + "&srinfo=totalhits&format=xml&srlimit=" + hits;

        try {
            Document doc = Helper.readUrlAsDocument(wikiUrl, 1000);
            NodeList list = doc.getElementsByTagName("p");
            for (int ii = 0; ii < list.getLength(); ii++) {
                Node node = list.item(ii);
                if (node.getNodeType() != Node.ELEMENT_NODE)
                    continue;

                WikiEntry entry = new WikiEntry();
                entry.text = ((Element) node).getAttribute("snippet");
                entry.title = ((Element) node).getAttribute("title");
                if (!empty(entry.title)) {
                    entry.url = "http://" + language + ".wikipedia.org/wiki/" + Helper.urlEncode(entry.title.replaceAll(" ", "_"));
                    result.add(entry);
                }
            }

        } catch (Exception ex) {
            logger.error("Cannot query wikipedia " + ex.getLocalizedMessage() + " URL was:" + wikiUrl);
        }

        return result;
    }
}



