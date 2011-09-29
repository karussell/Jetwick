/*
 * Copyright 2011 Peter Karich, jetwick_@_pannous_._info.
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

import java.io.IOException;
import java.io.Reader;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.ReusableAnalyzerBase;
import org.apache.lucene.analysis.ReusableAnalyzerBase.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.util.Version;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class JetwickAnalyzer extends ReusableAnalyzerBase {

    /** 
     * Default maximum allowed token length 
     */
    public static final int DEFAULT_MAX_TOKEN_LENGTH = 255;
    private int maxTokenLength = DEFAULT_MAX_TOKEN_LENGTH;
    /**
     * Specifies whether deprecated acronyms should be replaced with HOST type.
     * See {@linkplain "https://issues.apache.org/jira/browse/LUCENE-1068"}
     */
    private final boolean replaceInvalidAcronym;
    protected final Version matchVersion;
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

    public JetwickAnalyzer() {
        matchVersion = Version.LUCENE_31;
        replaceInvalidAcronym = true;
    }

    @Override
    protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
        final StandardTokenizer src = new StandardTokenizer(matchVersion, reader);
        src.setMaxTokenLength(maxTokenLength);
        src.setReplaceInvalidAcronym(replaceInvalidAcronym);
        TokenStream tok = JetwickFilterFactory.myCreate(src, handleAsChar, handleAsDigit,
                generateWordParts, generateNumberParts,
                catenateWords, catenateNumbers, catenateAll,
                splitOnCaseChange, preserveOriginal,
                splitOnNumerics, stemEnglishPossessive, protectedWords);
        tok = new LowerCaseFilter(matchVersion, tok);
        return new TokenStreamComponents(src, tok) {

            @Override
            protected boolean reset(final Reader reader) throws IOException {
                src.setMaxTokenLength(JetwickAnalyzer.this.maxTokenLength);
                return super.reset(reader);
            }
        };
    }
}
