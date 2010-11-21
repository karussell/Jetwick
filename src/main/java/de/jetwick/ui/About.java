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

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.link.Link;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class About extends WebPage {

    public About() {
//        add(new Link("accountLink") {
//
//            @Override
//            public void onClick() {
//                PageParameters pp = new PageParameters();
//                pp.add("u", "time*g");
//                setResponsePage(HomePage.class, pp);
//            }
//        });
        add(new Link("ownLink") {

            @Override
            public void onClick() {
                PageParameters pp = new PageParameters();
                pp.add("q", "java");
                pp.add("u", "timetabling");
                setResponsePage(HomePage.class, pp);
            }
        });
    }
}
