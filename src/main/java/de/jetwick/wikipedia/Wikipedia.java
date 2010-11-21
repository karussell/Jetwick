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
    private final String[] stopwords = /* what is ... */ {"your", "name",
        "new", "wrong", "time", "today", "weather", "question", "answer",
        "picture", "up", "true", "matter", "temperature", "news", "gossip"};
    // this, that // it: // empty()
    // can you tell me what the big bang theory is
    private final String[] keywords = new String[]{"what is", "what's",
        "whats", "who is", "what was", "tell me about",
        "do you know about", "do you know who", "who was", "tell me about",
        "define", "definition", "defined", "wiki", "wikipedia", "lookup",
        "look up", "research", "find out about", "you know what ... is",
        "do you know", "ever heard of", "what are", "what does", "what r",
        "definiere"};

    // danger: "do you know"?
    public String[] getDropwords() {
        return new String[]{"the", "what", "who", "is", "was", "anything",
                    "this", "that", "it", "mean"};
    }

    public String[] getKeywords() {
        // TODO Auto-generated method stub
        return keywords;// NOT: do you know why ...
    }

    public boolean handle(String language, String input) throws Exception {
//        if (matchWords(input, stopwords))
//            return false;
//        if (matchWords(input, PRONOUNS))
//            return false;
//        if (matchWords(input, "are", "r") && input.endsWith("s"))
//            input = input.substring(0, input.length() - 1);

        if (empty(input))
            return false;

        String wikiUrl = "http://" + language + ".wikipedia.org/wiki/";
        String q = URLEncoder.encode(input, "UTF8").replace("+", "%20");
        String html_url = wikiUrl + q;
        String url = html_url + "?action=raw";// render
        String res = Helper.readUrl(url, 4000);
        if (empty(res))
            return false;
        if (res.toLowerCase().contains("redir")) {
            //q = cutHead(res, new String[]{"redirect", "#", "REDIRECT"}).trim();
            if (q.length() > 1 && q.length() < 80) {
                res = q;
                res = res.replaceAll("].*", "");
                res = res.replaceAll("\\n.*", "");
                res = res.replaceAll(".*\\[", "");
                q = URLEncoder.encode(res, "UTF8").replace("+", "%20");
                html_url = wikiUrl + q;
                url = html_url + "?action=raw";// render
                res = Helper.readUrl(url, 2000);
            }
        }
        if (empty(res))
            return false;
        int max_length = 40000;
        if (res.length() > max_length)
            res = res.substring(0, max_length);
        // if (res.contains("may refer to"))
        // return false;
        // if (res.contains("Error"))// todo
        // return false;
        // res = fixHtml(res);
        res = fixWiki(res);
        if (res.length() > 4000)
            res = res.substring(0, 4000);

        if (res.length() < 40)
            return false;

        // open(html_url);
        return true;
    }

    private boolean empty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static String fixWiki(String res) {
        // NOOOOO use bliki once and for all
        // res = res.replaceAll("\\{\\{[^|^\\{]*?\\}\\}+", "");...
        res = res.replaceAll("&#.*?;", " ");
        res = res.replaceAll("&nbsp;", " ");
        res = res.replaceAll("<link.*?link>", " ");
        res = res.replaceAll("<ref.*?ref>", " ");
        final int length = res.length();
        int bad1 = 0;
        int bad2 = 0;
        int bad3 = 0;
        int lastP = 0;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < length; i++) {
            final char charA = res.charAt(i);
            if (charA == '{')
                bad1++;
            if (charA == '[') {
                bad2++;
                if (bad2 < 4)
                    lastP = i;
            }
            if (charA == '|')
                lastP = i;

            if (charA == '(')
                bad3++;
            if (bad1 <= 0 && bad2 <= 0) {

                b.append(charA);

            }
            if (charA == '}')
                bad1--;
            if (charA == ']') {
                bad2--;

                if (bad1 < 1 && lastP > 0) {// what the hack!
                    b.append(res.substring(lastP + 1, i));
                    // free standing [[bla|Suffering]]
                    lastP = 0;
                }
            }
            if (charA == ')')
                bad3--;
        }
        res = b.toString();
        res = res.replace("*", " ");
        res = res.replaceAll("\n", " ");
        res = res.replaceAll("'''", " ");
        res = res.replaceAll("''", " ");
        res = res.replaceAll("==", " ");
        res = res.replaceAll("=", " ");
        res = res.replaceAll(", ,", " ");
        res = res.replaceAll("  ", " ");
        return res;
    }

    public String getHelpMessage() {
        return " say 'define luck' to listen to a Wikipedia entry";
    }

    public Collection<WikiEntry> query(String query, String language) {
        return query(query, language, 3);
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



