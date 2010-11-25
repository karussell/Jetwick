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

import de.jetwick.config.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * This class tests the multicore + shard set up of our solr search.
 *
 * can we use index merging + creation with multiple webapps? see last comment in
 * http://www.derivante.com/2009/05/05/solr-performance-benchmarks-single-vs-multi-core-index-shards/
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SolrMCTweetSearchTest extends AbstractMCTestCase {
    
    private SolrMCTweetSearch mcSearch;

    public SolrMCTweetSearch getTweetSearch() {
        return mcSearch;
    }

    @Before
    @Override
    public void setUp() throws Exception {
//        super.setUp();
//
//        Configuration cfg = new Configuration();
//        cfg.setTweetSearchUrl("http://localhost:" + port + solrWebapp);
//        mcSearch = new SolrMCTweetSearch(cfg);
    }

    @After
    @Override
    public void tearDown() throws Exception {
//        super.tearDown();
    }

    @Test
    public void testCreateIfNeeded() throws Exception {
//        assertEquals(true, mcSearch.createCoreIfNeeded("test"));

    }
}
