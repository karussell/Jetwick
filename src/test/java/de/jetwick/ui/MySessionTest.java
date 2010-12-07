/*
 *  Copyright 2010 Peter Karich jetwick_@_pannous_._info
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetwick.ui;

import de.jetwick.solr.SolrUser;
import de.jetwick.solr.SolrUserSearch;
import de.jetwick.tw.Credits;
import de.jetwick.tw.TwitterSearch;
import java.net.URL;
import java.util.Date;
import javax.servlet.http.Cookie;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebResponse;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import twitter4j.RateLimitStatus;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.User;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class MySessionTest extends WicketPagesTestClass {

    public MySessionTest() {
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    SolrUserSearch newMockUserSearch(SolrUser user) {
        SolrUserSearch s = mock(SolrUserSearch.class);
        when(s.findByTwitterToken("normalToken")).thenReturn(user);
        return s;
    }

    @Test
    public void testInit() {
        MySession session = (MySession) tester.getWicketSession();
        assertNull(session.getUser());

        session.setUseDefaultUser(false);
        session.init(tester.getWicketRequest(), newMockUserSearch(null));
        assertNull(session.getUser());

        session.setTwitterSearchInitialized(false);
        session.setUseDefaultUser(true);
        session.init(tester.getWicketRequest(), newMockUserSearch(null));
        assertNotNull(session.getUser());
        session.logout(newMockUserSearch(null), tester.getWicketResponse());
        assertNull(session.getUser());
    }

    @Test
    public void testInitFromCookie() {
        MySession session = (MySession) tester.getWicketSession();
        session.setUseDefaultUser(false);

        WebRequest req = mock(WebRequest.class);
        when(req.getCookie(TwitterSearch.COOKIE)).thenReturn(new Cookie(TwitterSearch.COOKIE, "normalToken"));
        session.init(req, newMockUserSearch(new SolrUser("testuser")));
        assertEquals("testuser", session.getUser().getScreenName());
    }

    @Test
    public void testDoNotInitFromWrongCookie() {
        MySession session = (MySession) tester.getWicketSession();
        session.setUseDefaultUser(false);

        WebRequest req = mock(WebRequest.class);
        session.setUseDefaultUser(false);
        when(req.getCookie(TwitterSearch.COOKIE)).thenReturn(new Cookie("tokenWrong", null));
        session.init(req, newMockUserSearch(new SolrUser("testuser")));
        assertNull(session.getUser());
    }

    @Test
    public void testSetCookie() throws TwitterException {
        MySession session = (MySession) tester.getWicketSession();
        session.setUseDefaultUser(false);

        TwitterSearch ts = mock(TwitterSearch.class);
        when(ts.init()).thenReturn(true);
        when(ts.getCredits()).thenReturn(new Credits("normalToken", "tSec", "x", "y"));
        when(ts.getTwitterUser()).thenReturn(new Twitter4JUser("testuser"));

        WebResponse resp = mock(WebResponse.class);
        SolrUser user = new SolrUser("testuser");
        SolrUserSearch uSearch = newMockUserSearch(user);
        Cookie cookie = session.setTwitterSearch(ts, uSearch, resp);
        verify(uSearch).save(user, true);
        assertEquals(TwitterSearch.COOKIE, cookie.getName());
        assertEquals("normalToken", cookie.getValue());

        uSearch = newMockUserSearch(user);
        session.logout(uSearch, resp);
        verify(uSearch).save(user, true);
        verify(resp).clearCookie(cookie);
    }
}
