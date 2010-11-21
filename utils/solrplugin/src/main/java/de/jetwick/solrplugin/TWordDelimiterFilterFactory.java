package de.jetwick.solrplugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.solr.analysis.BaseTokenFilterFactory;
import org.apache.solr.common.ResourceLoader;
import org.apache.solr.common.util.StrUtils;
import org.apache.solr.util.plugin.ResourceLoaderAware;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TWordDelimiterFilterFactory extends BaseTokenFilterFactory implements ResourceLoaderAware {

    public static final String PROTECTED_TOKENS = "protected";

    public void inform(ResourceLoader loader) {
        String wordFiles = args.get(PROTECTED_TOKENS);
        if (wordFiles != null) {
            try {
                File protectedWordFiles = new File(wordFiles);
                if (protectedWordFiles.exists()) {
                    List<String> wlist = loader.getLines(wordFiles);
                    //This cast is safe in Lucene
                    protectedWords = new CharArraySet(wlist, false);//No need to go through StopFilter as before, since it just uses a List internally
                } else {
                    List<String> files = StrUtils.splitFileNames(wordFiles);
                    for (String file : files) {
                        List<String> wlist = loader.getLines(file.trim());
                        if (protectedWords == null)
                            protectedWords = new CharArraySet(wlist, false);
                        else
                            protectedWords.addAll(wlist);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    private CharArraySet protectedWords = null;
    int generateWordParts = 0;
    int generateNumberParts = 0;
    int catenateWords = 0;
    int catenateNumbers = 0;
    int catenateAll = 0;
    int splitOnCaseChange = 0;
    int splitOnNumerics = 0;
    int preserveOriginal = 0;
    int stemEnglishPossessive = 0;
    String handleAsChar = "";
    String handleAsDigit = "";

    @Override
    public void init(Map<String, String> args) {
        super.init(args);
        generateWordParts = getInt("generateWordParts", 1);
        generateNumberParts = getInt("generateNumberParts", 1);
        catenateWords = getInt("catenateWords", 0);
        catenateNumbers = getInt("catenateNumbers", 0);
        catenateAll = getInt("catenateAll", 0);
        splitOnCaseChange = getInt("splitOnCaseChange", 1);
        splitOnNumerics = getInt("splitOnNumerics", 1);
        preserveOriginal = getInt("preserveOriginal", 0);
        stemEnglishPossessive = getInt("stemEnglishPossessive", 1);
        handleAsChar = getArgs().get("handleAsChar");
        if (handleAsChar == null)
            handleAsChar = "";

        handleAsDigit = getArgs().get("handleAsDigit");
        if (handleAsDigit == null)
            handleAsDigit = "";
    }

    public TWordDelimiterFilter create(TokenStream input) {
        byte[] tab = new byte[256];
        for (int i = 0; i < 256; i++) {
            byte code = 0;

            if (Character.isLowerCase(i) || handleAsChar.contains(String.valueOf((char) i))) {
                code |= TWordDelimiterFilter.LOWER;
            } else if (Character.isUpperCase(i)) {
                code |= TWordDelimiterFilter.UPPER;
            } else if (Character.isDigit(i) || handleAsDigit.contains(String.valueOf((char) i))) {
                code |= TWordDelimiterFilter.DIGIT;
            }
            if (code == 0) {
                code = TWordDelimiterFilter.SUBWORD_DELIM;
            }
            tab[i] = code;
        }

        return new TWordDelimiterFilter(input, tab,
                generateWordParts, generateNumberParts,
                catenateWords, catenateNumbers, catenateAll,
                splitOnCaseChange, preserveOriginal,
                splitOnNumerics, stemEnglishPossessive, protectedWords);
    }
}
