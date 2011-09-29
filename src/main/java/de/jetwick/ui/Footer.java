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
package de.jetwick.ui;

import de.jetwick.util.Helper;
import org.apache.wicket.PageParameters;
import org.apache.wicket.ResourceReference;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.link.ResourceLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.util.value.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class Footer extends Panel {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private ResourceReference rssRef;
    private ValueMap vm;

    public Footer(String id, PageParameters pp, String title, String url) {
        super(id);
        add(new ExternalLink("shareTwLink", Helper.getTwitterHref("Search your tweets!", url, "")));
        add(new ExternalLink("shareFbLink", Helper.toFacebookHref(url, title)));
        add(new ExternalLink("shareEmailLink", Helper.toEmailHref("alexia@techcrunch.com", "Jetslide News Reader 4 Geeks",
                "Hey,\n\nI found some nice articles via Jetslide:\n\n" + Helper.urlEncode(url))));

        vm = new ValueMap(pp);
        vm.put(TweetSearchPage.TIME, TweetSearchPage.TIME_TODAY);
        rssRef = new ResourceReference("rssFeed");
        add(new ResourceLink("rssLink", rssRef, vm));
    }

    public ResourceReference getRssRef() {
        return rssRef;
    }

    public ValueMap getRssValueMap() {
        return vm;
    }
}
