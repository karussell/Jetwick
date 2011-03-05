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

import de.jetwick.ui.util.DefaultFocusBehaviour;
import de.jetwick.ui.util.MyAutoCompleteTextField;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.apache.wicket.PageParameters;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.autocomplete.AutoCompleteSettings;
import org.apache.wicket.extensions.ajax.markup.html.autocomplete.AutoCompleteTextField;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.Radio;
import org.apache.wicket.markup.html.form.RadioGroup;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.odlabs.wiquery.core.commons.IWiQueryPlugin;
import org.odlabs.wiquery.core.commons.WiQueryResourceManager;
import org.odlabs.wiquery.core.effects.EffectBehavior;
import org.odlabs.wiquery.core.javascript.JsStatement;
import org.odlabs.wiquery.ui.effects.BounceEffect;
import org.odlabs.wiquery.ui.effects.EffectsHelper;
import org.odlabs.wiquery.ui.effects.ExplodeEffect;
import org.odlabs.wiquery.ui.effects.ExplodeEffect.Mode;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SearchBox extends Panel implements IWiQueryPlugin {

    public static final String ALL = "all", USER = "user", FRIENDS = "friends";
    public static final List<String> SEARCHTYPES = Arrays.asList(ALL, FRIENDS, USER);
    private Integer selectedIndex;
    private String query;
    private String userName;
    private AutoCompleteTextField queryTF;
    private final Form form;

    // for test
    public SearchBox(String id) {
        this(id, null, null);
    }

    public void setSearchType(String type) {
        if (type != null)
            selectedIndex = SEARCHTYPES.indexOf(type);
        else
            selectedIndex = 0;
    }

    public SearchBox(String id, final String loggedInUser, String searchTypeAsStr) {
        super(id);

        setSearchType(searchTypeAsStr);
        final RadioGroup rg = new RadioGroup("searchTypes", new PropertyModel(this, "selectedIndex"));

        final Button buttonRight = new Button("submitbutton") {

            @Override
            public void onSubmit() {
                setResponsePage(getApplication().getHomePage(), getParams(query, selectedIndex, userName, loggedInUser));
            }
        };
        final Button bttnLeft = new Button("submitbuttonleft") {

            @Override
            public void onSubmit() {
                buttonRight.onSubmit();
            }
        };
        form = new Form("searchform") {

            @Override
            public void onSubmit() {
                buttonRight.onSubmit();
            }
        };
        
//        buttonRight.add(new EffectBehavior(new BounceEffect()));
        form.add(buttonRight);
        form.add(bttnLeft);
        form.setMarkupId("queryform");
        add(form);

        AutoCompleteSettings config = new AutoCompleteSettings().setUseHideShowCoveredIEFix(false);
        config.setThrottleDelay(200);

        // connect the form's textfield with the java property        
        queryTF = new MyAutoCompleteTextField("textField",
                new PropertyModel(this, "query"), config) {

            @Override
            protected Iterator getChoices(String input) {
                return SearchBox.this.getQueryChoices(input).iterator();
            }

            @Override
            protected void onSelectionChange(AjaxRequestTarget target, String newValue) {
                SearchBox.this.onSelectionChange(target, newValue);
            }
        };
        queryTF.add(new DefaultFocusBehaviour());
        // autosubmit when user selects choice -> problem: the user text will be submitted although it should be cleared before submit
//        queryTF.add(new AjaxFormSubmitBehavior(form, "onchange") {
//
//            @Override
//            protected void onSubmit(AjaxRequestTarget target) {
//            }
//
//            @Override
//            protected void onError(AjaxRequestTarget target) {
//            }
//        });
        queryTF.setMarkupId("querybox");
        form.add(queryTF);

        MyAutoCompleteTextField userTF = new MyAutoCompleteTextField("userTextField",
                new PropertyModel(this, "userName"), config) {

            @Override
            protected Iterator getChoices(String input) {
                return SearchBox.this.getUserChoices(input).iterator();
            }

            @Override
            protected void onSelectionChange(AjaxRequestTarget target, String newValue) {
                //"Not supported yet."
            }
        };
        userTF.setMarkupId("userbox");

        rg.add(new Radio("0", new Model(0)).setMarkupId("sbnone"));
        rg.add(new Radio("1", new Model(1)).setMarkupId("sbfriends"));
        rg.add(new Radio("2", new Model(2)).setMarkupId("sbuser"));
        rg.add(userTF);
        form.add(rg);

        form.add(new BookmarkablePageLink("homelink", HomePage.class));
//        Model hrefModel = new Model() {
//
//            @Override
//            public Serializable getObject() {
//                String paramStr = "";
//                String txt = "";
//
//                if (userName != null && !userName.isEmpty()) {
//                    paramStr += "user=" + userName;
//                    txt += "@" + userName + " ";
//                }
//
//                if (query != null && !query.isEmpty()) {
//                    if (!paramStr.isEmpty()) {
//                        paramStr += "&";
//                        txt += "and ";
//                    }
//
//                    paramStr += "q=" + query;
//                    txt += query + " ";
//                }
//
//                if (!paramStr.isEmpty())
//                    paramStr = "?" + paramStr;
//
//                if (!txt.isEmpty())
//                    txt = "about " + txt;
//                else
//                    txt = "about any topic at ";
//
//                return Helper.toTwitterStatus(Helper.twitterUrlEncode("News " + txt
//                        + "http://jetwick.com/" + paramStr));
//            }
//        };

//        form.add(new ExternalLink("tweetQuery", hrefModel));                
    }

    public void init(String q, String u) {
        query = q;
        userName = u;
    }

    public String getUserName() {
        if (userName == null)
            userName = "";
        return userName;
    }

    public String getQuery() {
        if (query == null)
            query = "";

        return query;
    }

    protected void onSelectionChange(AjaxRequestTarget target, String str) {
    }

    protected Collection<String> getQueryChoices(String input) {
        throw new RuntimeException();
    }

    protected Collection<String> getUserChoices(String input) {
        throw new RuntimeException();
    }

    public PageParameters getParams(String tmpQuery, Integer tmpSelectedIndex, String tmpUserName, String tmpLoginUser) {
        PageParameters params = new PageParameters();
        if (tmpQuery != null && !tmpQuery.isEmpty())
            params.add("q", tmpQuery);

        if (tmpSelectedIndex == null || tmpSelectedIndex == 0) {
            if (tmpUserName != null)
                params.add("user", tmpUserName);
        } else if (tmpSelectedIndex == 2) {
            params.add("search", SEARCHTYPES.get(2));
            params.add("user", tmpUserName);
        } else if (tmpSelectedIndex == 1) {
            params.add("search", SEARCHTYPES.get(1));
//            params.add("user", tmpLoginUser);
        }

        return params;
    }

    @Override
    public void contribute(WiQueryResourceManager wiQueryResourceManager) {
        EffectsHelper.bounce(wiQueryResourceManager);        
    }

    @Override
    public JsStatement statement() {
        return new JsStatement();
    }
}
