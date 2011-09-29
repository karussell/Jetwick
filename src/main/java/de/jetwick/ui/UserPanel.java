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

import de.jetwick.data.JUser;
import de.jetwick.tw.TwitterSearch;
import de.jetwick.util.Helper;
import java.util.Collection;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxFallbackLink;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class UserPanel extends Panel {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private String imageUrl = "img/jetwick-twitter.png";
    private String profileLink = "http://jetsli.de/";
    private String title = "Jetslide News Reader";

    public UserPanel(String id, final TweetSearchPage tweetSearchRef) {
        super(id);

        ExternalLink profileImageLink = new ExternalLink("profileImageLink", new PropertyModel(this, "profileLink"));
        profileImageLink.add(new ContextImage("profileImage", new PropertyModel(this, "imageUrl")));
        profileImageLink.add(new AttributeAppender("title", new PropertyModel(this, "title"), " "));
        add(profileImageLink);

        add(new WebMarkupContainer("gojetslideLink") {

            @Override
            public boolean isVisible() {
                return !tweetSearchRef.getMySession().hasLoggedIn();
            }
        });
        Link loginLink = new Link("loginLink") {

            @Override
            public void onClick() {
                setResponsePage(Login.class);
            }
        };
        add(loginLink);

        if (!tweetSearchRef.getMySession().hasLoggedIn()) {
            add(new WebMarkupContainer("loginContainer").setVisible(false));
            add(new AttributeModifier("class", "logged-in", new Model("logged-out")));
            return;
        }

        title = tweetSearchRef.getMySession().getUser().getScreenName();
        imageUrl = tweetSearchRef.getMySession().getUser().getProfileImageUrl();
        profileLink = Helper.TURL + "/" + title;
        loginLink.setVisible(false);
        WebMarkupContainer container = new WebMarkupContainer("loginContainer") {

            {
                final JUser user = tweetSearchRef.getMySession().getUser();
                String name = user.getRealName();
                if (name == null)
                    name = user.getScreenName();
                add(new Label("loginText", "Hi " + name + "!"));
                add(new Link("logout") {

                    @Override
                    public void onClick() {
                        onLogout();
                    }
                });

                add(new AjaxFallbackLink("showHomeline") {

                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        onHomeline(target, user.getScreenName());
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

                final GrabTweetsDialog dialog = new GrabTweetsDialog(modalW.getContentId(), user.getScreenName()) {

                    @Override
                    public TwitterSearch getTwitterSearch() {
                        return tweetSearchRef.getTwitterSearch();
                    }

                    @Override
                    public void updateAfterAjax(AjaxRequestTarget target) {
                        UserPanel.this.updateAfterAjax(target);
                    }

                    @Override
                    public void onClose(AjaxRequestTarget target) {
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
            }
        };
        add(container);
    }

    protected Collection<String> getUserChoices(String input) {
        throw new RuntimeException();
    }

    public void updateAfterAjax(AjaxRequestTarget target) {
    }

    public void onShowTweets(AjaxRequestTarget target, String user) {
    }

    public void onHomeline(AjaxRequestTarget target, String user) {
    }

    public void onLogout() {
    }
}
