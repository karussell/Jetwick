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
package de.jetwick.es;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TokenizerFromSet extends Tokenizer {

    private TermAttribute termAtt;
    private OffsetAttribute offsetAtt;
    private Iterator<String> iter;
    private int counter = 0;

    public TokenizerFromSet(Iterator<String> iter) {
        this.iter = iter;
        offsetAtt = addAttribute(OffsetAttribute.class);
        termAtt = addAttribute(TermAttribute.class);
    }

    @Override
    public boolean incrementToken() throws IOException {
        clearAttributes();
        if (iter.hasNext()) {
            termAtt.setTermBuffer(iter.next());
            offsetAtt.setOffset(counter, counter);
            counter++;
            return true;
        }
        return false;
    }
}
