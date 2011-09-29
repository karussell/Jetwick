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
import de.jetwick.data.JTweet;
import de.jetwick.es.AbstractElasticSearch;
import de.jetwick.es.ElasticNode;
import de.jetwick.es.ElasticTagSearch;
import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.es.ElasticUserSearch;
import de.jetwick.rmi.RMIServer;
import de.jetwick.snacktory.HtmlFetcher;
import de.jetwick.tw.Credits;
import de.jetwick.tw.TweetProducer;
import de.jetwick.tw.TweetProducerViaSearch;
import de.jetwick.tw.TwitterSearch;
import de.jetwick.util.GenericUrlResolver;
import de.jetwick.util.MaxBoundSet;
import org.elasticsearch.client.Client;
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
        bind(Configuration.class).toInstance(config);

        installTweetProducer();
        installLastSearches();
        installTwitterModule();
        installSearchModule();
        installRMIModule();

        HtmlFetcher fetcher = createHtmlFetcher();
        if (fetcher != null)
            bind(HtmlFetcher.class).toInstance(fetcher);

        GenericUrlResolver urlResolver = createGenericUrlResolver();
        if (urlResolver != null)
            bind(GenericUrlResolver.class).toInstance(urlResolver);        
    }

    public void installSearchModule() {
        // TODO shouldn't fail when node is not available!!??
        Client client = AbstractElasticSearch.createClient(ElasticNode.CLUSTER,
                config.getTweetSearchUrl(), ElasticNode.PORT);

        ElasticTweetSearch tweetSearch = new ElasticTweetSearch(client);
        try {
            tweetSearch.nodeInfo();
        } catch (Exception ex) {
            logger.warn("Problem to get node info:" + ex.getMessage());
        } 
        bind(ElasticTweetSearch.class).toInstance(tweetSearch);

        ElasticUserSearch userSearch = new ElasticUserSearch(client);
        bind(ElasticUserSearch.class).toInstance(userSearch);

        ElasticTagSearch tagSearch = new ElasticTagSearch(client);
        bind(ElasticTagSearch.class).toInstance(tagSearch);        
    }

    public GenericUrlResolver createGenericUrlResolver() {
        final GenericUrlResolver urlResolver = new GenericUrlResolver(config.getUrlResolverQueueSize());
        urlResolver.setResolveThreads(config.getUrlResolverThreads());
        urlResolver.setResolveTimeout(config.getUrlResolverTimeout());
//        urlResolver.setMaxQueueSize(config.getUrlResolverHelperQueueSize());        
        return urlResolver;
    }

    public void installRMIModule() {
        bind(RMIServer.class).toInstance(new RMIServer(config));
    }

    public void installTwitterModule() {
        final Credits cred = config.getTwitterSearchCredits();
//        logger.info("TWITTER:"+cred.getConsumerKey() + " " + cred.getConsumerSecret());
//        logger.info(cred.getToken() + " " + cred.getTokenSecret());

        final TwitterSearch ts = createTwitterSearch().setConsumer(
                cred.getConsumerKey(), cred.getConsumerSecret());

        try {
            ts.initTwitter4JInstance(cred.getToken(), cred.getTokenSecret(), true);
        } catch (Exception ex) {
            logger.error("Cannot create twitter4j instance!\n######### TWITTER4J ERROR: But start jetwick nevertheless! Error:" + ex);
        }

        bind(TwitterSearch.class).toProvider(new Provider<TwitterSearch>() {

            @Override
            public TwitterSearch get() {
                // avoid exception in this getter: do not call twitter.verify which can fail if twitter down etc
                return createTwitterSearch().setConsumer(
                        cred.getConsumerKey(), cred.getConsumerSecret()).
                        setTwitter4JInstance(ts.getTwitter4JInstance());
            }
        });
    }

    public TwitterSearch createTwitterSearch() {
        return new TwitterSearch();
//        return new TwitterSearchOffline();
    }

    public void installLastSearches() {
        logger.info("install maxBoundSet singleton");
//        bind(MaxBoundSet.class).asEagerSingleton();
        bind(MaxBoundSet.class).toInstance(new MaxBoundSet<String>(250, 500).setMaxAge(10 * 60 * 1000));
    }

    public void installTweetProducer() {
//        bind(TweetProducer.class).to(TweetProducerOffline.class);
        bind(TweetProducer.class).to(TweetProducerViaSearch.class);
    }

    public HtmlFetcher createHtmlFetcher() {
        HtmlFetcher fetcher = new HtmlFetcher();
        fetcher.setMaxTextLength(JTweet.MAX_LENGTH);
        return fetcher;
    }
}
