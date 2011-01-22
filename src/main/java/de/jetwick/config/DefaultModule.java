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
package de.jetwick.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.rmi.RMIServer;
import de.jetwick.tw.Credits;
import de.jetwick.tw.TweetProducer;
import de.jetwick.tw.TweetProducerOnline;
import de.jetwick.tw.TwitterSearch;
import de.jetwick.tw.UrlTitleCleaner;
import de.jetwick.util.MaxBoundSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultModule extends AbstractModule {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private Configuration config = new Configuration();

    public DefaultModule() {
    }

    @Override
    protected void configure() {
        logger.info(config.toString());
        installTweetProducer();
        installLastSearches();
        installTwitterModule();
        installSearchModule();
        installDbPasswords();
        installDbModule();
        installRMIModule();
        installUrlCleaner();
    }

    public void installDbPasswords() {
        logger.info("db user:" + config.getHibernateUser());
        System.setProperty("hibernate.connection.username", config.getHibernateUser());
        System.setProperty("hibernate.connection.password", config.getHibernatePassword());
    }

    public void installDbModule() {
        install(new HibModule());
    }

    public void installSearchModule() {
        bind(Configuration.class).toInstance(config);        
        ElasticTweetSearch tweetSearch = new ElasticTweetSearch(config);
        bind(ElasticTweetSearch.class).toInstance(tweetSearch);

        ElasticUserSearch userSearch = new ElasticUserSearch(config);
        bind(ElasticUserSearch.class).toInstance(userSearch);
    }

    public void installRMIModule() {
        bind(RMIServer.class).toInstance(new RMIServer(config));
    }

    public void installTwitterModule() {
        final Credits cred = config.getTwitterSearchCredits();
        final TwitterSearch ts = new TwitterSearch().setConsumer(
                cred.getConsumerKey(), cred.getConsumerSecret());

        try {
            ts.setTwitter4JInstance(cred.getToken(), cred.getTokenSecret());
        } catch (Exception ex) {
            logger.error("Cannot create twitter4j instance!\n######### TWITTER4J ERROR: But start jetwick nevertheless! Error:" + ex);
        }

        bind(TwitterSearch.class).toProvider(new Provider<TwitterSearch>() {

            @Override
            public TwitterSearch get() {
                return new TwitterSearch().setConsumer(
                        cred.getConsumerKey(), cred.getConsumerSecret()).
                        setTwitter4JInstance(ts.getTwitter4JInstance());
            }
        });
    }

    public void installLastSearches() {
        logger.info("install maxBoundSet singleton");
//        bind(MaxBoundSet.class).asEagerSingleton();
        bind(MaxBoundSet.class).toInstance(new MaxBoundSet<String>(250, 500).setMaxAge(10 * 60 * 1000));
    }

    public void installUrlCleaner() {
        try {
            UrlTitleCleaner cleaner = new UrlTitleCleaner(config.getUrlTitleAvoidList());
            bind(UrlTitleCleaner.class).toInstance(cleaner);
        } catch (Exception ex) {
            logger.error("error while reading url-title-file:" + config.getUrlTitleAvoidList() + " " + ex.getMessage());
        }
    }

    private void installTweetProducer() {
//        bind(TweetProducer.class).to(TweetProducerOffline.class);
        bind(TweetProducer.class).to(TweetProducerOnline.class);
    }
}
