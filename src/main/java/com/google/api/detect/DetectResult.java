/**
 * Copyright (C) 2009,  Richard Midwinter
 *
 * Stands under LGPL. See license.txt
 */
package com.google.api.detect;

import com.google.api.translate.Language;

/**
 * Represents the result of a Detect query.
 * 
 * @author Richard Midwinter
 * @author Soren AD <soren@tanesha.net>
 */
public class DetectResult {

    private Language language;
    private boolean reliable;
    private double confidence;

    public DetectResult(final Language language, final boolean reliable, final double confidence) {
        this.language = language;
        this.reliable = reliable;
        this.confidence = confidence;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public boolean isReliable() {
        return reliable;
    }

    public void setReliable(boolean reliable) {
        this.reliable = reliable;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
