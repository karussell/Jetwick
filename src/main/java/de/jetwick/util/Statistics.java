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
package de.jetwick.util;

import com.google.api.translate.Language;
import com.google.api.translate.Translate;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Module;
import de.jetwick.config.DefaultModule;
import de.jetwick.data.JTag;
import de.jetwick.es.ElasticTweetSearch;
import de.jetwick.data.JTweet;
import de.jetwick.data.JUser;
import de.jetwick.es.ElasticTagSearch;
import de.jetwick.es.TweetQuery;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.elasticsearch.action.admin.indices.optimize.OptimizeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Execute eg. via
 * ./myjava  de.jetwick.util.Statistics exportNoiseWords=solr/conf/stopwords.txt
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class Statistics {

    private static Logger logger = LoggerFactory.getLogger(Statistics.class);

    public static void main(String[] args) throws Exception {
        Map<String, String> map = Helper.parseArguments(args);
        logger.info("arguments:" + map);
        if (args.length == 0)
            map.put("print", "timetabling");

        new Statistics().start(map);
    }
    @Inject
    private ElasticTweetSearch tweetSearch;
    @Inject
    private ElasticTagSearch tagSearch;

    public Statistics() {
        Module module = new DefaultModule();
        Guice.createInjector(module).injectMembers(this);
    }

    public void start(Map<String, String> map) throws Exception {
        String argStr = map.get("optimize");
        if (argStr != null) {
            int segments = 1;
            logger.info("Start optimizing for twindex");
            OptimizeResponse rsp = tweetSearch.optimize(tweetSearch.getIndexName(), segments);
            logger.info("Optimized twindex to " + segments + " segments for " + rsp.getSuccessfulShards() + "/" + rsp.getTotalShards() + " shards.\n Now uindex");
            rsp = tweetSearch.optimize(tweetSearch.getIndexName(), segments);
            logger.info("Optimized uindex  to " + segments + " segments for " + rsp.getSuccessfulShards() + "/" + rsp.getTotalShards() + " shards.");
        }

        argStr = map.get("listTweets");
        if (argStr != null) {
            if ("true".equals(argStr))
                argStr = "**:*";

            List<JUser> list = new ArrayList<JUser>();
            long ret = tweetSearch.search(list, new TweetQuery(argStr, false)).
                    getHits().getTotalHits();
            logger.info("Found: " + ret + " users. Returned: " + list.size());
            print(list);
            return;
        }

        // specify file via exportNoiseWords=stopwords.txt
        argStr = map.get("exportNoiseWords");
        if (argStr != null) {
            write(new TreeSet<String>(JTweet.NOISE_WORDS.keySet()), argStr);
            return;
        }

        argStr = map.get("importTags");
        if (argStr != null)
            importTags(map.get("tagFile"));

        argStr = map.get("clearPropertiesOfTags");
        if (argStr != null)
            clearPropertiesOfTags();

        argStr = map.get("readStopAndClear");
        if (argStr != null)
            readStopwords(JTweet.class.getResourceAsStream("noise_words_pt.txt"));//noise_words_fr.txt, lang_det_sp.txt

        argStr = map.get("translate");
        if (argStr != null)
            translate(Language.PORTUGUESE);
    }

    public void print(List list) {
        for (Object o : list) {
            System.out.println(o);
        }
    }

    public void importTags(String file) throws IOException {
        Set<String> newTags = new TreeSet<String>();
        for (String str : Helper.readFile(file)) {
            if (str.trim().length() > 1)
                newTags.add(JTag.toLowerCaseOnlyOnTerms(str.trim()));
        } // do only delete those where we don't have a new one
        // do only update tags which are new
        
        boolean ignoreSearchError = false;
        try {
            for (JTag tag : tagSearch.findAll(0, 1000)) {
                if (!newTags.contains(tag.getTerm()))
                    tagSearch.deleteByName(tag.getTerm());
                else
                    newTags.remove(tag.getTerm());
            }
        } catch (Exception ex) {
            ignoreSearchError = true;
            logger.info("Tag index seems to be not available or empty! Message:" + ex.getMessage());
        }
        
        tagSearch.addAll(newTags, true, ignoreSearchError);
        tagSearch.optimize();
        logger.info("Imported tag:" + newTags.size() + " all tags:" + tagSearch.findAll(0, 1000).size());
    }

    public void clearPropertiesOfTags() throws IOException {
        Set<JTag> newTags = new LinkedHashSet<JTag>();
        int counter = 0;
        for (JTag tag : tagSearch.findAll(0, 1000)) {
            counter++;
            newTags.add(tag.setMaxCreateTime(0L).setLastMillis(0).setQueryInterval(1000));
        }
        tagSearch.bulkUpdate(newTags, tagSearch.getIndexName(), true);
        tagSearch.optimize();
        logger.info(counter + " Updated:" + newTags.size() + " tags " + newTags);
    }

    public void write(Set<String> words, String file) throws Exception {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), Helper.UTF8));
        writer.write("# Written from YTweet via Statistics class! " + new Date());
        for (String str : words) {
            writer.write(str);
            writer.write("\n");
        }
        writer.close();
    }

    public void readStopwords(InputStream is) throws Exception {
        List<String> list = Helper.readFile(Helper.createBuffReader(is));
        Set<String> set = new TreeSet<String>();
        for (String str : list) {
            if (str.isEmpty() || str.startsWith("//"))
                continue;

            str = str.toLowerCase();
            if (str.contains(" "))
                for (String tmp : str.split(" ")) {
                    set.add(tmp.trim());
                }
            else
                set.add(str.trim());
        }

        for (String str : set) {
            System.out.println(str);
        }
    }

    public void translate(Language lang) throws Exception {
        List<String> list = Helper.readFile(Helper.createBuffReader(JTweet.class.getResourceAsStream("lang_det_en.txt")));
        Set<String> res = new TreeSet<String>();
        Set<String> cache = new LinkedHashSet<String>();
        int charCounter = 0;
        Translate.setHttpReferrer("http://jetwick.com");
        for (String str : list) {
            if (str.isEmpty() || str.startsWith("//"))
                continue;

            str = str.toLowerCase().trim();
            charCounter += str.length();
            cache.add(str);
            if (charCounter > 1500) {
                try {
                    String gTranslated = Translate.execute(cache.toString(), Language.ENGLISH, lang);
                    for (String tmp : gTranslated.split(",")) {
                        tmp = tmp.toLowerCase().trim().replaceAll("\\[", "").replaceAll("\\]", "");
                        res.add(tmp);
                    }
//                    System.out.println(tmp);
                } catch (Exception ex) {
                    logger.error("Cannot translate " + cache.size() + " lines", ex);
                }

                charCounter = 0;
                cache.clear();
            }
        }

        System.out.println("=======================\n\n");

        for (String str : res) {
            System.out.println(str);
        }
    }
}
