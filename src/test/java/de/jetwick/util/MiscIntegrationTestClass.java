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
import de.jetwick.wikipedia.WikiEntry;
import de.jetwick.wikipedia.Wikipedia;
import java.util.Collection;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class MiscIntegrationTestClass {

    @Test
    public void testResolve() {
        String url = "http://bit.ly/dipo5a";
        String newUrl = Helper.getResolvedUrl(url, 900);
        assertTrue(url.length() < newUrl.length());

        url = "http://is.gd/en49t";
        newUrl = Helper.getResolvedUrl(url, 900);
        assertTrue(url.length() < newUrl.length());

        url = "http://bit.ly/aowRP7";
        newUrl = Helper.getResolvedUrl(url, 900);
        assertTrue(url.length() < newUrl.length());

        assertFalse(Helper.getResolvedUrl("http://bit.ly/bGsbxa", 1000).contains(" "));

        // does not work??
        Helper.getResolvedUrl("http://tumblr.com/xrwg2fwzl", 5000);

        Helper.getResolvedUrl("http://bit.ly/9MBBhW", 5000);
    }

    @Test
    public void testGetUrlTitle() {
        assertEquals("Google", Helper.getUrlTitle("http://google.de", 1000));

        // encoding is now windows-1251
        assertEquals("Фильмы скачать бесплатно, новинки игр, качественный софт, музыка на Tamross.ru",
                Helper.getUrlTitle("http://tamross.ru/videoyroki/31817-java-dlya-professionalov-obuchayushhij-videokurs.html", 4000));

        assertEquals("Vaadin Sampler", Helper.getUrlTitle("http://demo.vaadin.com/sampler", 1000));

        // too slow sometimes ;-)
        //assertEquals("Twitter Search Jetwick", Helper.getUrlTitle("http://jetwick.com", 2000));
//        assertEquals("java　初心者　勉強中 - ニコニコ生放送", Helper.getUrlTitle("http://live.nicovideo.jp/watch/lv27534029", 4000));

        Helper.enableUserAgentOverwrite();
        // the following site has a unique title (which is not good) for its unknown agents (== mobile)!?
        Helper.getUrlTitle("http://bit.ly/alKNQI", 5000);

        Helper.enableCookieMgmt();
        // the following site directs to login (als a unique title)
        Helper.getUrlTitle("http://ow.ly/2LbUA", 2000);
    }

    @Test
    public void testTranslate() throws Exception {
        Translate.setHttpReferrer("http://jetwick.com");
        System.out.println(Translate.execute("Estoy listo para seguir discutiendo http://www.cubadebate.cu/reflexiones-fidel/2010... "
                + "#USA #Iran #Cuba #EEUU #Obama #FidelCastro",
                Language.AUTO_DETECT, Language.fromString("de")));
    }

    @Test
    public void testQuery() {
        Collection<WikiEntry> list = new Wikipedia().query("wicket", "de", 5);
        assertEquals(5, list.size());
        for (WikiEntry e : list) {
            System.out.println(e);
        }
    }
}
