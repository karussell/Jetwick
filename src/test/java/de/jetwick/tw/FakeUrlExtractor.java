/*
 *  Copyright 2010 Peter Karich jetwick_@_pannous_._info
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
package de.jetwick.tw;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class FakeUrlExtractor extends UrlExtractor {

    @Override
    public String resolveOneUrl(String url, int timeout) {
        return url + "_x";
    }

    @Override
    public String[] getInfo(String url, int timeout) {
        return new String[]{url + "_t", url + "_s"};
    }
}
