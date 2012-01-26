/*
 *  Copyright 2011 Peter Karich
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
 */
package de.jetwick.util;

import com.google.api.translate.Language;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.util.zip.GZIPInputStream;
import org.json.JSONArray;

/**
 * Makes the Google Translate API available to Java applications.
 * 
 * @author Peter Karich
 */
public final class Translate {

    private static final String URL = "http://translate.google.com/translate_a/t?client=t&sl=#FROM#&tl=#TO#&text=";

    /**
     * Translates text from a given Language to another given Language using Google Translate.
     * 
     * @param text The String to translate.
     * @param from The language code to translate from.
     * @param to The language code to translate to.
     * @return The translated String.
     * @throws Exception on error.
     */
    public static String execute(final String text, final Language from, final Language to) throws Exception {
        String url = URL.replaceAll("#FROM#", from.toString()).replaceAll("#TO#", to.toString())
                + URLEncoder.encode(text, "UTF8");
        JSONArray arr = new JSONArray(download(url));
        return arr.getJSONArray(0).getJSONArray(0).getString(0);
    }

    public static String download(String urlAsString) {
        try {
            URL url = new URL(urlAsString);
            //using proxy may increase latency
            HttpURLConnection hConn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            hConn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux i686; rv:7.0.1) Gecko/20100101 Firefox/7.0.1");
            hConn.addRequestProperty("Referer", "http://jetsli.de/crawler");

            hConn.setConnectTimeout(2000);
            hConn.setReadTimeout(2000);
            InputStream is = hConn.getInputStream();
            if ("gzip".equals(hConn.getContentEncoding()))
                is = new GZIPInputStream(is);

            return getInputStream(is);
        } catch (Exception ex) {
            return "";
        }
    }

    public static String getInputStream(InputStream is) throws IOException {
        if (is == null)
            throw new IllegalArgumentException("stream mustn't be null!");

        BufferedReader bufReader = Helper.createBuffReader(is);
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufReader.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
            return sb.toString();
        } finally {
            bufReader.close();
        }
    }
}
