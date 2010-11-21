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
import java.net.URLEncoder;
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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.util.StrUtils;
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
    public static final String HTTP = "http://";
    public static final String HTTPS = "https://";
    public static final String TURL = "http://twitter.com";
    public static final String JURL = "";
    public static final String UTF8 = "UTF8";
    private static final String localDateTimeFormatString = "yyyy-MM-dd'T'HH:mm:ss.000'Z'";
    private static final SimpleDateFormat sFormat = new SimpleDateFormat(localDateTimeFormatString);
    private static final String simpleDateString = "HH:mm yyyy-MM-dd";
    private static final SimpleDateFormat simpleFormat = new SimpleDateFormat(simpleDateString);

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

    public static String toLocalDateTime(Date date) {
        return sFormat.format(date);
    }

    public static String toSimpleDateTime(Date date) {
        return simpleFormat.format(date);
    }

    public static Date toDate(String createdAt) {
        try {
            return sFormat.parse(createdAt);
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }
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

    public static String toTwitterHref(String user, long id) {
        return TURL + "/" + user + "/status/" + id;
    }

    public static String toTwitterStatus(String txt) {
        return TURL + "?status=" + txt;
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

    public static String toTwitterLink(String title, String url) {
        return toLink(title, TURL + "/" + url);
    }

    public static String toJetwickUser(String title, String user) {
        return toInternLink(title, JURL + "?u=" + user);
    }

    public static String toJetwickSearch(String title, String q) {
        return toInternLink(title, JURL + "?q=" + q);
    }

    public static String toInternLink(String title, String url) {
        if (url.startsWith("www."))
            url = "http://" + url;

        return "<a class=\"i-tw-link\" href=\"" + url + "\">" + title + "</a>";
    }

    public static String toLink(String title, String url) {
        if (url.startsWith("www."))
            url = "http://" + url;

        String shortTitle = title;
        if (title.length() > 50)
            shortTitle = title.substring(0, 47) + "...";

        return "<a title=\"" + title + "\" class=\"ex-tw-link\" target=\"_blank\" href=\"" + url + "\">" + shortTitle + "</a>";
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
     * @return a sorted list where the string with the highest integer value comes first!
     */
    public static List<Entry<String, Integer>> sort(Collection<Entry<String, Integer>> entrySet) {
        List<Entry<String, Integer>> sorted = new ArrayList<Entry<String, Integer>>(entrySet);
        Collections.sort(sorted, new Comparator<Entry<String, Integer>>() {

            @Override
            public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
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
     * Returns an 'optimized'/fast HttpUrlConnection
     */
    public static HttpURLConnection getHttpURLConnection(String urlAsString) throws Exception {
        URL url = new URL(urlAsString);
        //using proxy may increase latency
        HttpURLConnection hConn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        // force no follow
        hConn.setInstanceFollowRedirects(false);
        // the program doesn't care what the content actually is !!
        // http://java.sun.com/developer/JDCTechTips/2003/tt0422.html
        hConn.setRequestMethod("HEAD");
        return hConn;
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
            HttpURLConnection hConn = getHttpURLConnection(urlAsString);
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

    /**
     * Returns title and description of a specified string (as byte array)
     */
    public static String[] getUrlInfosFromText(byte[] arr) {
        String res = new String(arr);
        int index = getStartTitleEndPos(res);
        if (index < 0)
            return new String[]{"", ""};

        int encIndex = res.indexOf("charset=");
        if (encIndex > 0) {
            int lastEncIndex = res.indexOf("\"", encIndex + 8);

            // if we have charset="something"
            if (lastEncIndex == encIndex + 8)
                lastEncIndex = res.indexOf("\"", ++encIndex + 8);

            if (lastEncIndex > encIndex + 8) {
                String encoding = res.substring(encIndex + 8, lastEncIndex);
                try {
                    res = new String(arr, encoding);
                    index = getStartTitleEndPos(res);
                    if (index < 0)
                        return new String[]{"", ""};
                } catch (Exception ex) {
                }
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
            hConn.setConnectTimeout(timeout);
            hConn.setReadTimeout(timeout);
            // default length of bufferedinputstream is 8k
            byte[] arr = new byte[8192];
            BufferedInputStream in = new BufferedInputStream(hConn.getInputStream(), arr.length);
            in.read(arr);
            return getUrlInfosFromText(arr);
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

    public static Document readUrlAsDocument(String urlAsString, int timeout) throws Exception {
        URL url = new URL(urlAsString);
        //using proxy may increase latency
        HttpURLConnection hConn = (HttpURLConnection) url.openConnection();
        hConn.setReadTimeout(timeout);
        hConn.setConnectTimeout(timeout);
        return newDocumentBuilder().parse(hConn.getInputStream());
    }

    public static String readUrl(String urlAsString, int timeout) throws IOException {
        URL url = new URL(urlAsString);
        //using proxy may increase latency
        HttpURLConnection hConn = (HttpURLConnection) url.openConnection();
        hConn.setReadTimeout(timeout);
        hConn.setConnectTimeout(timeout);
        return readInputStream(hConn.getInputStream());
    }

    public static String readInputStream(InputStream is) throws IOException {
        BufferedReader bufReader = new BufferedReader(new InputStreamReader(is, "UTF8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = bufReader.readLine()) != null) {
            sb.append(line);
            sb.append('\n');
        }
        bufReader.close();
        return sb.toString();
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
        StringBuffer sb = new StringBuffer();
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

    public static String escapeLuceneQuery(String str) {
        return ClientUtils.escapeQueryChars(str);
    }

    /**
     * copied from org/apache/solr/update/processor/SignatureUpdateProcessorFactory.java
     * @param signature
     * @return
     */
    public static String sigToString(byte[] signature) {
        char[] arr = new char[signature.length << 1];
        for (int i = 0; i < signature.length; i++) {
            int b = signature[i];
            int idx = i << 1;
            arr[idx] = StrUtils.HEX_DIGITS[(b >> 4) & 0xf];
            arr[idx + 1] = StrUtils.HEX_DIGITS[b & 0xf];
        }
        return new String(arr);
    }
}
