/**
 * Copyright (C) 2009,  Richard Midwinter
 *
 * Stands under LGPL. See license.txt
 */
package com.google.api;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;

/**
 * Makes generic Google API functionality available to specific API classes.
 * 
 * @author Richard Midwinter
 * @author Kramar Tomas
 */
public abstract class GoogleAPI {

    protected static final String ENCODING = "UTF-8";
    /**
     * The HTTP referrer attribute.
     * 
     * This allows Google to distinguish between programs.
     */
    protected static String referrer;

    /**
     * Sets the HTTP Referrer.
     * @param pReferrer The HTTP referrer parameter.
     */
    public static void setHttpReferrer(final String pReferrer) {
        referrer = pReferrer;
    }

    public static void validateReferrer() throws Exception {
        if (referrer == null || referrer.length() == 0) {
            throw new Exception("[google-api-translate-java] Referrer is not set. Call setHttpReferrer().");
        }
    }

    /**
     * Forms an HTTP request, sends it using GET method and returns the result of the request as a JSONObject.
     * 
     * @param url The URL to query for a JSONObject.
     * @return The translated String.
     * @throws Exception on error.
     */
    protected static JSONObject retrieveJSON(final URL url) throws Exception {
        try {
            final HttpURLConnection uc = (HttpURLConnection) url.openConnection();
            uc.setRequestProperty("referer", referrer);
            uc.setRequestMethod("GET");
            uc.setDoOutput(true);

            try {
                final String result = inputStreamToString(uc.getInputStream());

                return new JSONObject(result);
            } finally { // http://java.sun.com/j2se/1.5.0/docs/guide/net/http-keepalive.html
                uc.getInputStream().close();
                if (uc.getErrorStream() != null) {
                    uc.getErrorStream().close();
                }
            }
        } catch (Exception ex) {
            throw new Exception("[google-api-translate-java] Error retrieving translation.", ex);
        }
    }

    /**
     * Forms an HTTP request, sends it using POST method and returns the result of the request as a JSONObject.
     * 
     * @param url The URL to query for a JSONObject.
     * @param parameters Additional POST parameters
     * @return The translated String.
     * @throws Exception on error.
     */
    protected static JSONObject retrieveJSON(final URL url, final String parameters) throws Exception {
        try {
            final HttpURLConnection uc = (HttpURLConnection) url.openConnection();
            uc.setRequestProperty("referer", referrer);
            uc.setRequestMethod("POST");
            uc.setDoOutput(true);

            final PrintWriter pw = new PrintWriter(uc.getOutputStream());
            pw.write(parameters);
            pw.flush();

            try {
                final String result = inputStreamToString(uc.getInputStream());

                return new JSONObject(result);
            } finally { // http://java.sun.com/j2se/1.5.0/docs/guide/net/http-keepalive.html
                uc.getInputStream().close();
                if (uc.getErrorStream() != null) {
                    uc.getErrorStream().close();
                }
                pw.close();
            }
        } catch (Exception ex) {
            throw new Exception("[google-api-translate-java] Error retrieving translation.", ex);
        }
    }

    /**
     * Reads an InputStream and returns its contents as a String.
     * Also effects rate control.
     * @param inputStream The InputStream to read from.
     * @return The contents of the InputStream as a String.
     * @throws Exception on error.
     */
    private static String inputStreamToString(final InputStream inputStream) throws Exception {
        final StringBuilder outputBuilder = new StringBuilder();

        try {
            String string;
            if (inputStream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, ENCODING));
                while (null != (string = reader.readLine())) {
                    outputBuilder.append(string).append('\n');
                }
            }
        } catch (Exception ex) {
            throw new Exception("[google-api-translate-java] Error reading translation stream.", ex);
        }

        return outputBuilder.toString();
    }
}
