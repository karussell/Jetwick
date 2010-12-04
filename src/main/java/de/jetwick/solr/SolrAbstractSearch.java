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

import de.jetwick.tw.TweetDetector;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BinaryRequestWriter;
import org.apache.solr.client.solrj.impl.BinaryResponseParser;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.impl.StreamingUpdateSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public abstract class SolrAbstractSearch {

    protected SolrServer server;
    protected int termMinFrequency = 2;
    protected int langMinFrequency = 10;

    public SolrAbstractSearch() {
    }

    public SolrServer createServer(String url, String login, String pw, boolean streaming) {
        try {
            CommonsHttpSolrServer tmpServ;
            if (streaming) {
                tmpServ = new StreamingUpdateSolrServer(url, 500, 1);
            } else {
                tmpServ = new CommonsHttpSolrServer(url);
            }
            if (login != null && pw != null) {
//                StreamingUpdateSolrServer tmpServ = new StreamingUpdateSolrServer(url, 400, 2);

                HttpClient client = tmpServ.getHttpClient();
                AuthScope scope = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, null, null);
                client.getState().setCredentials(scope, new UsernamePasswordCredentials(login, pw));

                // workaround! see https://issues.apache.org/jira/browse/SOLR-1238
                client.getParams().setAuthenticationPreemptive(true);
            }

            setServer(tmpServ);
            return tmpServ;
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected void setServer(SolrServer server) {
        this.server = server;
        if (server instanceof CommonsHttpSolrServer)
            configure((CommonsHttpSolrServer) server);
    }

    public void configure(CommonsHttpSolrServer server) {
        server.setRequestWriter(new BinaryRequestWriter());
//        server.setParser(new XMLResponseParser());
        server.setParser(new BinaryResponseParser());

        // allowCompression defaults to false. 'true' could be even slower (more cpu intensive)!!
        // use compression="on" in server.xml of tomcat
//        server.setAllowCompression(true);

//        server.setSoTimeout(1000);  // socket read timeout
//        server.setMaxRetries(0);    // defaults to 0.  > 1 not recommended.
    }

    public void setTermMinFrequency(int limit) {
        this.termMinFrequency = limit;
    }

    public void setLangMinFrequency(int langFrequencyLimit) {
        this.langMinFrequency = langFrequencyLimit;
    }

    public abstract QueryResponse search(SolrQuery q) throws SolrServerException;

    public SolrQuery attachPagability(SolrQuery query, int page, int hitsPerPage) {
        return query.setStart(page * hitsPerPage).setRows(hitsPerPage);
    }

    public void commit() {
        try {
            server.commit();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public UpdateResponse optimize(int maxSegments) {
        try {
            return server.optimize(true, true, maxSegments);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * @param maxSegments use a higher maxSegments to reduce optimize time
     */
    public void commit(int maxSegments) {
        try {
            server.commit();
            if (maxSegments > 0)
                server.optimize(true, true, maxSegments);

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void rollback() {
        try {
            server.rollback();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    void deleteAll() {
        try {
            server.deleteByQuery("*:*");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void getInfo(StringBuilder sb) {
        try {
            SolrQuery query = new SolrQuery("*:*").setQueryType("simple");
            long ret = search(query).getResults().getNumFound();
            sb.append("\n===== SOLR =====\n");
            sb.append("num of all docs:  ");
            sb.append("\t");
            sb.append(ret);
            sb.append("\n");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /*
     * use TermCreateCommand instead
     */
    @Deprecated
    static List<String> filterLanguages(final Map<String, Integer> langs) {
        if (langs.size() == 0)
            return Collections.EMPTY_LIST;

        List<Entry<String, Integer>> sorted = new ArrayList<Entry<String, Integer>>(langs.entrySet());
        Collections.sort(sorted, new Comparator<Entry<String, Integer>>() {

            @Override
            public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
                int i1 = o1.getValue();
                int i2 = o2.getValue();
                if (i1 < i2)
                    return 1;
                else if (i1 > i2)
                    return -1;
                else
                    return 0;
            }
        });

        List<String> res = new ArrayList<String>();
        int max = 0;
        for (Entry<String, Integer> entry : sorted) {
            // the first result is the maximal
            if (max < entry.getValue())
                max = entry.getValue();

            // skip the rest if less than ~3% of the first result
            if (entry.getValue() <= max / 30f)
                break;

            if (langs.size() > 1 && TweetDetector.UNKNOWN_LANG.equals(entry.getKey()))
                // at least one known language is included so continue
                continue;

            res.add(entry.getKey());
        }

        return res;
    }
}
