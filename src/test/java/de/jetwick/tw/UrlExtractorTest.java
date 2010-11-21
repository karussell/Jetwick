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

import de.jetwick.data.UrlEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import org.junit.Before;
import org.junit.Test;
import twitter4j.Tweet;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class UrlExtractorTest {

    private List<Tweet> ret = new ArrayList<Tweet>();

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
        return ((Twitter4JTweet) ret.get(index)).getUrlEntries();
    }

    @Test
    public void testResolve() {
        TweetProducer twp = createProducer();
        twp.resolveUrls(new LinkedBlockingDeque<Tweet>(Arrays.asList(
                new Twitter4JTweet(1L, "test http://hiho test2", "peter"),
                new Twitter4JTweet(2L, "test http://www.is.de/AShortenerDomainWouldBeWithoutWWW test2", "peter"),
                new Twitter4JTweet(3L, "test http://tooolong.de/test test2", "peter"),
                new Twitter4JTweet(4L, "http://ok-long.de/test test2", "peter"),
                new Twitter4JTweet(5L, "http://training-central.com ", "peter"),
                new Twitter4JTweet(6L, "http://notval.l/", "peter"),
                new Twitter4JTweet(7L, "http://vali.d.le/", "peter"),
                new Twitter4JTweet(8L, "baöoruhasdfiuhas df aisudfh asildufhas dfasduifh http://vali.d.le/", "peter"),
                new Twitter4JTweet(9L, " http://url.de/1 http://url.de/2\n http://url.de/3  ", "peter"))), ret, 1);

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
        TweetProducer twp = createProducer();

        twp.resolveUrls(new LinkedBlockingDeque<Tweet>(Arrays.asList(
                new Twitter4JTweet(1L, "test http://hiho.de test2", "peter"))), ret, 1);

        assertEquals("http://hiho.de_x", getFirst(0).getResolvedUrl());
        assertEquals("hiho.de_x", getFirst(0).getResolvedDomain());
        assertEquals("http://hiho.de_x_t", getFirst(0).getResolvedTitle());
    }

    @Test
    public void testResolveFailing() {
        TweetProducer twp = new TweetProducer() {

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

        twp.resolveUrls(new LinkedBlockingDeque<Tweet>(Arrays.asList(
                new Twitter4JTweet(1L, "test http://hiho.de test2", "peter"))), ret, 1);

        assertEquals("http://hiho.de", getFirst(0).getResolvedUrl());
        assertEquals("hiho.de", getFirst(0).getResolvedDomain());
        assertEquals("http://hiho.de_t", getFirst(0).getResolvedTitle());
    }

    @Test
    public void testResolveRealTweets() {
        TweetProducer twp = createProducer();

        twp.resolveUrls(new LinkedBlockingDeque<Tweet>(Arrays.asList(
                new Twitter4JTweet(1L, "correction ! RT @timetabling: @ptrthomas not really jetty + wicket, but nearly ;-) see http://is.gd/eoXjX and http://wp.me/p8zlh-z6", "peter"),
                new Twitter4JTweet(2L, "Samsung Vibrant Android Smartphone Drops To One Penny With Amazon [Shopping ...: TFTS (blog)And on... http://bit.ly/ahlIIw http://ib2.in/bB", "peter"))), ret, 1);

        assertEquals(2, get(0).size());
        Iterator<UrlEntry> iter = get(0).iterator();

        assertEquals("http://is.gd/eoXjX_x", iter.next().getResolvedUrl());
        assertEquals("http://wp.me/p8zlh-z6_x", iter.next().getResolvedUrl());

        assertEquals(2, get(1).size());
        iter = get(1).iterator();

        assertEquals("http://bit.ly/ahlIIw_x", iter.next().getResolvedUrl());
        assertEquals("http://ib2.in/bB_x", iter.next().getResolvedUrl());
    }

    TweetProducer createProducer() {
        return new TweetProducer() {

            @Override
            public UrlExtractor createExtractor() {
                return new UrlExtractor() {

                    @Override
                    public String resolveOneUrl(String url, int timeout) {
                        return url + "_x";
                    }

                    @Override
                    public String[] getInfo(String url, int timeout) {
                        return new String[]{url + "_t", url + "_s"};
                    }
                };
            }
        };
    }
}
