/*
 *  Copyright 2011 Peter Karich, jetwick_@_pannous_._info.
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
 *  under the License.
 */
package de.jetwick.ui;

import com.google.inject.Inject;
import com.google.inject.Provider;
import de.jetwick.data.JTag;
import de.jetwick.es.ElasticTagSearch;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.es.JetwickQuery;
import de.jetwick.tw.TwitterSearch;
import de.jetwick.util.Helper;
import java.util.Date;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.CSSPackageResource;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class JetwickPage extends WebPage {

    @Inject
    protected Provider<ElasticTagSearch> tagindexProvider;
    @Inject
    protected Provider<ElasticUserSearch> uindexProvider;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public JetwickPage() {
        this(true);
    }

    public JetwickPage(boolean enableDefaultStyle) {
        if (enableDefaultStyle)
            add(CSSPackageResource.getHeaderContribution("css/style.css"));

//            <link wicket:id="style" rel="stylesheet" type="text/css" href="css/style.css"/> 
//        add(new WebMarkupContainer("style").setVisible(enableDefaultStyle));        
    }

    public void init(PageParameters parameters) {
        initSession();
        init(createQuery(parameters), parameters);
    }

    public JetwickQuery createQuery(PageParameters parameters) {
        throw new RuntimeException("implement this");
    }

    public void init(JetwickQuery createQuery, PageParameters parameters) {
        throw new RuntimeException("implement this method");
    }

    public void postProcessing(final JetwickQuery q) {
        // we clean up tags after 2 days so this is ok
//        if (!getMySession().hasLoggedIn())
//            return;

        if (q.getQuery().length() < 2)
            return;

        if (!getMySession().hasLoggedIn()) {
            if (q.getQuery().startsWith("#") || q.getQuery().startsWith("%"))
                return;
        }

        JTag t = new JTag(q.getQuery());
        if (!Helper.isEmpty(q.extractUserName()))
            t.setUser(q.extractUserName());

        tagindexProvider.get().queueObject(t, true);
    }

    public void initSession() {
        try {
            String msg = getMySession().getSessionTimeOutMessage();

            if (!msg.isEmpty())
                warn(msg);

        } catch (Exception ex) {
            logger.error("Error on twitter4j init.", ex);
            error("Couldn't login. Please file report to http://twitter.com/jetwick " + new Date());
        }
    }

    public MySession getMySession() {
        return (MySession) getSession();
    }

    public TwitterSearch getTwitterSearch() {
        return getMySession().getTwitterSearch();
    }

    protected void myConfigureResponse() {
        // 1. searchAndGetUsers for wikileak
        // 2. apply de filter
        // 3. Show latest tweets (of user sebringl)
        // back button + de filter => WicketRuntimeException: component filterPanel:filterNames:1:filterValues:2:filterValueLink not found on page de.jetwick.ui.HomePage
        // http://www.richardnichols.net/2010/03/apache-wicket-force-page-reload-to-fix-ajax-back/
        // http://blogs.atlassian.com/developer/2007/12/cachecontrol_nostore_considere.html

        // M1.5 org.apache.wicket.request.http.WebResponse.disableCaching()

        WebResponse response = getWebRequestCycle().getWebResponse();
        response.setHeader("Cache-Control", "no-cache, max-age=0,must-revalidate, no-store");
    }
}
