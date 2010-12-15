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

import de.jetwick.solr.JetwickQuery;
import de.jetwick.ui.util.DefaultFocusBehaviour;
import de.jetwick.ui.util.MyAutoCompleteTextField;
import de.jetwick.util.Helper;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.wicket.PageParameters;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.autocomplete.AutoCompleteSettings;
import org.apache.wicket.extensions.ajax.markup.html.autocomplete.AutoCompleteTextField;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class SearchBox extends Panel {

    private String query;
    private String userName;
    private AutoCompleteTextField queryTF;
//    private TextField queryTF;

    public SearchBox(String id) {
        super(id);

        final Form form = new Form("searchform") {

            @Override
            public void onSubmit() {
                PageParameters params = new PageParameters();
                if (query != null && !query.isEmpty())
                    params.add("q", query);
                if (userName != null && !userName.isEmpty())
                    params.add("u", userName);

//                setResponsePage(HomePage.class, params);
                setResponsePage(getApplication().getHomePage(), params);
            }
        };
        form.setMarkupId("queryform");
        add(form);

        form.add(new BookmarkablePageLink("homelink", HomePage.class));
        Model hrefModel = new Model() {

            @Override
            public Serializable getObject() {
                String paramStr = "";
                String txt = "";

                if (userName != null && !userName.isEmpty()) {
                    paramStr += "u=" + userName;
                    txt += "@" + userName + " ";
                }

                if (query != null && !query.isEmpty()) {
                    if (!paramStr.isEmpty()) {
                        paramStr += "&";
                        txt += "and ";
                    }

                    paramStr += "q=" + query;
                    txt += query + " ";
                }

                if (!paramStr.isEmpty())
                    paramStr = "?" + paramStr;

                if (!txt.isEmpty())
                    txt = "about " + txt;
                else
                    txt = "about any topic at ";

                return Helper.toTwitterStatus(Helper.twitterUrlEncode("News " + txt
                        + "http://jetwick.com/" + paramStr));
            }
        };

        form.add(new ExternalLink("tweetQuery", hrefModel));
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
        form.add(userTF);
    }

    public void init(SolrQuery solrQuery) {
        this.query = JetwickQuery.extractNonNullQueryString(solrQuery);
        this.userName = JetwickQuery.extractUserName(solrQuery);
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
}
