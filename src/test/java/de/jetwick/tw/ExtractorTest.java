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
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class ExtractorTest {

    private String link = "http://adf.ly/2GgD";
    private Extractor extractor;

    public ExtractorTest() {
    }

    @Before
    public void setUp() {
        extractor = new Extractor();
    }

    @Test
    public void testToSaveHtml() {
        assertEquals("", extractor.toSaveHtml(""));
        assertEquals("test", extractor.toSaveHtml("test"));
        assertEquals("#", extractor.toSaveHtml("#"));
        assertEquals("http:", extractor.toSaveHtml("http:"));
        assertEquals("@", extractor.toSaveHtml("@"));
        assertEquals(Helper.toJetwickUser("@notfail", "%40notfail"), extractor.toSaveHtml("@notfail"));
        assertEquals("@ fail", extractor.toSaveHtml("@ fail"));
        assertEquals("@?", extractor.toSaveHtml("@?"));
    }

    @Test
    public void testLinkToSaveHtml() {
        assertEquals("bla : " + Helper.toLink(link, link) + " bla",
                extractor.toSaveHtml("bla : " + link + " bla"));
        link = "http://bit.ly/bgkw";
        assertEquals(Helper.toLink(link, link), extractor.toSaveHtml(link));
    }

    @Test
    public void testNoScriptInjecting() {
        assertEquals("&lt;a href=&quot;ho&quot;&gt;hi&lt;/a&gt;", extractor.toSaveHtml("<a href=\"ho\">hi</a>"));
        assertEquals("Peter Andre Confirms He&#039;s Releasing Children&#039;s Books",
                extractor.toSaveHtml("Peter Andre Confirms He's Releasing Children's Books"));
    }

    @Test
    public void testUserAndAnchorToSaveHtml() {
        assertEquals("Hi " + Helper.toJetwickUser("@timetabling", "%40timetabling") + " how are you?",
                extractor.toSaveHtml("Hi @timetabling how are you?"));

        assertEquals(" " + Helper.toJetwickUser("@timetabling", "%40timetabling") + " look here " + Helper.toLink(link, link),
                extractor.toSaveHtml(" @timetabling look here " + link));
        assertEquals(Helper.toJetwickUser("@timetabling", "%40timetabling") + ":",
                extractor.toSaveHtml("@timetabling:"));

        assertEquals(Helper.toJetwickUser("@timetabling", "%40timetabling"),
                extractor.toSaveHtml("@timetabling"));

        assertEquals("peter.k@test.de",
                extractor.toSaveHtml("peter.k@test.de"));

        assertEquals(Helper.toJetwickSearch("#java", "%23java") + " " + Helper.toLink(link, link),
                extractor.toSaveHtml("#java " + link));


        assertEquals(Helper.toJetwickSearch("#<B>java</B>", "%23java") + " " + Helper.toLink(link, link),
                extractor.toSaveHtml("#<B>java</B> " + link));
    }

//    @Test
//    public void testBolding() {
//        assertEquals("<a href=\"http://jetwick.com/?u=peter\">@peter</a>", extractor.toSaveHtml("@<B>peter</B>"));
//        assertEquals("peter_nitsch", extractor.toSaveHtml("@<B>peter</B>_nitsch"));
//        assertEquals(Helper.toJetwickSearch("notfail", "notfail"), Helper.getAnchors("#<B>notfail</B>").size());
//    }
    @Test
    public void testLinks() {
        assertTrue(extractor.toSaveHtml("http://www.<b>unitime</b>.org/").
                endsWith("href=\"http://www.unitime.org/\">http://www.<b>unitime</b>.org/</a>"));

        assertTrue(extractor.toSaveHtml("www.<b>unitime</b>.org/").
                contains("href=\"http://www.unitime.org/\">www.<b>unitime</b>.org/</a>"));

        String str = "http://j.mp/dv6kfF";
        assertEquals("Android app stealing user data<br/>" + Helper.toLink(str, str),
                extractor.toSaveHtml("Android app stealing user data\nhttp://j.mp/dv6kfF"));

        assertEquals("Android " + extractor.toLink("http://j.mp/dv6kfF", "http://j.mp/dv6kfF") + "<br/> test",
                extractor.toSaveHtml("Android http://j.mp/dv6kfF\n test"));
    }

    @Test
    public void testComplex() {
        link = "http://is.gd/bSmRb";
        assertEquals(Helper.toJetwickSearch("#jobs", "%23jobs")
                + " " + Helper.toJetwickSearch("#hiring", "%23hiring"),
                extractor.toSaveHtml("#jobs #hiring"));
    }

    @Test
    public void testNewLine() {
        assertEquals("bla<br/>bli", extractor.toSaveHtml("bla\nbli"));
    }
}
