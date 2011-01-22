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

import com.google.inject.Guice;
import com.google.inject.Injector;
import de.jetwick.config.Configuration;
import de.jetwick.config.DefaultModule;
import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.es.ElasticTweetSearchTest;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.es.ElasticUserSearchTest;
import de.jetwick.rmi.RMIClient;
import de.jetwick.solr.SolrUser;
import de.jetwick.tw.TwitterSearch;
import de.jetwick.tw.queue.TweetPackage;
import java.rmi.RemoteException;
import org.apache.wicket.Application;
import org.apache.wicket.guice.GuiceComponentInjector;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.Before;
import twitter4j.TwitterException;

public class WicketPagesTestClass {

    protected WicketTester tester;
    protected Injector injector;

    @Before
    public void setUp() throws Exception {
        tester = new WicketTester(createJetwickApp());
    }

    protected <T> T getInstance(Class<T> clazz) {
        return injector.getInstance(clazz);
    }

    protected JetwickApp createJetwickApp() {
        DefaultModule mod = new DefaultModule() {

            @Override
            public void installTwitterModule() {
                bind(TwitterSearch.class).toInstance(createTwitterSearch());
            }

            @Override
            public void installDbModule() {
                // TODO provide mock db
            }

            @Override
            public void installSearchModule() {
                // TODO provide mock searcher                
                bind(ElasticUserSearch.class).toInstance(createSolrUserSearch());

                bind(ElasticTweetSearch.class).toInstance(createSolrTweetSearch());
            }

            @Override
            public void installRMIModule() {
                bind(RMIClient.class).toInstance(createRMIClient());
            }
        };
        injector = Guice.createInjector(mod);
        return new JetwickApp(injector) {

            @Override
            public String getConfigurationType() {
                return Application.DEVELOPMENT;
            }

            @Override
            protected GuiceComponentInjector getGuiceInjector() {
                return new GuiceComponentInjector(this, injector);
            }
        };
    }

    protected ElasticUserSearch createSolrUserSearch() {
        ElasticUserSearchTest sst = new ElasticUserSearchTest();
        try {
            sst.setUp();
            return sst.getUserSearch();
        } catch (Exception ex) {
            throw new UnsupportedOperationException("Cannot setup user search", ex);
        }
    }

    protected ElasticTweetSearch createSolrTweetSearch() {
        ElasticTweetSearchTest stst = new ElasticTweetSearchTest();
        try {
            stst.setUp();
            return stst.getTweetSearch();
        } catch (Exception ex) {
            throw new UnsupportedOperationException("Cannot setup tweet search", ex);
        }
    }

    protected TwitterSearch createTwitterSearch() {
        return new TwitterSearch() {

            @Override
            public int getRateLimit() {
                return 100;
            }

            @Override
            public TwitterSearch setTwitter4JInstance(String t, String ts) {
                return this;
            }

            @Override
            public SolrUser getUser() throws TwitterException {
                return new SolrUser("testUser");
            }
        }.setConsumer("", "");
    }

    protected RMIClient createRMIClient() {
        return new RMIClient(new Configuration()) {

            @Override
            public RMIClient init() {
                return this;
            }

            @Override
            public void send(TweetPackage tweets) throws RemoteException {
                // disable rmi stuff
            }
        };
    }
}
