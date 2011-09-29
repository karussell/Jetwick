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

import de.jetwick.es.TweetQuery;
import de.jetwick.es.ElasticTweetSearch;
import java.util.Date;
import org.junit.After;
import com.google.inject.Module;
import de.jetwick.config.DefaultModule;
import de.jetwick.JetwickTestClass;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
import de.jetwick.data.UrlEntry;
import de.jetwick.es.ElasticTweetSearchTest;
import de.jetwick.snacktory.HtmlFetcher;
import de.jetwick.snacktory.JResult;
import de.jetwick.tw.UrlExtractor;
import java.io.IOException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class GenericUrlResolverTest extends JetwickTestClass {

    private GenericUrlResolver resolver;
    private ElasticTweetSearchTest twTestSearch = new ElasticTweetSearchTest();
    private ElasticTweetSearch twSearch;

    @BeforeClass
    public static void beforeClass() {
        ElasticTweetSearchTest.beforeClass();
    }

    @AfterClass
    public static void afterClass() {
        ElasticTweetSearchTest.afterClass();
    }

    @Before
    @Override
    public void setUp() throws Exception {
        twTestSearch.setUp();
        super.setUp();

        twSearch = twTestSearch.getSearch();
        resolver = getInstance(GenericUrlResolver.class);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        twTestSearch.tearDown();
    }

    @Test
    public void testResolve() throws InterruptedException {
        HtmlFetcher fetcher = new HtmlFetcher() {

            @Override
            public String getResolvedUrl(String urlAsString, int timeout) {
                return urlAsString + "_r";
            }

            @Override
            public JResult fetchAndExtract(String url, int timeout, boolean resolve) throws Exception {
                JResult res = new JResult();
                return res.setUrl(url).setTitle(url + "_t");
            }
        };
        resolver.setHtmlFetcher(fetcher);
        JTweet tw = createTweet(1L, "http://hiho.de");
        resolver.putObject(tw);
        assertNotNull(resolver.findUrlInCache("http://hiho.de"));

        assertTrue(resolver.executeResolve(0));
        twSearch.forceEmptyQueueAndRefresh(400);

        assertNotNull(twSearch.findByTwitterId(tw.getTwitterId()));
        // original url
        tw = twSearch.findByUrl("http://hiho.de").get(0);
        UrlEntry ue = tw.getUrlEntries().iterator().next();
        assertEquals("http://hiho.de", ue.getOriginalUrl(tw));
        // resolved url
        assertEquals("http://hiho.de_r", ue.getResolvedUrl());
        // fetched title
        assertEquals("http://hiho.de_r_t", ue.getResolvedTitle());
        assertNotNull(twSearch.findByUrl("http://hiho.de_r").get(0));        
    }

    @Test
    public void testResolveProblem() throws InterruptedException {
        HtmlFetcher fetcher = new HtmlFetcher() {

            @Override
            public String getResolvedUrl(String urlAsString, int timeout) {
                return urlAsString + "_r";
            }

            @Override
            public JResult fetchAndExtract(String url, int timeout, boolean resolve) throws Exception {
                throw new IOException("url does not exist");
            }
        };
        resolver.setHtmlFetcher(fetcher);
        resolver.putObject(createTweet(1L, "http://hiho.de"));
        assertEquals(1, resolver.getUnresolvedSize());
        assertNotNull(resolver.findUrlInCache("http://hiho.de"));

        assertTrue(resolver.executeResolve(0));
        twSearch.forceEmptyQueueAndRefresh();

        // feed article even if resolving makes trouble
        assertEquals(1, twSearch.findByUrl("http://hiho.de").size());
        // we have real time get => remove url from cache
        assertNull(resolver.findUrlInCache("http://hiho.de"));

        // but do not include in scan search directly after resolving
//        assertEquals(0, resolver.getCheckAgainSize());
    }

    @Test
    public void testAlreadyExistentSameId() throws InterruptedException {
        HtmlFetcher fetcher = new HtmlFetcher() {

            @Override
            public String getResolvedUrl(String urlAsString, int timeout) {
                return urlAsString + "_r";
            }

            @Override
            public JResult fetchAndExtract(String url, int timeout, boolean resolve) throws Exception {
                JResult res = new JResult();
                return res.setUrl(url).setTitle(url + "_t");
            }
        };
        resolver.setHtmlFetcher(fetcher);
        // use persistent tweet otherwise elasticTweetSearch won't update the tweet (and the retweet count)
        resolver.putObject(createTweetWithUrlEntries(1L, "http://hiho.de", 10, "http://hiho.de").makePersistent());
        assertNotNull(resolver.findUrlInCache("http://hiho.de"));

        assertEquals(0, twSearch.countAll());
        // now put an existing article into aindex so that the article with sharecount==10 wont be fetched but queued again!
        twSearch.update(Collections.singletonList(createTweetWithUrlEntries(1L, "http://hiho.de_r", 1, "http://hiho.de")), new Date(), false);
        twSearch.forceEmptyQueueAndRefresh();

        assertEquals(1, twSearch.search(new TweetQuery()).size());
        assertEquals(1, twSearch.search(new TweetQuery()).get(0).getVersion());

        assertEquals(1, twSearch.findByUrl("http://hiho.de").size());
        assertEquals(1, twSearch.findByUrl("http://hiho.de").get(0).getVersion());

        assertTrue(resolver.executeResolve(0));
        twSearch.forceEmptyQueueAndRefresh();

        assertEquals(1, twSearch.findByUrl("http://hiho.de").size());
        assertEquals(10, twSearch.findByUrl("http://hiho.de").get(0).getRetweetCount());
    }   

    @Test
    public void testALotIdenticalUrls() {
        JTweet a1 = createTweet(1L, "http://url1.de");
        JTweet a2 = createTweet(2L, "http://url1.de");
        JTweet a3 = createTweet(3L, "http://url1.de");
        resolver.putObject(a1);
        assertEquals(1, resolver.getUnresolvedSize());
        resolver.putObject(a2);
        assertEquals(1, resolver.getUnresolvedSize());

        // make article existing => skip resolving but do queue into article index
        twSearch.forceEmptyQueueAndRefresh();
        resolver.putObject(a3);
        assertEquals(0, resolver.getUnresolvedSize());
        twSearch.forceEmptyQueueAndRefresh();
    }

    JTweet createTweet(long id, String url) {
        return createTweetWithUrlEntries(id, url, 0, url).setCreatedAt(new Date());
    }

    JTweet createTweetWithUrlEntries(long id, String url, int rt, final String origUrl) {
        UrlExtractor extractor = new UrlExtractor() {

            @Override
            public JResult getInfo(String url, int timeout) throws Exception {
                return UrlEntry.createSimpleResult(origUrl);
            }
        };
        JTweet tw = new JTweet(id, "text is not important " + url, new JUser("timetabling")).setRetweetCount(rt);
        extractor.setTweet(tw);
        tw.setUrlEntries(extractor.run().getUrlEntries());        
        return tw;
    }

    @Override
    public Module createModule() {
        return new DefaultModule() {

            @Override
            public void installSearchModule() {
                bind(ElasticTweetSearch.class).toInstance(twTestSearch.getSearch());
            }
        };
    }
}
