/*
 *  Copyright 2010 Peter Karich jetwick_@_pannous_._info
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetwick.ui;

import de.jetwick.util.Helper;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.panel.FeedbackPanel;

/**
 *
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class OfflinePage extends JetwickPage {
    private String password;

    public OfflinePage(PageParameters params) {
        String pw = params.getString(TweetSearchPage.PASSWORD);
        if(!Helper.isEmpty(pw))
            info("Your invitation code is wrong. Please Click on the link below to get one!");
        
        add(new FeedbackPanel("feedback"));
        
//        TextField queryTextField = new TextField("textField", new PropertyModel(this, "password"));                
//        Form form= new Form("pwForm") {
//
//            @Override
//            protected void onSubmit() {                
//                PageParameters pp = new PageParameters();
//                pp.put(Jetslide.PASSWORD, password);
//                setResponsePage(Jetslide.class, pp);
//            }            
//        };        
//        form.add(queryTextField);
//        add(form);
    }    
    
    public String getPassword() {
        return password;
    }

    public void setPassword(String str) {
        password = str;
    }
}
