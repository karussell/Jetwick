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

package de.jetwick.solr;

import org.elasticsearch.common.Base64;
import de.jetwick.config.Configuration;
import de.jetwick.util.Helper;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
//import org.apache.solr.common.util.Base64;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class UserIndexResponseOrderTestClass {

    private static String url;
    private String userPw;
    private static String FIRST_USER_NAME = "/response/result/doc[1]/str[@name='user']";

    @Before
    public void setUp() {
        Configuration cfg = new Configuration();
        url = cfg.getTweetSearchRemoteUrl() + "/select?";
        userPw = cfg.getTweetSearchLogin() + ":" + cfg.getTweetSearchPassword();
    }

    @Test
    public void testUserComesFirst() throws Exception {
        Node node = query("fq=user:google", FIRST_USER_NAME);
        assertEquals("google", node.getTextContent());

        node = query("fq=user:dzone", FIRST_USER_NAME);
        assertEquals("dzone", node.getTextContent());

        node = query("fq=user:wikileaks", FIRST_USER_NAME);
        assertEquals("wikileaks", node.getTextContent());

        node = query("fq=user:twitter", FIRST_USER_NAME);
        assertEquals("twitter", node.getTextContent());

//        node = query("q=\"justin%20bieber\"", FIRST_USER_NAME);
//        assertEquals("justinbieber", node.getTextContent());
    }

    private Node query(String query, String xpath) throws Exception {
        Document doc = Helper.newDocumentBuilder().parse(createConnection(url + query).getInputStream());
        //System.out.println(Helper.getDocumentAsString(doc, true));
        return xpath(doc, xpath);
    }

    private Node xpath(Node node, String xpath) throws Exception {
        XPathFactory factory = XPathFactory.newInstance();
        XPath path = factory.newXPath();
        XPathExpression expression = path.compile(xpath);
        return (Node) expression.evaluate(node, XPathConstants.NODE);
    }

    private HttpURLConnection createConnection(String url) throws Exception {
        HttpURLConnection urlConn = null;

        // there are several other such methods. e.g. in twitter4j or under com.sun:
        String encoding = new String(Base64.decode(userPw));
        //URLConnection
        urlConn = (HttpURLConnection) new URL(url).openConnection();
        // Enable reading from server ( to read response )
        urlConn.setDoInput(true);
        // Disable cache
        urlConn.setUseCaches(false);
        urlConn.setDefaultUseCaches(false);
        // Set Basic Authentication parameters
        urlConn.setRequestProperty("Authorization", "Basic " + encoding);
        return urlConn;
    }
}
