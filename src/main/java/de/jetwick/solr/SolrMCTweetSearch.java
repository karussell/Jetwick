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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.CoreAdminParams.CoreAdminAction;

/**
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SolrMCTweetSearch extends SolrTweetSearch {

    private String url;
    private Set<String> activeCores = new LinkedHashSet<String>();
    private SolrServer adminServer;
//    SolrMCTweetSearch(TestHarness h, String url) {
//        super(new EmbeddedSolrServer(h.getCoreContainer(), h.getCore().getName()));
//        createServer(url, null, null, false);
//    }

    public SolrMCTweetSearch(Configuration cfg) {
        super(cfg);

        // read solr.xml to get cores
        addCore("core0");
        addCore("core1");
    }

    @Override
    public SolrServer createServer(String extUrl, String login, String pw, boolean streaming) {        
        adminServer = super.createServer(extUrl, login, pw, streaming);
        // slightly different url so appaend the core
        // http://localhost:8983/solr/core0
        SolrServer queryServer = super.createServer(extUrl.endsWith("/") ? extUrl + "core0" : extUrl + "/core0", login, pw, streaming);
        this.url = extUrl;
        if (url.startsWith("http://"))
            url = url.substring("http://".length());

        if (url.endsWith("/"))
            url = url.substring(0, url.length() - 1);

        return queryServer;
    }

    public void createCore(String newCore) {
    }

    public void mergeCores(String... cores) {
    }

    public boolean addCore(String core) {
        // TODO createCoreIfNeeded(indexName, dataDir, server);
        return activeCores.add(core);
    }

    public String getShardParam() {
        // localhost:8983/solr/core0,localhost:8983/solr/core1,
        String tmp = "";
        for (String t : activeCores) {
            tmp += url + "/" + t + ",";
        }
        return tmp;
    }

    @Override
    public QueryResponse search(Collection<SolrUser> users, SolrQuery query) throws SolrServerException {
        query.setParam("shards", getShardParam());
        return super.search(users, query);
    }

    public boolean createCoreIfNeeded(String coreName) {
        try {
            if (!isExisting(coreName)) {
                // Create the core
                // http://localhost:8983/solr/admin/cores?action=CREATE&name=core1&instanceDir=mc&dataDir=core1
                CoreAdminRequest.Create create = new CoreAdminRequest.Create();
                create.setCoreName(coreName);
                create.setInstanceDir("mc");
                create.setDataDir(coreName);
                create.process(adminServer);
                return true;
            }
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isExisting(String coreName) {
        try {
            // SolrJ provides no direct method to check if a core exists, but getStatus will
            // return an empty list for any core that doesn't.
            CoreAdminResponse statusResponse = CoreAdminRequest.getStatus(coreName, adminServer);
            return statusResponse.getCoreStatus(coreName).size() > 0;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void swapCores(String liveCore, String coreName) {
        CoreAdminRequest request = new CoreAdminRequest();
        request.setAction(CoreAdminAction.SWAP);
        request.setCoreName(coreName);
        request.setOtherCoreName(liveCore);
        try {
            request.process(server);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
//    We also use SolrJ, and have a dynamically created Core capability - where we don't know in advance what the Cores will be that we require.
//
//We almost always do a complete index build, and if there's a previous instance of that index, it needs to be available during a complete index build, so we have two cores per index, and switch them as required at the end of an indexing run.
//
//Here's a summary of how we do it (we're in an early prototype / implementation right now - this isn't  production quality code - as you can tell from our voluminous javadocs on the methods...)
//
//1) Identify if the core exists, and if not, create it:
//
//   /**
//     * This method instantiates two SolrServer objects, solr and indexCore.  It requires that
//     * indexName be set before calling.
//     */
//    private void initSolrServer() throws IOException
//    {
//        String baseUrl = "http://localhost:8983/solr/";
//        solr = new CommonsHttpSolrServer(baseUrl);
//
//        String indexCoreName = indexName + SolrConstants.SUFFIX_INDEX; // SUFIX_INDEX = "_INDEX"
//        String indexCoreUrl = baseUrl + indexCoreName;
//
//        // Here we create two cores for the indexName, if they don't already exist - the live core used
//        // for searching and a second core used for indexing. After indexing, the two will be switched so the
//        // just-indexed core will become the live core. The way that core swapping works, the live core will always
//        // be named [indexName] and the indexing core will always be named [indexname]_INDEX, but the
//        // dataDir of each core will alternate between [indexName]_1 and [indexName]_2.
//        createCoreIfNeeded(indexName, indexName + "_1", solr);
//        createCoreIfNeeded(indexCoreName, indexName + "_2", solr);
//        indexCore = new CommonsHttpSolrServer(indexCoreUrl);
//    }
//
//
//   /**
//     * Create a core if it does not already exists. Returns true if a new core was created, false otherwise.
//     */
//    private boolean createCoreIfNeeded(String coreName, String dataDir, SolrServer server) throws IOException
//    {
//        boolean coreExists = true;
//        try
//        {
//            // SolrJ provides no direct method to check if a core exists, but getStatus will
//            // return an empty list for any core that doesn't.
//            CoreAdminResponse statusResponse = CoreAdminRequest.getStatus(coreName, server);
//            coreExists = statusResponse.getCoreStatus(coreName).size() > 0;
//            if(!coreExists)
//            {
//                // Create the core
//                LOG.info("Creating Solr core: " + coreName);
//                CoreAdminRequest.Create create = new CoreAdminRequest.Create();
//                create.setCoreName(coreName);
//                create.setInstanceDir(".");
//                create.setDataDir(dataDir);
//                create.process(server);
//            }
//        }
//        catch (SolrServerException e)
//        {
//            e.printStackTrace();
//        }
//        return !coreExists;
//    }
//
//
//2) Do the index, clearing it first if it's a complete rebuild:
//
//	[snip]
//        if (fullIndex)
//        {
//            try
//            {
//                indexCore.deleteByQuery("*:*");
//            }
//            catch (SolrServerException e)
//            {
//                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//            }
//        }
//	[snip]
//
//	various logic, then (we submit batches of 100 :
//
//	[snip]
//            List<SolrInputDocument> docList = b.getSolrInputDocumentList();
//	      UpdateResponse rsp;
//            try
//            {
//                rsp = indexCore.add(docList);
//                rsp = indexCore.commit();
//            }
//            catch (IOException e)
//            {
//                LOG.warn("Error commiting documents", e);
//            }
//            catch (SolrServerException e)
//            {
//                LOG.warn("Error commiting documents", e);
//            }
//	[snip]
//
//3) optimize, then swap cores:
//
//    private void optimizeCore()
//    {
//        try
//        {
//            indexCore.optimize();
//        }
//        catch(SolrServerException e)
//        {
//            LOG.warn("Error while optimizing core", e);
//        }
//        catch(IOException e)
//        {
//            LOG.warn("Error while optimizing core", e);
//        }
//    }
//
//    private void swapCores()
//    {
//        String liveCore = indexName;
//        String indexCore = indexName + SolrConstants.SUFFIX_INDEX; // SUFFIX_INDEX = "_INDEX"
//        LOG.info("Swapping Solr cores: " + indexCore + ", " + liveCore);
//        CoreAdminRequest request = new CoreAdminRequest();
//        request.setAction(CoreAdminAction.SWAP);
//        request.setCoreName(indexCore);
//        request.setOtherCoreName(liveCore);
//        try
//        {
//            request.process(solr);
//        }
//        catch (SolrServerException e)
//        {
//            e.printStackTrace();
//        }
//        catch (IOException e)
//        {
//            e.printStackTrace();
//        }
//    }
//
//
//And that's about it.
//
//You could adjust the above so there's only one core per index that you want - if you don't do complete reindexes, and don't need the index to always be searchable.
//
//Hope that helps...
//
//
//Bob Sandiford | Lead Software Engineer | SirsiDynix
//P: 800.288.8020 X6943 | Bob.Sandiford@sirsidynix.com
//www.sirsidynix.com
//
//
//> > -----Original Message-----
//> > From: Nizan Grauer [mailto:nizang@yahoo-inc.com]
//> > Sent: Tuesday, November 09, 2010 3:36 AM
//> > To: solr-user@lucene.apache.org
//> > Subject: Dynamic creating of cores in solr
//> >
//> > Hi,
//> >
//> > I'm not sure this is the right mail to write to, hopefully you can help
//> > or direct me to the right person
//> >
//> > I'm using solr - one master with 17 slaves in the server and using
//> > solrj as the java client
//> >
//> > Currently there's only one core in all of them (master and slaves) -
//> > only the cpaCore.
//> >
//> > I thought about using multi-cores solr, but I have some problems with
//> > that.
//> >
//> > I don't know in advance which cores I'd need -
//> >
//> > When my java program runs, I call for documents to be index to a
//> > certain url, which contains the core name, and I might create a url
//> > based on core that is not yet created. For example:
//> >
//> > Calling to index - http://localhost:8080/cpaCore  - existing core,
//> > everything as usual
//> > Calling to index -  http://localhost:8080/newCore - server realizes
//> > there's no core "newCore", creates it and indexes to it. After that -
//> > also creates the new core in the slaves
//> > Calling to index - http://localhost:8080/newCore  - existing core,
//> > everything as usual
//> >
//> > What I'd like to have on the server side to do is realize by itself if
//> > the cores exists or not, and if not  - create it
//> >
//> > One other restriction - I can't change anything in the client side -
//> > calling to the server can only make the calls it's doing now - for
//> > index and search, and cannot make calls for cores creation via the
//> > CoreAdminHandler. All I can do is something in the server itself
//> >
//> > What can I do to get it done? Write some RequestHandler?
//> > REquestProcessor? Any other option?
//> >
//> > Thanks, nizan
//
}
