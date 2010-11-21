package de.jetwick.solrplugin;

import org.apache.lucene.search.DefaultSimilarity;

/**
 * similar to
 * @see https://issues.apache.org/jira/browse/LUCENE-1360
 */
public class MySimilarity extends DefaultSimilarity {

    private static float ARR[] = {0.0f, 1.5f, 1.25f, 1.0f, 0.875f, 0.75f, 0.625f, 0.5f, 0.4375f, 0.375f, 0.3125f};

    /**
     * Implemented as a lookup for the first 10 counts, then
     * <code>1/sqrt(numTerms)</code>. This is to avoid term counts below
     * 11 from having the same lengthNorm after being stored encoded as
     * a single byte.
     */
    @Override
    public float lengthNorm(String fieldName, int numTerms) {
        if (numTerms < 20) {
            // this shouldn't be possible, but be safe.
            if (numTerms <= 0)
                return 0;

//            return ARR[numTerms];
            return -0.00606f * numTerms + 0.35f;
        }
        //else
        return (float) (1.0 / Math.sqrt(numTerms));
    }
//    @Override
//    public float tf(float freq) {
//        if (freq > 0 && freq <= 20) {
//            // make it linear
//            return -0.00606f * freq + 0.35f;
//        }
//
//        return super.tf(freq);
//    }
}
