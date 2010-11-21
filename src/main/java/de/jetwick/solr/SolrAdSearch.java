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
import de.jetwick.data.AdEntry;
import de.jetwick.util.Helper;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Querying + Indexing using the SolrJ interface which uses HTTP queries under the hood:
 * http://wiki.apache.org/solr/Solrj
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SolrAdSearch extends SolrAbstractSearch {

    public static final String ID = "id";
    protected Logger logger = LoggerFactory.getLogger(getClass());

    public SolrAdSearch(String url) {
        createServer(url, null, null, false);
    }

    public SolrAdSearch(Configuration config) {
        createServer(config.getAdSearchUrl(), config.getAdSearchLogin(), config.getAdSearchPassword(), config.getAdStreamingServer());
    }

    public SolrAdSearch(SolrServer server) {
        setServer(server);
    }

    public void update(Collection<AdEntry> entries) {
        try {
            Collection<SolrInputDocument> list = new ArrayList<SolrInputDocument>();
            for (AdEntry ae : entries) {
                SolrInputDocument doc = createDoc(ae);
                if (doc != null)
                    list.add(doc);
            }
            server.add(list);
        } catch (Exception e) {
            logger.error("Error while updating ad entries: " + entries.size() + ". " + e.getLocalizedMessage());
        }
    }

    @Override
    public QueryResponse search(SolrQuery q) throws SolrServerException {
        return search(new ArrayList(), q);
    }

    public Collection<AdEntry> search(String q) throws SolrServerException, InterruptedException {
        q = q.trim();
//        if (!q.startsWith("\"") && !q.endsWith("\""))
//            q = "\"" + q + "\"";

        List<AdEntry> res = new ArrayList<AdEntry>();

        if (!q.isEmpty())
            search(res, new SolrQuery(q).setRows(2));
        return res;
    }

    public QueryResponse search(Collection<AdEntry> result, SolrQuery query) throws SolrServerException {
        QueryResponse rsp = server.query(query);
        SolrDocumentList docs = rsp.getResults();

        for (SolrDocument sd : docs) {
            result.add(readDoc(sd));
        }

        return rsp;
    }

    public SolrInputDocument createDoc(AdEntry ae) throws IOException {
        SolrInputDocument doc1 = new SolrInputDocument();
        // make sure that if we look for a specific user this user will show up first:
        doc1.addField(ID, ae.getId());
        doc1.addField("description", ae.getDescription());
        doc1.addField("iconUrl", ae.getIconUrl());
        doc1.addField("title", ae.getTitle());

        for (String kw : ae.getKeywords()) {
            doc1.addField("keywords", kw);
        }

        for (Entry<String, String> entry : ae.getQueryUserPairs()) {
            String key = entry.getKey();
            String val = entry.getValue();
            if (key.isEmpty())
                key = " ";
            if (val.isEmpty())
                val = " ";

            doc1.addField("queryuser", key + "\t " + val);
        }

        return doc1;
    }

    public AdEntry readDoc(final SolrDocument doc) {
        String id = (String) doc.getFieldValue(ID);
        AdEntry ae = new AdEntry(id);
        ae.setDescription((String) doc.getFieldValue("description"));
        ae.setIconUrl((String) doc.getFieldValue("iconUrl"));
        ae.setTitle((String) doc.getFieldValue("title"));

        if (doc.getFieldValues("keywords") != null)
            for (Object kw : doc.getFieldValues("keywords")) {
                ae.addKeyword((String) kw);
            }

        for (Object kw : doc.getFieldValues("queryuser")) {
            String vals[] = ((String) kw).split("\t");
            ae.addQueryUserPair(vals[0], vals[1]);
        }

        return ae;
    }

    public Collection<AdEntry> importFromFile(String argStr) throws IOException {
        return importFromFile(Helper.createBuffReader(new File(argStr)));
    }

    public Collection<AdEntry> importFromFile(BufferedReader reader) throws IOException {
        List<AdEntry> ads = new ArrayList<AdEntry>();
        String line = null;

        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#"))
                continue;

            String[] entries = line.split("\t");
            // id
            AdEntry ad = new AdEntry(entries[0]);

            // title
            ad.setTitle(entries[1]);

            // keywords
            for (String str : entries[2].split(";")) {
                if (!str.isEmpty())
                    ad.addKeyword(str.trim());
            }

            // description
            ad.setDescription(entries[3]);

            // iconUrl
            ad.setIconUrl(entries[4]);

            // query + user            
            for (int i = 5; i + 1 < entries.length; i += 2) {
                ad.addQueryUserPair(entries[i], entries[i + 1]);
            }
            if (entries.length > 6)
                ads.add(ad);
            else
                logger.error("No query+user found in:" + ad);
        }

        deleteAll();
        update(ads);
        commit(1);
        return ads;
    }
}
