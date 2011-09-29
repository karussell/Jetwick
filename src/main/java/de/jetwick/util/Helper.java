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
package de.jetwick.util;

import com.google.api.translate.Language;
import com.google.api.translate.Translate;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * TODO tested through methods in YTweetTable!
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class Helper {

    private static Logger logger = LoggerFactory.getLogger(Helper.class);
    public static final String JETSLIDE_URL = "http://jetsli.de/";
    public static final String JETSLIDE_CRAWLER_URL = JETSLIDE_URL + "crawler";
    public static final String JETWICK_URL = JETSLIDE_URL + "tweets/";
    public static final String HTTP = "http://";
    public static final String HTTPS = "https://";
    public static final String TURL = "https://twitter.com";
    public static final String TSURL = "https://search.twitter.com/search?q=";
    public static final String JURL = "";
    public static final String UTF8 = "UTF8";
    public static final String ISO = "ISO-8859-1";
    // Last-Modified: Mon, 29 Jun 1998 02:28:12 GMT
    public static final String cacheDateFormatString = "EEE, dd MMM yyyy HH:mm:ss z";
    private static final String localDateTimeFormatString = "yyyy-MM-dd'T'HH:mm:ss.S'Z'";
    private static final String simpleDateString = "HH:mm yyyy-MM-dd";
    private static final String weekDayString = "EEE";
    private static final String monthDayString = "d. MMMM";
    private static final String urlDateString = "yyyy-MM-dd";
    public static int K4 = 4096;
    public static int K8 = K4 * 2;

    public static String getWeekDay(Date date) {
        DateFormat df = new SimpleDateFormat(weekDayString, Locale.UK);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(date);
    }

    public static String getMonthDay(Date date) {
        DateFormat df = new SimpleDateFormat(monthDayString, Locale.UK);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(date);
    }

    public static String toSimpleDateTime(Date date) {
        DateFormat df = new SimpleDateFormat(simpleDateString, Locale.UK);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(date);
    }

    public static DateFormat createLocalDateFormat() {
        DateFormat df = new SimpleDateFormat(localDateTimeFormatString);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df;
    }

    public static DateFormat createUrlDateFormat() {
        DateFormat df = new SimpleDateFormat(urlDateString);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df;
    }

    public static String toLocalDateTime(Date date) {
        return createLocalDateFormat().format(date);
    }

    public static Date toDate(String createdAt) {
        try {
            return createLocalDateFormat().parse(createdAt);
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static BufferedReader createBuffReader(File file) throws FileNotFoundException, UnsupportedEncodingException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
    }

    public static BufferedReader createBuffReader(InputStream is) throws FileNotFoundException, UnsupportedEncodingException {
        return new BufferedReader(new InputStreamReader(is, "UTF-8"));
    }

    /**
     * Read a file from classpath
     */
    public static BufferedReader createBuffReaderCP(String file) throws UnsupportedEncodingException {
        return new BufferedReader(new InputStreamReader(Helper.class.getResourceAsStream(file), "UTF-8"));
    }

    public static BufferedWriter createBuffWriter(File file) throws FileNotFoundException, UnsupportedEncodingException {
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
    }

    public static List<String> readFile(String file) throws IOException {
        return readFile(new InputStreamReader(new FileInputStream(file), UTF8));
    }

    public static List<String> readFile(Reader simpleReader) throws IOException {
        BufferedReader reader = new BufferedReader(simpleReader);
        List<String> res = new ArrayList();
        String line = null;
        while ((line = reader.readLine()) != null) {
            res.add(line);
        }
        reader.close();
        return res;
    }

    /**
     * Liefert einen DOM Parser zurück.
     */
    public static DocumentBuilder newDocumentBuilder()
            throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        // ist das langsammer: factory.setValidating(true);
        factory.setValidating(false);
        factory.setNamespaceAware(false);

        DocumentBuilder builder = factory.newDocumentBuilder();

        return builder;
    }

    public static Document getAsDocument(String xmlString) throws SAXException,
            IOException, ParserConfigurationException {
        return newDocumentBuilder().parse(
                new ByteArrayInputStream(xmlString.getBytes()));
    }

    public static String getDocumentAsString(Node node, boolean prettyXml)
            throws TransformerException, UnsupportedEncodingException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();

        if (prettyXml) {
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        }
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(node), new StreamResult(baos));

        return baos.toString("UTF-8");
    }

    public static Document getUrlAsDocument(String urlAsString, int timeout) throws Exception {
        URL url = new URL(urlAsString);
        //using proxy may increase latency
        HttpURLConnection hConn = (HttpURLConnection) url.openConnection();
        hConn.setReadTimeout(timeout);
        hConn.setConnectTimeout(timeout);
//        hConn.setRequestProperty("Accept-Encoding", "gzip, deflate");

        InputStream is = hConn.getInputStream();
//        if ("gzip".equals(hConn.getContentEncoding()))
//            is = new GZIPInputStream(is);
        return newDocumentBuilder().parse(is);
    }

    public static String getInputStream(InputStream is) throws IOException {
        if (is == null)
            throw new IllegalArgumentException("stream mustn't be null!");

        BufferedReader bufReader = createBuffReader(is);
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = bufReader.readLine()) != null) {
            sb.append(line);
            sb.append('\n');
        }
        bufReader.close();
        return sb.toString();
    }

    public static void removeDuplicates(List list) {
        Set set = new LinkedHashSet(list);
        list.clear();
        list.addAll(set);
    }

    public static String getFileUnderHome(String str) {
        char c = File.separatorChar;
        String appHome = System.getProperty("user.home") + c + ".jetwick";
        File f = new File(appHome);
        if (!f.exists())
            f.mkdir();

        return appHome + c + str;
    }

    public static Date toDateNoNPE(String string) {
        if (string == null)
            return null;
        return toDate(string);
    }

    public static String stripOutLuceneHighlighting(String str) {
        str = str.replaceAll("<B>", "");
        str = str.replaceAll("</B>", "");
        str = str.replaceAll("<b>", "");
        str = str.replaceAll("</b>", "");
        return str;
    }

    public static String fixForUserInput(String str) {
        str = str.replaceAll("\\@<B>", "<B>@");
        return str;
    }

    public static String twitterIntentReply(long id) {
        return TURL + "/intent/tweet?in_reply_to=" + id;
    }

    public static String twitterIntentRetweet(long id) {
        return TURL + "/intent/retweet?tweet_id=" + id;
    }

    public static String twitterIntentFav(long id) {
        return TURL + "/intent/favorite?tweet_id=" + id;
    }

    public static String toTwitterHref(String user, long id) {
        return TURL + "/" + user + "/status/" + id;
    }

    public static String toTwitterStatus(String txt) {
        return TURL + "?status=" + txt;
    }

    public static String getTwitterHref(String title, String url, String afterUrl) {
        if (!Helper.isEmpty(afterUrl))
            afterUrl = " " + afterUrl;

        if (title.length() > 95)
            title = title.substring(0, 95) + "..";

        String text = title + " " + url + afterUrl;
        return Helper.toTwitterStatus(Helper.twitterUrlEncode(text));
    }

    public static String toReplyHref(String user, Long tweetId) {
        return toReplyStatusHref("@" + user + " ", user, tweetId, false);
    }

    public static String toReplyStatusHref(String status, String user, Long tweetId, boolean encode) {
        if (encode)
            status = twitterUrlEncode(status);

        String str = TURL + "?status=" + status;
        if (tweetId != null)
            str += "&in_reply_to_status_id=" + tweetId;
        if (user != null)
            str += "&in_reply_to=" + user;

        return str;
    }

    public static String toFacebookHref(String url, String title) {
        return "http://www.facebook.com/sharer.php?u=" + urlEncode(url) + "&t=" + urlEncode(title);
    }

    public static String toReadItLaterHref(String url, String title) {
        return "https://readitlaterlist.com/save?url=" + urlEncode(url) + "&title=" + urlEncode(title);
    }

    public static String toEmailHref(String email, String subject, String body) {
        body = body.replaceAll("\n", "%0D%0A");
        return "mailto:" + email + "?subject=" + subject + "&body=" + body;
    }

    public static String toGoogleTranslateHref(String url, String from, String to) {
        return "http://translate.google.com/translate?sl=" + from + "&tl=" + to + "&u=" + urlEncode(url);
    }

    public static String toJetwickUser(String title, String user) {
        return toInternLink(title, JURL + "?search=user&user=" + user);
    }

    public static String toJetwickSearch(String title, String q) {
        return toInternLink(title, JURL + "?q=" + q);
    }

    public static String toInternLink(String title, String url) {
        if (url.startsWith("www."))
            url = "http://" + url;

        return "<a class=\"i-tw-link\" href=\"" + url + "\">" + title + "</a>";
    }

    public static Date plusDays(Date date, int days) {
        return new Date(date.getTime() + days * 24 * 3600 * 1000);
    }

    public static Map<String, String> parseArguments(String[] args) {
        Map<String, String> map = new LinkedHashMap<String, String>();

        for (String arg : args) {
            String strs[] = arg.split("\\=");
            if (strs.length != 2)
                continue;

            String key = strs[0];
            if (key.startsWith("-")) {
                key = key.substring(1);
            }
            if (key.startsWith("-")) {
                key = key.substring(1);
            }
            String value = strs[1];
            map.put(key, value);
        }

        return map;
    }

    public static String urlEncode(String str) {
        try {
            return URLEncoder.encode(str, UTF8);
        } catch (UnsupportedEncodingException ex) {
            return str;
        }
    }

    public static String urlDecode(String str) {
        try {
            return URLDecoder.decode(str, UTF8);
        } catch (UnsupportedEncodingException ex) {
            return str;
        }
    }

    /**
     * encode space not as +
     */
    public static String twitterUrlEncode(String str) {
        return urlEncode(str).replaceAll("\\+", "%20");
    }

    /**
     * Skip characters which are not allowed in xml.
     * 
     * Taken from
     * http://stackoverflow.com/questions/20762/how-do-you-remove-invalid-hexadecimal-characters-from-an-xml-based-data-source-pr
     */
    public static String xmlCharacterWhitelist(String inputStr) {
        if (inputStr == null)
            return null;

        StringBuilder sbOutput = new StringBuilder();
        char ch;

        for (int i = 0; i < inputStr.length(); i++) {
            ch = inputStr.charAt(i);
            if ((ch >= 0x0020 && ch <= 0xD7FF)
                    || (ch >= 0xE000 && ch <= 0xFFFD)
                    || ch == 0x0009
                    || ch == 0x000A
                    || ch == 0x000D) {
                sbOutput.append(ch);
            }
        }
        return sbOutput.toString();
    }

    /**
     * @return a sorted list where the object with the highest integer value comes first!
     */
    public static <T> List<Entry<T, Integer>> sort(Collection<Entry<T, Integer>> entrySet) {
        List<Entry<T, Integer>> sorted = new ArrayList<Entry<T, Integer>>(entrySet);
        Collections.sort(sorted, new Comparator<Entry<T, Integer>>() {

            @Override
            public int compare(Entry<T, Integer> o1, Entry<T, Integer> o2) {
                int i1 = o1.getValue();
                int i2 = o2.getValue();
                if (i1 < i2)
                    return 1;
                else if (i1 > i2)
                    return -1;
                else
                    return 0;
            }
        });

        return sorted;
    }

    /**
     * @return a sorted list where the string with the highest integer value comes first!
     */
    public static <T> List<Entry<T, Long>> sortLong(Collection<Entry<T, Long>> entrySet) {
        List<Entry<T, Long>> sorted = new ArrayList<Entry<T, Long>>(entrySet);
        Collections.sort(sorted, new Comparator<Entry<T, Long>>() {

            @Override
            public int compare(Entry<T, Long> o1, Entry<T, Long> o2) {
                long i1 = o1.getValue();
                long i2 = o2.getValue();
                if (i1 < i2)
                    return 1;
                else if (i1 > i2)
                    return -1;
                else
                    return 0;
            }
        });

        return sorted;
    }

    /**
     * The specified list will be sorted where then the small numbers comes first
     */
    public static <T> void sortInplaceLongReverse(List<Entry<T, Long>> entrySet) {
        Collections.sort(entrySet, new Comparator<Entry<T, Long>>() {

            @Override
            public int compare(Entry<T, Long> o1, Entry<T, Long> o2) {
                long i1 = o1.getValue();
                long i2 = o2.getValue();
                if (i1 < i2)
                    return -1;
                else if (i1 > i2)
                    return 1;
                else
                    return 0;
            }
        });
    }

    public static String extractDomain(String url) {
        // url shorteners seems to have a "domain.de" shorter or equal to 11
        // the longest was tinyurl.com the shortest is t.co
        if (url.startsWith(HTTP))
            url = url.substring(HTTP.length());
        if (url.startsWith(HTTPS))
            url = url.substring(HTTPS.length());

        int index = url.indexOf("/");
        if (index < 0)
            index = Math.max(url.length(), url.indexOf(" "));

        String domain = url.substring(0, index);
        if (domain.startsWith("www."))
            domain = domain.substring(4);

        // skip if the domain of domain.de is of zero length or if the "de" is less then 2 chars
        index = domain.indexOf(".");
        if (index < 0 || domain.length() < 4)
            return "";

        return domain;
    }

    /**
     * On some devices we have to hack:
     * http://developers.sun.com/mobility/reference/techart/design_guidelines/http_redirection.html
     * @return the resolved url if any. Or null if it couldn't resolve the url
     * (within the specified time) or the same url if response code is OK
     */
    public static String getResolvedUrl(String urlAsString, int timeout) {
        try {
            URL url = new URL(urlAsString);
            //using proxy may increase latency
            HttpURLConnection hConn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            // force no follow

            hConn.setInstanceFollowRedirects(false);
            // the program doesn't care what the content actually is !!
            // http://java.sun.com/developer/JDCTechTips/2003/tt0422.html
            hConn.setRequestMethod("HEAD");
            // default is 0 => infinity waiting
            hConn.setConnectTimeout(timeout);
            hConn.setReadTimeout(timeout);
            hConn.connect();
            int responseCode = hConn.getResponseCode();
            hConn.getInputStream().close();
            if (responseCode == HttpURLConnection.HTTP_OK)
                return urlAsString;

            String loc = hConn.getHeaderField("Location");
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM && loc != null)
                return loc.replaceAll(" ", "+");

        } catch (Exception ex) {
        }
        return "";
    }
    final static String DESCRIPTION = "<meta name=\"description\" content=\"";
    final static String DESCRIPTION2 = "<meta name=\"Description\" content=\"";

    public static String extractEncoding(String contentType) {
        String[] values = contentType.split(";");
        String charset = "";

        for (String value : values) {
            value = value.trim().toLowerCase();

            if (value.startsWith("charset="))
                charset = value.substring("charset=".length());
        }

        // http1.1 says ISO-8859-1 is the default charset
        if (charset.length() == 0)
            charset = ISO;

        return charset;
    }

    /**
     * Returns title and description of a specified string (as byte array)
     */
    public static String[] getUrlInfosFromText(byte[] arr, String contentType) {
        String res;
        try {
            res = new String(arr, extractEncoding(contentType));
        } catch (Exception ex) {
            res = new String(arr);
        }

        int index = getStartTitleEndPos(res);
        if (index < 0)
            return new String[]{"", ""};

        int encIndex = res.indexOf("charset=");
        if (encIndex > 0) {
            int lastEncIndex = res.indexOf("\"", encIndex + 8);

            // if we have charset="something"
            if (lastEncIndex == encIndex + 8)
                lastEncIndex = res.indexOf("\"", ++encIndex + 8);

            // re-read byte array with different encoding
            if (lastEncIndex > encIndex + 8) {
                try {
                    String encoding = res.substring(encIndex + 8, lastEncIndex);
                    res = new String(arr, encoding);
                } catch (Exception ex) {
                }
                index = getStartTitleEndPos(res);
                if (index < 0)
                    return new String[]{"", ""};
            }
        }

        int lastIndex = res.indexOf("</title>");
        if (lastIndex <= index)
            return new String[]{"", ""};

        String title = res.substring(index, lastIndex);
        index = res.indexOf(DESCRIPTION);
        if (index < 0)
            index = res.indexOf(DESCRIPTION2);

        lastIndex = res.indexOf("\"", index + DESCRIPTION.length());
        if (index < 0 || lastIndex < 0)
            return new String[]{title, ""};

        index += DESCRIPTION.length();
        return new String[]{title, res.substring(index, lastIndex)};
    }

    public static String[] getUrlInfos(String urlAsString, int timeout) {
        try {
            URL url = new URL(urlAsString);
            //using proxy may increase latency
            HttpURLConnection hConn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            hConn.setRequestProperty("User-Agent", "Mozilla/5.0 Gecko/20100915 Firefox/3.6.10");

            // on android we got problems because of this
            // so disable that for now
//            hConn.setRequestProperty("Accept-Encoding", "gzip, deflate");
            hConn.setConnectTimeout(timeout);
            hConn.setReadTimeout(timeout);
            // default length of bufferedinputstream is 8k
            byte[] arr = new byte[K4];
            InputStream is = hConn.getInputStream();

            if ("gzip".equals(hConn.getContentEncoding()))
                is = new GZIPInputStream(is);

            BufferedInputStream in = new BufferedInputStream(is, arr.length);
            in.read(arr);

            return getUrlInfosFromText(arr, hConn.getContentType());
        } catch (Exception ex) {
        }
        return new String[]{"", ""};
    }

    /**
     * @return tries to get the title of the specified url. returns an empty string
     * if this failed
     */
    public static String getUrlTitle(String urlAsString, int timeout) {
        return getUrlInfos(urlAsString, timeout)[0];
    }

    public static int getStartTitleEndPos(String res) {
        int index = res.indexOf("<title>");
        if (index < 0) {
            index = res.indexOf("<title ");
            if (index < 0)
                return -1;

            index = res.indexOf(">", index);
            if (index >= 0)
                index++;
        } else
            index += "<title>".length();

        return index;
    }

    /**
     * Returns the given string with consecutive whitespace characters
     * replaced with a single space and then trimmed
     * @see http://www.rgagnon.com/javadetails/java-0352.html
     */
    public static String trimAll(String str) {
        return str.replaceAll("\\s+", " ").trim();
    }

    /**
     * removes new lines
     * @return
     */
    public static String trimNL(String str) {
        return str.replaceAll("\n", " ");
    }

    /**
     * the following method was taken from suns Decoder and stands under CDDL
     * 
     * look here for a converter:
     * http://gmailassistant.sourceforge.net/src/org/freeshell/zs/common/HtmlManipulator.java.html
     */
    public static String htmlEntityDecode(String s) {
        int i = 0, j = 0, pos = 0;
        StringBuilder sb = new StringBuilder();
        while ((i = s.indexOf("&", pos)) != -1 && (j = s.indexOf(';', i)) != -1) {
            int n = -1;
            for (i += 1; i < j; ++i) {
                char c = s.charAt(i);
                if ('0' <= c && c <= '9')
                    n = (n == -1 ? 0 : n * 10) + c - '0';
                else
                    break;
            }

            // skip malformed html entities
            if (i != j)
                n = -1;

            if (n != -1) {
                sb.append((char) n);
            } else {
                // force deletion of chars                
                for (int k = pos; k < i - 1; ++k) {
                    sb.append(s.charAt(k));
                }
                sb.append(" ");
            }
            // skip ';'
            i = j + 1;
            pos = i;
        }
        if (sb.length() == 0)
            return s;
        else
            sb.append(s.substring(pos, s.length()));
        return sb.toString();

    }

    /**
     * @see http://blogs.sun.com/CoreJavaTechTips/entry/cookie_handling_in_java_se
     */
    public static void enableCookieMgmt() {
        CookieManager manager = new CookieManager();
        manager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(manager);
    }

    /**
     * @see http://stackoverflow.com/questions/2529682/setting-user-agent-of-a-java-urlconnection
     */
    public static void enableUserAgentOverwrite() {
        System.setProperty("http.agent", "");
    }

    public static String translate(String txt, Language fromLanguage, Language toLanguage) throws Exception {
        Translate.setHttpReferrer(JETWICK_URL);

        txt = workAroundBefore(txt);
        try {
            txt = Translate.execute(txt, fromLanguage, toLanguage);
        } catch (Exception ex) {
            // if language detection fails
            txt = Translate.execute(txt, Language.ENGLISH, toLanguage);
        }
        return workAroundAfter(txt);
    }

    public static String[] translateAll(String[] texts, Language[] froms, Language[] tos) throws Exception {
        // the following is faster but often fails in Translate.execute:
        // retrieveJSON(url, parametersBuilder.toString()).getJSONArray("responseData");

//        Translate.setHttpReferrer(JETWICK_URL);
//        for (int i = 0; i < texts.length; i++) {
//            texts[i] = workAroundBefore(texts[i]);
//        }
//        String[] res = Translate.execute(texts, froms, tos);
//        for (int i = 0; i < res.length; i++) {
//            res[i] = workAroundAfter(res[i]);
//        }
        String res[] = new String[texts.length];
        for (int i = 0; i < texts.length; i++) {
            try {
                res[i] = translate(texts[i], froms[i], tos[i]);
            } catch (Exception ex) {
                logger.warn("Cannot translate:" + texts[i] + " " + ex.getMessage());
            }
        }

        return res;
    }

    /**
     * workaround for http://groups.google.com/group/google-translate-general/browse_thread/thread/8cdc2b71f5213cf7
     */
    private static String workAroundBefore(String origText) {
        if (origText.contains("# ") || origText.contains("@ "))
            return origText;
        else
            // use letters so that google thinks the word after the '#' is related to those letters and won't translate or mix it up.
            return origText.replaceAll("#", "XbllsHYBoPll").replaceAll("@", "XallsHYBoPll");
    }

    private static String workAroundAfter(String origText) {
        return origText.replaceAll("XbllsHYBoPll", "#").replaceAll("XallsHYBoPll", "@");
    }

    public static byte bitString2byte(String str) {
        int res = 0;

        if (str.length() > 8)
            throw new UnsupportedOperationException("string length may be max 8");

        for (int i = 0; i < str.length(); i++) {
            res = res << 1;

            if ('1' == str.charAt(i)) {
                res |= 1;
            } else if ('0' == str.charAt(i)) {
            } else
                throw new UnsupportedOperationException("string may contain only 1 or 0");
        }

        return (byte) res;
    }

    public static String byte2bitString(byte b) {
        int integ = b;
        String res = "";
        for (int j = 0; j < 8; j++) {
            if ((integ & 0x01) == 1)
                res = "1" + res;
            else
                res = "0" + res;

            integ = integ >> 1;
        }

        return res;
    }

    public static long byteArray2long(byte[] signature) {
        if (signature.length > 8)
            throw new UnsupportedOperationException("Cannot lossless convert byte array into long if length is greater than 8");

        long val = 0;
        for (int i = signature.length - 1; i >= 0; i--) {
            val = val << 8;
            val |= signature[i];
        }

        return val;
    }

//    /**
//     * copied from org/apache/solr/update/processor/SignatureUpdateProcessorFactory.java
//     * @param signature
//     * @return
//     */
//    public static String sigToString(byte[] signature) {
//        char[] arr = new char[signature.length << 1];
//        for (int i = 0; i < signature.length; i++) {
//            int b = signature[i];
//            int idx = i << 1;
//            arr[idx] = StrUtils.HEX_DIGITS[(b >> 4) & 0xf];
//            arr[idx + 1] = StrUtils.HEX_DIGITS[b & 0xf];
//        }
//        return new String(arr);
//    }
    public static String[] toStringArray(Collection<String> coll) {
        return coll.toArray(new String[coll.size()]);
    }

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static String getTwitterUserFromUrl(String sourceUrl) {
        String user = sourceUrl.replaceFirst("#!/", "");
        int index = user.indexOf(".com/");
        if (index > 0) {
            index += ".com/".length();
            int index2 = user.indexOf("/", index);
            if (index2 < 0)
                index2 = user.length();

            user = user.substring(index, index2);
        }
        return user.toLowerCase();
    }

    public static Long getTwitterIdFromUrl(String sourceUrl) {
        String id = sourceUrl.replaceFirst("#!/", "");
        int index = id.lastIndexOf("/");
        if (index > 0) {
            index++;
            try {
                return Long.parseLong(id.substring(index));
            } catch (NumberFormatException ex) {
            }
        }
        return null;
    }

    public static String getMsg(Exception ex) {
        if (ex == null)
            return "null";
        else if (ex.getMessage() == null)
            return "null, " + ex.getClass().getSimpleName();

        if (ex.getMessage().length() > 100)
            return ex.getMessage().substring(0, 100) + "...";
        return ex.getMessage();
    }

    // http://is.gd/apishorteningreference.php
    public static String createShortUrl(String urlStr) throws Exception {
        URL url = new URL("http://is.gd/create.php?format=simple&url=" + urlStr);
        return createBuffReader(url.openStream()).readLine();
    }

    public static String stripControlChars(String iString) {
        StringBuilder result = new StringBuilder(iString);
        int idx = result.length();
        while (idx-- > 0) {
            if (result.charAt(idx) < 0x20 && result.charAt(idx) != 0x9
                    && result.charAt(idx) != 0xA && result.charAt(idx) != 0xD)
                result.deleteCharAt(idx);
        }
        return result.toString();
    }

    public static int countChars(String term, char mychar) {
        int l = term.length();
        int counter = 0;
        for (int i = 0; i < l; i++) {
            if (term.charAt(i) == mychar)
                counter++;
        }
        return counter;
    }
}
