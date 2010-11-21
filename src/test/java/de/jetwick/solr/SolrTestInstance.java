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

package de.jetwick.solr;

import java.io.File;

/**
 * Taken from http://css.dzone.com/articles/getting-know-solr
 */
public class SolrTestInstance {

    public static String SOLR_PROP = "de.jetwick.test.solr.root";
    public static String JAVA = "java";
    private Process solr;

    public void start() throws Exception {
        // couldn't get started the JettySolrRunner!? so we try this hack

        Runtime r = Runtime.getRuntime();
        solr = r.exec(JAVA + " -jar start.jar", null, getSolrRoot());
        Thread.sleep(4000);
    }

    public void stop() {
        if (solr != null)
            solr.destroy();
    }

    private File getSolrRoot() throws Exception {
        String root = System.getProperty(SOLR_PROP);
        if (root == null)
            throw new Exception("Solr path is not specified, please set the property " + SOLR_PROP);

        return new File(root);
    }
}
