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

package de.jetwick.wikipedia;

import org.apache.wicket.Component;
import org.apache.wicket.extensions.ajax.markup.html.AjaxLazyLoadPanel;
import org.apache.wicket.markup.html.basic.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is without custom html! See OneLineAdPanel
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class WikipediaLazyLoadPanel extends AjaxLazyLoadPanel {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private String searchQuery;
    private String lang;

    public WikipediaLazyLoadPanel(String id) {
        super(id);
    }

    // hide indicator!
    @Override
    public Component getLoadingComponent(String markupId) {
        return new Label(markupId, "<span/>").setEscapeModelStrings(false);
    }

    @Override
    public Component getLazyLoadComponent(String markupId) {
        WikipediaPanel wikiPanel = new WikipediaPanel(markupId);

        try {
            wikiPanel.update(searchQuery, lang);
        } catch (Exception ex) {
            logger.error("Couldn't query wikipedia!", ex);
        }

        return wikiPanel;
    }

    public void setParams(String searchQuery, String language) {
        this.searchQuery = searchQuery;
        lang = language;
    }
}
