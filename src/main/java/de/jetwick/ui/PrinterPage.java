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

package de.jetwick.ui;

import de.jetwick.data.JTweet;
import de.jetwick.tw.Extractor;
import de.jetwick.util.Helper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;

public class PrinterPage extends JetwickPage {

    private List<JTweet> results = new ArrayList<JTweet>();

    public PrinterPage() {
        add(new ListView("users", results) {

            @Override
            public void populateItem(final ListItem item) {
                final JTweet tw = (JTweet) item.getModelObject();

                item.add(new ExternalLink("userName", Helper.JETWICK_URL + "?user="
                        + Helper.urlEncode(tw.getFromUser().getScreenName()),
                        tw.getFromUser().getScreenName()));

                item.add(new ExternalLink("statusLink",
                        Helper.toTwitterHref(tw.getFromUser().getScreenName(), tw.getTwitterId())));

                Label label = new Label("tweetText", new Extractor().setTweet(tw).run().toString());
                label.setEscapeModelStrings(false);
                item.add(label);
            }
        });
    }

    public void setResults(Collection<JTweet> tweets) {
        results.clear();
        results.addAll(tweets);
    }
}
