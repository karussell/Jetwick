/*
 * Copyright 2011 Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetwick.tw;

import de.jetwick.solr.SolrTweet;
import de.jetwick.solr.SolrUser;
import de.jetwick.util.AnyExecutor;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.http.AccessToken;

/**
 * Offline twitter search. Implemented to test saved searches.
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TwitterSearchOffline extends TwitterSearch {

    private Logger logger = LoggerFactory.getLogger(TwitterSearchOffline.class);

    public TwitterSearchOffline() {
        logger.warn("Using offline twitter search!");
    }

    @Override
    public TwitterSearch initTwitter4JInstance(String token, String tokenSecret) {
        return this;
    }

    @Override
    public String oAuthLogin(String callbackUrl) throws Exception {
        return callbackUrl + "&oauth_verifier=xy";
    }

    @Override
    public AccessToken oAuthOnCallBack(String oauth_verifierParameter) throws TwitterException {
        return new AccessToken("token", "tokenSecret");
    }

    @Override
    public User getTwitterUser() throws TwitterException {
        return new Twitter4JUser("_testUser_");
    }

    @Override
    public void getFriends(String userName, AnyExecutor<SolrUser> executor) {
        executor.execute(new SolrUser("timetabling"));
        executor.execute(new SolrUser("dzone"));
    }        
    
    @Override
    public long search(String term, Collection<SolrTweet> result, int tweets, long lastId) throws TwitterException {
        throw new UnsupportedOperationException("use online version for searching!");
    }
}
