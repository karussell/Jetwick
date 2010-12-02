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

import de.jetwick.config.Configuration;
import de.jetwick.data.UrlEntry;
import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class UrlExtractorTest {
    
    private BlockingQueue<SolrTweet> ret = new LinkedBlockingQueue<SolrTweet>();

    public UrlExtractorTest() {
    }

    @Before
    public void setUp() {
        ret.clear();
    }

    public UrlEntry getFirst(int index) {
        return get(index).iterator().next();
    }

    public Collection<UrlEntry> get(int index) {
        Iterator<SolrTweet> iter = ret.iterator();
        SolrTweet tw;
        int counter = 0;
        while (iter.hasNext()) {
            tw = iter.next();
            if (counter == index)
                return tw.getUrlEntries();
            counter++;
        }
        return null;
    }

    @Test
    public void testResolve() {
        TweetConsumer twp = createProducer();
        twp.resolveUrls(new LinkedBlockingDeque<SolrTweet>(Arrays.asList(
                createTweet(1L, "test http://hiho test2"),
                createTweet(2L, "test http://www.is.de/AShortenerDomainWouldBeWithoutWWW test2"),
                createTweet(3L, "test http://tooolong.de/test test2"),
                createTweet(4L, "http://ok-long.de/test test2"),
                createTweet(5L, "http://training-central.com "),
                createTweet(6L, "http://notval.l/"),
                createTweet(7L, "http://vali.d.le/"),
                createTweet(8L, "baöoruhasdfiuhas df aisudfh asildufhas dfasduifh http://vali.d.le/"),
                createTweet(9L, " http://url.de/1 http://url.de/2\n http://url.de/3  "))), ret, 1);

        assertEquals(1, get(0).size());
        assertEquals("http://hiho", get(0).iterator().next().getResolvedUrl());
        assertEquals(1, get(1).size());
        assertEquals(1, get(2).size());

        assertEquals(0, getFirst(3).getIndex());
        assertEquals(22, getFirst(3).getLastIndex());
        assertEquals("http://ok-long.de/test_x", getFirst(3).getResolvedUrl());
        assertEquals("ok-long.de", getFirst(3).getResolvedDomain());
        assertEquals("http://ok-long.de/test_x_t", getFirst(3).getResolvedTitle());
        assertEquals("http://vali.d.le/_x", getFirst(6).getResolvedUrl());
        assertEquals("vali.d.le", getFirst(6).getResolvedDomain());

        assertEquals("http://vali.d.le/_x", getFirst(7).getResolvedUrl());

        assertEquals(3, get(8).size());
        Iterator<UrlEntry> iter = get(8).iterator();

        assertEquals("http://url.de/1_x", iter.next().getResolvedUrl());
        assertEquals("http://url.de/2_x", iter.next().getResolvedUrl());
        assertEquals("http://url.de/3_x", iter.next().getResolvedUrl());
    }

    @Test
    public void testResolveTitle() {
        TweetConsumer twp = createProducer();

        twp.resolveUrls(new LinkedBlockingDeque<SolrTweet>(Arrays.asList(
                createTweet(1L, "test http://hiho.de test2"))), ret, 1);

        assertEquals("http://hiho.de_x", getFirst(0).getResolvedUrl());
        assertEquals("hiho.de_x", getFirst(0).getResolvedDomain());
        assertEquals("http://hiho.de_x_t", getFirst(0).getResolvedTitle());
    }

    @Test
    public void testResolveFailing() {
        TweetConsumer twp = new TweetConsumer(new Configuration()) {

            @Override
            public UrlExtractor createExtractor() {
                return new UrlExtractor() {

                    @Override
                    public String resolveOneUrl(String url, int timeout) {
                        // resolving 'failed'
                        return "";
                    }

                    @Override
                    public String[] getInfo(String url, int timeout) {
                        return new String[]{url + "_t", url + "_s"};
                    }
                };
            }
        };

        twp.resolveUrls(new LinkedBlockingDeque<SolrTweet>(Arrays.asList(
                createTweet(1L, "test http://hiho.de test2"))), ret, 1);

        assertEquals("http://hiho.de", getFirst(0).getResolvedUrl());
        assertEquals("hiho.de", getFirst(0).getResolvedDomain());
        assertEquals("http://hiho.de_t", getFirst(0).getResolvedTitle());
    }

    @Test
    public void testResolveRealTweets() {
        TweetConsumer twp = createProducer();

        twp.resolveUrls(new LinkedBlockingDeque<SolrTweet>(Arrays.asList(
                createTweet(1L, "correction ! RT @timetabling: @ptrthomas not really jetty + wicket, but nearly ;-) see http://is.gd/eoXjX and http://wp.me/p8zlh-z6"),
                createTweet(2L, "Samsung Vibrant Android Smartphone Drops To One Penny With Amazon [Shopping ...: TFTS (blog)And on... http://bit.ly/ahlIIw http://ib2.in/bB"))), ret, 1);

        assertEquals(2, get(0).size());
        Iterator<UrlEntry> iter = get(0).iterator();

        assertEquals("http://is.gd/eoXjX_x", iter.next().getResolvedUrl());
        assertEquals("http://wp.me/p8zlh-z6_x", iter.next().getResolvedUrl());

        assertEquals(2, get(1).size());
        iter = get(1).iterator();

        assertEquals("http://bit.ly/ahlIIw_x", iter.next().getResolvedUrl());
        assertEquals("http://ib2.in/bB_x", iter.next().getResolvedUrl());
    }

    TweetConsumer createProducer() {
        return new TweetConsumer(new Configuration()) {

            @Override
            public UrlExtractor createExtractor() {
                return new FakeUrlExtractor();
            }
        };
    }

    SolrTweet createTweet(long id, String twText) {
        return new SolrTweet(id, twText, new SolrUser("tmp")).setCreatedAt(new Date(id));
    }
}
