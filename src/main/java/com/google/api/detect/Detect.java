/**
 * Copyright (C) 2009,  Richard Midwinter
 *
 * Stands under LGPL. See license.txt
 */
package com.google.api.detect;

import java.net.URL;
import java.net.URLEncoder;

import org.json.JSONObject;

import com.google.api.GoogleAPI;
import com.google.api.translate.Language;

/**
 * Makes the Google Detect API available to Java applications.
 * 
 * @author Richard Midwinter
 * @author Soren AD <soren@tanesha.net>
 */
public class Detect extends GoogleAPI {
	
	/**
	 * Constants.
	 */
	private static String URL = "http://ajax.googleapis.com/ajax/services/language/detect?v=1.0&q=";

	/**
	 * Detects the language of a supplied String.
	 * 
	 * @param text The String to detect the language of.
	 * @return A DetectResult object containing the language, confidence and reliability.
	 * @throws Exception on error.
	 */
	public static DetectResult execute(final String text) throws Exception {
    	validateReferrer();
    	
		final URL url = new URL(URL +URLEncoder.encode(text, ENCODING));
		
		final JSONObject json = retrieveJSON(url);
		
		return new DetectResult(
				Language.fromString(json.getJSONObject("responseData").getString("language")),
				json.getJSONObject("responseData").getBoolean("isReliable"),
				json.getJSONObject("responseData").getDouble("confidence"));
	}
}
