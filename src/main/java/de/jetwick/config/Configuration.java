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
    private static final String CONFIG_FILE = "jetslide.config.file";
    private String file;

    public Configuration() {
        file = Helper.getFileUnderHome("config.properties");
        if (System.getProperty(CONFIG_FILE) != null)
            file = System.getProperty(CONFIG_FILE);

        try {
            prop = new Properties();
            prop.load(new InputStreamReader(new FileInputStream(file), Helper.UTF8));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Jetslide needs a config file under:" + file);
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
        String key = "jetslide.usearch.url";
        return get(key);
    }

    public String getUserBlacklist() {
        String key = "jetslide.usearch.blacklist";
        return get(key);
    }
    
    public int getTweetSearchBatch() {
        String key = "jetslide.twsearch.batchsize";
        return Integer.parseInt(get(key, true));
    }

    public String getTweetSearchUrl() {
        String key = "jetslide.twsearch.url";
        return get(key, true);
    }

    public boolean getTweetStreamingServer() {
        return Boolean.parseBoolean(get("jetslide.twsearch.streamingserver"));
    }

    public int getTweetSearchRemoveDays() {
        return Integer.parseInt(get("jetslide.twsearch.remove.days", true));
    }

    public String getUrlTitleAvoidList() {
        String key = "tweet.resolveurl.avoidlist";
        return get(key);
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
        String key = "jetslide.twitter4j.main.";
        return new Credits(get(key + "token"), get(key + "tokenSecret"),
                get(key + "consumerKey"), get(key + "consumerSecret"));
    }

    public Credits getJetwotCredits() {
        String key = "jetslide.twitter4j.jetwot.";
        return new Credits(get(key + "token"), get(key + "tokenSecret"),
                get(key + "consumerKey"), get(key + "consumerSecret"));
    }

    public double getTweetsPerSecLimit() {
        String key = get("twitter.stream.tweetsPerSecLimit");
        if (key == null)
            return 0.5;
        return Double.parseDouble(key);
    }

    public boolean isStreamEnabled() {
        String key = get("twitter.stream.enable");
        if (key == null)
            return true;
        return Boolean.parseBoolean(key);
    }

    public int getUrlResolverInputQueueSize() {
        String key = get("jetslide.urlresolver.inputqueuesize");
        if (key == null)
            return 300;
        return Integer.parseInt(key);
    }

    public int getUrlResolverQueueSize() {
        String key = get("jetslide.urlresolver.queuesize");
        if (key == null)
            return 700;
        return Integer.parseInt(key);
    }

    public int getUrlResolverThreads() {
        String key = get("jetslide.urlresolver.threads");
        if (key == null)
            return 10;
        return Integer.parseInt(key);
    }

    public int getUrlResolverTimeout() {
        String key = get("jetslide.urlresolver.timeout");
        if (key == null)
            return 10 * 1000;
        return Integer.parseInt(key);
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
