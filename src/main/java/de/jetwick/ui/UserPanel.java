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

import de.jetwick.data.YUser;
import de.jetwick.tw.MyTweetGrabber;
import de.jetwick.tw.TwitterSearch;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxFallbackLink;
import org.apache.wicket.extensions.ajax.markup.html.IndicatingAjaxLink;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterException;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class UserPanel extends Panel {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private boolean started = false;

    public UserPanel(String id, final YUser user, final MyTweetGrabber grabber) {
        super(id);

        add(new Label("loginText", "Hello " + user.getRealName() + "!"));
        add(new Link("logout") {

            @Override
            public void onClick() {
                onLogout();
            }
        });
        add(new AjaxFallbackLink("showTweets") {

            @Override
            public void onClick(AjaxRequestTarget target) {
                onShowTweets(target, user.getScreenName());
            }
        });
        add(new IndicatingAjaxLink("grabTweets") {

            @Override
            public void onClick(AjaxRequestTarget target) {
                if (!started) {
                    logger.info("Grab tweets for " + user.getScreenName());
                    started = true;
                    int savedTweets = grabber.archivize();
                    if (grabber.getException() != null) {
                        logger.error("Error while storing archive", grabber.getException());
                        String msg = TwitterSearch.getMessage(grabber.getException());
                        if (msg.length() > 0)
                            error(msg);
                        else
                            error("Couldn't process your request. Please contact admin or twitter.com/jetwick if problem remains!");
                    } else
                        info(user.getRealName() + " " + savedTweets + " tweets were stored. In approx. 5min they will be searchable.");
                    started = false;
                }

                onGrabTweets(target);
            }
        });
    }

    public void onGrabTweets(AjaxRequestTarget target) {
    }

    public void onShowTweets(AjaxRequestTarget target, String user) {
    }

    public void onLogout() {
    }
}
