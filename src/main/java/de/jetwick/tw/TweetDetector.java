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
import de.jetwick.tw.cmd.StringFreqMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * This class tries to detect the language and stores terms of the specified tweets.
 *
 * Used while indexing to store the language and the terms as additional field attributes.
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetDetector {

    public static final String UNKNOWN_LANG = "unknown";
    public static final String MISC_LANG = "misc";
    public static final String NUM = "num";
    public static final String SINGLE = "1";
    public static final String DE = "de";
    public static final String NL = "nl";
    public static final String EN = "en";
    public static final String RU = "ru";
    public static final String SP = "sp";
    public static final String FR = "fr";
    public static final String PT = "pt";
    private Collection<JTweet> tweets;
    private int termMaxCount = 6;
    private StringFreqMap languages = new StringFreqMap();
    private StringFreqMap terms = new StringFreqMap();

    public TweetDetector(Collection<JTweet> tweets) {
        this.tweets = tweets;
    }

    public TweetDetector() {
    }

    /**
     * To create symbol-free terms
     *
     * TODO PERFORMANCE expensive method
     */
    public static String stripNoiseFromWord(String str) {
        if (str.length() < 2)
            return str;

        // remove highlighting
        str = str.replaceAll("<b>", "");
        str = str.replaceAll("</b>", "");
        // ignore urls
        // urls contain all characters except spaces: [^ ] and we need this multple times: *
        str = str.replaceAll("http[s]?://[^ ]*", " ");
        str = str.replaceAll("[\\\"\\:\\;\\&\\.\\!\\?\\)\\(\\[\\]\\,\\>\\<\\-\\n\\t\\&]", " ");
        str = str.replaceAll(" #", " ");
        // or at the beginning of the line
        if (str.charAt(0) == '#')
            str = str.substring(1);
        str = str.replaceAll("^#", " ");

        str = str.replaceAll(":-", " ");
        str = str.replaceAll(";-", " ");

        return str;
    }

    public List<Entry<String, Integer>> getSortedTerms() {
        return terms.getSortedTermLimited(termMaxCount);
    }

    public StringFreqMap getTerms() {
        return terms;
    }

    public TweetDetector setTermMaxCount(int tagLimit) {
        this.termMaxCount = tagLimit;
        return this;
    }

    public StringFreqMap getLanguages() {
        return languages;
    }

    public TweetDetector run() {
        languages.clear();
        Map<String, Integer> termMap = new LinkedHashMap<String, Integer>();
        for (JTweet tweet : tweets) {
            termMap.clear();
            oneTweet(termMap, languages, tweet);

            // if one tweet has several terms 'java' increase the term only once!
            for (Entry<String, Integer> entry : termMap.entrySet()) {
                Integer integ = terms.get(entry.getKey());
                if (integ != null)
                    terms.put(entry.getKey(), integ + 1);
                else
                    terms.put(entry.getKey(), 1);
            }
        }

        return this;
    }

    public TweetDetector runOne(String text) {
        languages.clear();
        oneTweet(terms, languages, text.toLowerCase());
        return this;
    }

    private void oneTweet(Map<String, Integer> termMap, Map<String, Integer> langMap, JTweet tweet) {
        oneTweet(termMap, langMap, tweet.getText().toLowerCase());
    }

    private void oneTweet(Map<String, Integer> termMap, Map<String, Integer> langMap, String text) {
        // split against white space characters
        text = stripNoiseFromWord(text);
        String tmpTerms[] = text.split("\\s");
        int counter = 0;
        for (String term : tmpTerms) {
            counter++;
            if (term.length() < 2 || term.length() > 70 || term.startsWith("@"))
                continue;

            Set<String> detectedLangs = JTweet.LANG_DET_WORDS.get(term);
            if (langMap != null && detectedLangs != null) {
                // skip the last term for language detection
                if (counter < tmpTerms.length) {
                    for (String lang : detectedLangs) {
                        if (lang.equals(TweetDetector.NUM)
                                || lang.equals(TweetDetector.SINGLE)
                                || lang.equals(TweetDetector.MISC_LANG))
                            continue;

                        Integer integ = langMap.put(lang, 1);
                        if (integ != null)
                            langMap.put(lang, integ + 1);
                    }
                }
            }

            Set<String> noiseWordLangs = JTweet.NOISE_WORDS.get(term);
            if (termMap != null && noiseWordLangs == null) {
                Integer integ = termMap.put(term, 1);
                if (integ != null)
                    termMap.put(term, integ + 1);
            }
        }
    }
}
