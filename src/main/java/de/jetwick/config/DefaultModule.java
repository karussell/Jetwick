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
import de.jetwick.rmi.RMIServer;
import de.jetwick.solr.SolrAdSearch;
import de.jetwick.solr.SolrTweetSearch;
import de.jetwick.tw.TwitterSearch;
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
        installTwitterModule();
        installSolrModule();
        installDbPasswords();
        installDbModule();
        installRMIModule();
    }

    public void installDbPasswords() {
        logger.info("db user:" + config.getHibernateUser());
        System.setProperty("hibernate.connection.username", config.getHibernateUser());
        System.setProperty("hibernate.connection.password", config.getHibernatePassword());
    }

    public void installDbModule() {
        install(new HibModule());
    }

    public void installSolrModule() {
        bind(Configuration.class).toInstance(config);
        SolrTweetSearch tweetSearch = new SolrTweetSearch(config);
        bind(SolrTweetSearch.class).toInstance(tweetSearch);

        SolrAdSearch adSearch = new SolrAdSearch(config);
        bind(SolrAdSearch.class).toInstance(adSearch);
    }

    public void installRMIModule() {
        bind(RMIServer.class).toInstance(new RMIServer(config));
    }

    public void installTwitterModule() {
        bind(TwitterSearch.class).toInstance(new TwitterSearch().setCredits(config.getTwitterSearchCredits()));
    }
}
