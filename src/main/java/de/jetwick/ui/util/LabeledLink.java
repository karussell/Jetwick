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

package de.jetwick.ui.util;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.IndicatingAjaxFallbackLink;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;

/**
 * map to div instead <a href...></a> !!
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class LabeledLink extends Panel {

    public LabeledLink(String id, String label) {
        this(id, null, new Model(label), true);
    }

    public LabeledLink(String id, String label, boolean ajaxified) {
        this(id, null, new Model(label), ajaxified);
    }

    public LabeledLink(String id, Model model, Model labelModel) {
        this(id, model, labelModel, true);
    }

    public LabeledLink(String id, Model model, Model labelModel, boolean ajaxified) {
        super(id);

        Link link;
        if (ajaxified) {
            link = new IndicatingAjaxFallbackLink("linkId", model) {

                @Override
                public void onClick(AjaxRequestTarget target) {
                    LabeledLink.this.onClick(target);
                }
            };
        } else
            link = new Link("linkId", model) {

                @Override
                public void onClick() {
                    LabeledLink.this.onClick(null);
                }
            };
        add(link.add(new Label("labelId", labelModel)));
    }

    public void onClick(AjaxRequestTarget target) {
    }
}
