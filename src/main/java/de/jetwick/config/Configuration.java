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

import de.jetwick.tw.Credits;
import de.jetwick.util.Helper;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * Put here what you want in the read only configuration file
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class Configuration {

    private Properties prop;
    private static final String CONFIG_FILE = "jetwick.config.file";
    private String file;

    public Configuration() {
        file = Helper.getFileUnderHome("config.properties");
        if (System.getProperty(CONFIG_FILE) != null)
            file = System.getProperty(CONFIG_FILE);

        try {
            prop = new Properties();
            prop.load(new InputStreamReader(new FileInputStream(file), Helper.UTF8));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Jetwick needs a config file under:" + file);
        }
    }

    /**
     * @return either development or production. if empty it returns production
     */
    public String getStage() {
        String key = "app.stage";
        String val = get(key);
        if (val == null || val.isEmpty())
            val = "production";

        return val;
    }

    public String getUserSearchUrl() {
        String key = "solr.usearch.url";
        return get(key);
    }

    public String getUserSearchLogin() {
        String key = "solr.usearch.login";
        return get(key);
    }

    public String getUserSearchPassword() {
        String key = "solr.usearch.password";
        return get(key);
    }

    public String getUserBlacklist() {
        String key = "solr.usearch.blacklist";
        return get(key);
    }

    public int getTweetsPerBatch() {
        String key = "solr.twcollector.batchsize";
        return Integer.parseInt(get(key, true));
    }

    public String getTweetSearchUrl() {
        String key = "solr.twsearch.url";
        return get(key, true);
    }

    public String getTweetSearchRemoteUrl() {
        String key = "solr.twsearch.remoteurl";
        return get(key);
    }

    public void setTweetSearchUrl(String str) {
        set("solr.twsearch.url", str);
    }

//    public String getTweetSearchMCDir() {
//        String key = "solr.twsearch.mc.instance.dir";
//        return get(key, true);
//    }
    public String getTweetSearchLogin() {
        String key = "solr.twsearch.login";
        return get(key);
    }

    public String getTweetSearchPassword() {
        String key = "solr.twsearch.password";
        return get(key);
    }

    public boolean getTweetStreamingServer() {
        return Boolean.parseBoolean(get("solr.twsearch.streamingserver"));
    }

    public int getSolrSearchForRTDays() {
        return Integer.parseInt(get("solr.twsearch.searchrt.days", true));
    }

    public int getSolrRemoveDays() {
        return Integer.parseInt(get("solr.twsearch.remove.days", true));
    }

    /**
     * @return optimize interval in hours
     */
    public String getTweetSearchOptimizeInterval() {
        String key = "solr.twsearch.optimize.interval";
        return get(key);
    }

    public int getTweetSearchCommitOptimizeSegments() {
        String key = "solr.twsearch.optimize.segments";
        return Integer.parseInt(get(key));
    }

    public boolean isTweetResolveUrl() {
        return get("tweet.resolveurl.timeout") != null;
    }

    public int getTweetResolveUrlTimeout() {
        return Integer.parseInt(get("tweet.resolveurl.timeout"));
    }

    public int getTweetResolveUrlThreads() {
        return Integer.parseInt(get("tweet.resolveurl.threads"));
    }

    public String getUrlTitleAvoidList() {
        String key = "tweet.resolveurl.avoidlist";
        return get(key);
    }

    public int getUserInfoUpdateDays() {
        return Integer.parseInt(get("twitter.update.userinfo.days"));
    }

    public String getRMIHost() {
        String key = "rmi.host";
        return get(key);
    }

    public int getRMIPort() {
        String key = "rmi.port";
        return Integer.parseInt(get(key));
    }

    public String getRMIService() {
        String key = "rmi.service";
        return get(key);
    }

    public Credits getTwitterSearchCredits() {
        String key = "jetwick.twitter4j.main.";
        return new Credits(get(key + "token"), get(key + "tokenSecret"),
                get(key + "consumerKey"), get(key + "consumerSecret"));
    }

    public Credits getJetwotCredits() {
        String key = "jetwick.twitter4j.jetwot.";
        return new Credits(get(key + "token"), get(key + "tokenSecret"),
                get(key + "consumerKey"), get(key + "consumerSecret"));
    }    

    protected String get(String key) {
        return get(key, false);
    }

    protected String get(String key, boolean requiredProperty) {
        // system values are more important!
        String val = System.getProperty(key);
        if (val == null)
            val = prop.getProperty(key);

        if (requiredProperty && val == null)
            throw new NullPointerException("Value for " + key + " should NOT be null! Fix config file: " + file);

        return val;
    }

    protected void set(String key, String val) {
        prop.setProperty(key, val);
    }

    @Override
    public String toString() {
        String str = "";
        for (Entry<Object, Object> entry : prop.entrySet()) {
            String key = entry.getKey().toString();
            String val = entry.getValue().toString();
            if (key.toLowerCase().contains("secure") || key.toLowerCase().contains("password")
                    || key.toLowerCase().contains("secret"))
                continue;

            str += key + "=" + val + ";  ";
        }
        return str;
    }
}
