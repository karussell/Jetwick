/*
 * Copyright 2011 Peter Karich, jetwick_@_pannous_._info.
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

import de.jetwick.snacktory.JResult;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class UrlExtractorTest {
    
    public UrlExtractorTest() {
    }

    @Test
    public void testToLink() {
        UrlExtractor extractor = new UrlExtractor() {

            @Override
            public JResult getInfo(String url, int timeout) throws Exception {
                throw new UnsupportedOperationException("Not supported yet.");
            }            
        };
        assertEquals("http://jetsli.de", 
                extractor.setText("tetw http://jetsli.de").run().getUrlEntries().iterator().next().getResolvedUrl());
        
        // url extractor should be reusable
        assertEquals("http://t.co/qvfHAVC", 
                extractor.setText("tetw http://t.co/qvfHAVC\" ").run().getUrlEntries().iterator().next().getResolvedUrl());      
    }    
}
