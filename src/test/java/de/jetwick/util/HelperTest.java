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

import java.io.IOException;
import java.util.BitSet;
import java.util.Date;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class HelperTest {

    public HelperTest() {
    }

    @Test
    public void testGetFileUnderHome() {
        assertEquals("/home/peterk/.jetwick/test", Helper.getFileUnderHome("test"));
    }

    @Test
    public void testStripOut() {
        assertEquals("hihiho", Helper.stripOutLuceneHighlighting("hihi<B>ho</B>"));
    }

    @Test
    public void testTrim() {
        assertEquals("test test", Helper.trimNL("test\ntest"));

        System.out.println(Helper.toLocalDateTime(new Date()));
    }

    @Test
    public void testDecodeHtml() {
        assertEquals("Mark Reinhold s Blog", Helper.htmlEntityDecode("Mark Reinhold&#x2019;s Blog"));
        assertEquals("Jetwick Layout Update   Find", Helper.htmlEntityDecode("Jetwick Layout Update &laquo; Find"));

        assertEquals("Mark Reinhold&#x2019", Helper.htmlEntityDecode("Mark Reinhold&#x2019"));
        assertEquals("Mark Reinhold ", Helper.htmlEntityDecode("Mark Reinhold&#x2019;"));
        assertEquals(" test", Helper.htmlEntityDecode("&#x2019;test"));
    }

    @Test
    public void testExtractDomain() {
        assertEquals("test.de", Helper.extractDomain("http://test.de"));
        assertEquals("test.de", Helper.extractDomain("http://www.test.de"));
        assertEquals("ww.test.de", Helper.extractDomain("http://ww.test.de"));
        assertEquals("wwww.test.de", Helper.extractDomain("http://wwww.test.de"));
        assertEquals("test.de", Helper.extractDomain("http://test.de/böse"));
        assertEquals("test.de", Helper.extractDomain("http://test.de/böse&blabliblup"));
        assertEquals("search.twitter.com", Helper.extractDomain("http://search.twitter.com/böse&blabliblup"));

        assertEquals("test.de", Helper.extractDomain("https://test.de/böse&blabliblup"));
        assertEquals("netbeans.org", Helper.extractDomain("http://netbeans.org/community/articles/javaone/2010/index.html"));
        assertEquals("", Helper.extractDomain("http://..."));
        assertEquals("", Helper.extractDomain("http://a.b"));
        assertEquals("a.bc", Helper.extractDomain("http://a.bc"));
    }

    @Test
    public void testGetEndPos() {
        assertEquals(9, Helper.getStartTitleEndPos("  <title> "));
        assertEquals(11, Helper.getStartTitleEndPos("  <title  > "));
        assertEquals(26, Helper.getStartTitleEndPos("\"en-us\" /><title xmlns=\"\">Abraham"));

        assertEquals(-1, Helper.getStartTitleEndPos(" nothing title > "));
    }

    @Test
    public void testUrlInfos() throws IOException {
        String res[] = Helper.getUrlInfosFromText(fileToString("h1.html"));
        assertEquals("Red Sox owner confirms he bought Liverpool club &#8211; This Just In - CNN.com Blogs",
                res[0]);
        assertEquals("Boston Red Sox Owner and Head of New England Sport Ventures, "
                + "John Henry, has confirmed that he has bought Liverpool Football Club. "
                + "His confirmation came a day after a British High Court judge ruled against "
                + "the American owners of Liverpool Football Club in their bid to stop the team's sale to ...",
                res[1]);

        res = Helper.getUrlInfosFromText(fileToString("h2.html"));
        assertEquals("WikiLeaks and 9/11: What if? - latimes.com",
                res[0]);
        assertEquals("Frustrated investigators might have chosen to leak information that their superiors bottled up, perhaps averting the terrorism attacks.",
                res[1]);
    }

    @Test
    public void testByteArray2Long() throws IOException {
        assertEquals(1 + 16 + 64, Helper.bitString2byte("1010001"));
        assertEquals("00001010", Helper.byte2bitString(Helper.bitString2byte("00001010")));
        assertEquals("00111010", Helper.byte2bitString(Helper.bitString2byte("00111010")));
        assertEquals("11111111", Helper.byte2bitString(Helper.bitString2byte("11111111")));
        assertEquals("00000000", Helper.byte2bitString(Helper.bitString2byte("00000000")));

        byte[] bytes = new byte[4];
        bytes[0] = Helper.bitString2byte("00010001");
        bytes[1] = Helper.bitString2byte("00000001");
        assertEquals(1 + 16 + 256, Helper.byteArray2long(bytes));

        bytes = new byte[4];
        bytes[0] = Helper.bitString2byte("00010001");
        bytes[1] = Helper.bitString2byte("00000001");
        bytes[2] = Helper.bitString2byte("00010010");
        assertEquals(1 + 16 + 256 + (long) Math.pow(2, 17) + (long) Math.pow(2, 20), Helper.byteArray2long(bytes));
    }

    public static byte[] fileToString(String name) throws IOException {
        return Helper.readInputStream(HelperTest.class.getResourceAsStream(name)).getBytes();
    }
}
