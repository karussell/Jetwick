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
import java.util.Collection;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxFallbackLink;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.behavior.HeaderContributor;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.IHeaderContributor;
import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class UserPanel extends Panel {

    private final Logger logger = LoggerFactory.getLogger(getClass());

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

        final ModalWindow modalW = new ModalWindow("userModal");
        add(modalW);
        add(new AjaxLink("grabTweets") {

            @Override
            public void onClick(AjaxRequestTarget target) {
                modalW.show(target);
            }
        });
        modalW.setTitle("Specify the user and the number of tweets you want to grab");
        final GrabTweetsDialog dialog = new GrabTweetsDialog(modalW.getContentId(), user.getScreenName(), grabber) {

            @Override
            public void onFinish(AjaxRequestTarget target) {
                UserPanel.this.onFinish(target);
                modalW.close(target);
            }

            @Override
            protected Collection<String> getUserChoices(String input) {
                return UserPanel.this.getUserChoices(input);
            }
        };

        modalW.setContent(dialog);
        modalW.setCloseButtonCallback(new ModalWindow.CloseButtonCallback() {

            @Override
            public boolean onCloseButtonClicked(AjaxRequestTarget target) {
                logger.info("cancel grabber archiving thread!");
                dialog.interruptGrabber();
                modalW.close(target);
                return true;
            }
        });
        modalW.setWindowClosedCallback(new ModalWindow.WindowClosedCallback() {

            @Override
            public void onClose(AjaxRequestTarget target) {
                logger.info("closed dialog");
            }
        });
        modalW.setCookieName("user-modal");

        // disable js confirmation
        HeaderContributor c = new HeaderContributor(new IHeaderContributor() {

            @Override
            public void renderHead(IHeaderResponse response) {
                response.renderJavascriptReference("Wicket.Window.unloadConfirmation = true;");
            }
        });
        add(c);
    }

    protected Collection<String> getUserChoices(String input) {
        throw new RuntimeException();
    }

    public void onFinish(AjaxRequestTarget target) {
    }

    public void onShowTweets(AjaxRequestTarget target, String user) {
    }

    public void onLogout() {
    }
}
