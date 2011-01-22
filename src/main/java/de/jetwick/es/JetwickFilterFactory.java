/*
 * Copyright 2011 Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetwick.es;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.solr.analysis.WordDelimiterFilter;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;
import org.elasticsearch.index.settings.IndexSettings;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class JetwickFilterFactory extends AbstractTokenFilterFactory {

    @Inject
    public JetwickFilterFactory(Index index, @IndexSettings Settings indexSettings, @Assisted String name, @Assisted Settings settings) {
        super(index, indexSettings, name);
    }
    private CharArraySet protectedWords = null;
    private int generateWordParts = 1;
    private int generateNumberParts = 1;
    private int catenateWords = 0;
    private int catenateNumbers = 0;
    private int catenateAll = 0;
    private int splitOnCaseChange = 0;
    private int splitOnNumerics = 1;
    private int preserveOriginal = 1;
    private int stemEnglishPossessive = 0;
    private String handleAsChar = "";
    private String handleAsDigit = "@#$€₱č₤";

    @Override
    public TokenStream create(TokenStream tokenStream) {
        byte[] tab = new byte[256];
        for (int i = 0; i < 256; i++) {
            byte code = 0;

            if (Character.isLowerCase(i) || handleAsChar.contains(String.valueOf((char) i))) {
                code |= WordDelimiterFilter.LOWER;
            } else if (Character.isUpperCase(i)) {
                code |= WordDelimiterFilter.UPPER;
            } else if (Character.isDigit(i) || handleAsDigit.contains(String.valueOf((char) i))) {
                code |= WordDelimiterFilter.DIGIT;
            }
            if (code == 0) {
                code = WordDelimiterFilter.SUBWORD_DELIM;
            }
            tab[i] = code;
        }

        return new WordDelimiterFilter(tokenStream, tab,
                generateWordParts, generateNumberParts,
                catenateWords, catenateNumbers, catenateAll,
                splitOnCaseChange, preserveOriginal,
                splitOnNumerics, stemEnglishPossessive, protectedWords);
    }

    @Override
    public String name() {
        return "jetwickfilter";
    }
}
