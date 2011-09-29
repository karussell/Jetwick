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
package de.jetwick.ui;

import org.apache.wicket.Page;
import org.apache.wicket.Request;
import org.apache.wicket.Response;
import org.apache.wicket.Session;
import org.apache.wicket.guice.GuiceComponentInjector;
import org.apache.wicket.protocol.http.WebApplication;

import com.google.inject.Guice;
import com.google.inject.Injector;
import de.jetwick.config.Configuration;
import de.jetwick.config.DefaultModule;
import de.jetwick.data.JUser;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.tw.TwitterSearch;
import de.jetwick.util.Helper;
import org.apache.wicket.Application;
import org.apache.wicket.protocol.http.RequestUtils;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.request.target.coding.MixedParamUrlCodingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application object for your web application. If you want to run this application without deploying, run the Start class.
 *
 * @author Peter Karich
 */
public class JetwickApp extends WebApplication {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private Configuration cfg;
    private Injector injector;

    public JetwickApp() {
        this(Guice.createInjector(new DefaultModule()));
    }

    public JetwickApp(Injector inj) {
        injector = inj;
        cfg = injector.getInstance(Configuration.class);
    }

    protected GuiceComponentInjector getGuiceInjector() {
        return new GuiceComponentInjector(this, injector);
    }

    @Override
    protected void init() {
        super.init();

        // cache js etc one month
        getResourceSettings().setDefaultCacheDuration(30 * 24 * 3600);

        getApplicationSettings().setPageExpiredErrorPage(TweetSearchPage.class);
        getApplicationSettings().setInternalErrorPage(ErrorPage.class);

        // default is <em> </em> for disabled links
        getMarkupSettings().setDefaultBeforeDisabledLink(null);
        getMarkupSettings().setDefaultAfterDisabledLink(null);

        if ("development".equals(cfg.getStage())) {
            getDebugSettings().setDevelopmentUtilitiesEnabled(true);
//            getRequestCycleSettings().addResponseFilter(new ServerAndClientTimeFilter());
        }

//    For HybridUrlCodingStrategy we get some problems:
//    jetty bug for characters like '#' in query it won't work
//    link in tweet is: http://localhost/twittersearch|||0.1?u=%40TelegraphNews
//        and not       http://localhost/twittersearch?u=%40TelegraphNews|||0.1
//    error whens searching for \ => tomcat has problem (empty page)
//    not reproduceable: wget http://localhost/jetwick-dev/twittersearch/q/java
//         and you will get an endless loop! (for users without cookies!)
//    not reproducable problem if we click on 'retweets' of the following tweet nothing happens:
//         http://localhost/twittersearch/id/25372450085
//         but if we filter first then it works!!?? (e.g. search #wicket + filter original)


        // Die aufgerufene Website leitet die Anfrage so um, dass sie nie beendet werden kann.
        // Dieses Problem kann manchmal auftreten, wenn Cookies deaktiviert oder abgelehnt werden.
//        HybridUrlCodingStrategy strategy = new HybridUrlCodingStrategy("twittersearch", TweetSearchPage.class, true) {
//
//            @Override
//            protected String getBeginSeparator() {
//                return "|||";
//            }
//        };
//        mount(strategy);       

        // 1.5-M2.1
//        getRootRequestMapperAsCompound().add(new MountedMapper("twittersearch", TweetSearchPage.class));

//        getRequestCycleSettings().setRenderStrategy(IRequestCycleSettings.ONE_PASS_RENDER);

        mountBookmarkablePage("about", About.class);
        mountBookmarkablePage("imprint", Imprint.class);
        mountBookmarkablePage("offline", OfflinePage.class);

        if (!"offline".equals(cfg.getStage()))
            mount(new MixedParamUrlCodingStrategy("tweets", TweetSearchPage.class, new String[]{}));
        else
            mount(new MixedParamUrlCodingStrategy("tweets", OfflinePage.class, new String[]{}));


        mount(new MixedParamUrlCodingStrategy("login", Login.class, new String[]{}));
        addComponentInstantiationListener(getGuiceInjector());
    }

    @Override
    public Class<? extends Page> getHomePage() {
        if ("offline".equals(cfg.getStage()))
            return OfflinePage.class;
        else
            return TweetSearchPage.class;
    }

    // enable production mode
    @Override
    public String getConfigurationType() {
        if ("development".equals(cfg.getStage()))
            return Application.DEVELOPMENT;
        else
            return Application.DEPLOYMENT;
    }

    public static String createAbsoluteUrl(String urlFor) {
        String absUrl = RequestUtils.toAbsolutePath(urlFor);
        // current url encoding strategy creates for localhost: 
        // http://localhost:8080/jetwick/login/callback/true/slide/true
        if (Application.DEPLOYMENT.equals(Application.get().getConfigurationType())) {
            int index = absUrl.indexOf("jetwick/");
            if (index > 0)
                absUrl = Helper.JETSLIDE_URL + absUrl.substring(index + "jetwick/".length());
        }

        return absUrl;
    }

    @Override
    public Session newSession(Request request, Response response) {
        // inject only once per session!
        MySession session = new MySession(request);
        TwitterSearch ts = injector.getInstance(TwitterSearch.class);
        session.setTwitterSearch(ts);
        WebRequest wreq = (WebRequest) request;
        ElasticUserSearch uSearch = injector.getInstance(ElasticUserSearch.class);
        session.onNewSession(wreq, uSearch);
//        logger.info("new session user:" + session.getUser());
        if (session.hasLoggedIn()) {
            // set user specific twitter4j
            JUser u = session.getUser();
            ts.initTwitter4JInstance(u.getTwitterToken(), u.getTwitterTokenSecret(), false);
        }
        return session;
    }
}
