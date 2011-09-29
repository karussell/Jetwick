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

import java.io.Serializable;
import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.SubmitLink;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.target.basic.RedirectRequestTarget;
import org.apache.wicket.validation.validator.EmailAddressValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterException;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class LoginPanel extends Panel {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public LoginPanel(String id, final PageParameters oldParams) {
        super(id);
        final Input input = new Input();
        Label headerText;
        Model subLabel;
        WebMarkupContainer acceptToS = new WebMarkupContainer("acceptTos");
//        subLabel = new Model("Create New Account");
//        headerText = new Label("headerText", "Registration");
        subLabel = new Model("Login with Twitter");
        headerText = new Label("headerText", "Login");

        Form form = new Form("form") {

            @Override
            public void onSubmit() {
                MySession session = (MySession) getSession();
                session.setFormData(input.email, input.password);

                try {
                    PageParameters params = new PageParameters(oldParams);
                    params.add(Login.CALLBACK, "true");
                    String callbackUrl = JetwickApp.createAbsoluteUrl(urlFor(Login.class, params).toString());
                    logger.info("Clicked Login. Session=" + getSession().getId() + " CallbackUrl=" + callbackUrl);
                    String url = session.getTwitterSearch().oAuthLogin(callbackUrl);
//                    logger.info("request url:" + url);
//                    setRedirect(true);
                    getRequestCycle().setRequestTarget(new RedirectRequestTarget(url));                    
                } catch (TwitterException ex) {
                    error("Problem with twitter, cannot login. Please try again!");
                    logger.error("Problem with twitter cannot login!", ex);
                } catch (Exception ex) {
                    logger.error("Cannot login!", ex);
                }
                return;
            }
        };
        add(headerText);
        add(form);
        form.add(new TextField("email", new PropertyModel(input, "email")).setRequired(true).
                add(EmailAddressValidator.getInstance()));
//        form.add(new PasswordTextField("password", new PropertyModel(input, "password")).add(StringValidator.minimumLength(6)));

        form.add(new SubmitLink("submitbtn").add(new AttributeAppender("value", true, subLabel, " ")));

        acceptToS.add(new CheckBox("cb", new PropertyModel(input, "acceptTos")).setRequired(true));
        form.add(acceptToS);
    }

    public static class Input implements Serializable {

        String email;
        String password;
        boolean acceptTos;

        @Override
        public String toString() {
            return "email:" + email + " pw:" + password + " tos:" + acceptTos;
        }
    }
}
