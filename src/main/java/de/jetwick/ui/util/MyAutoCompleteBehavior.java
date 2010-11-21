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

import org.apache.wicket.RequestCycle;
import org.apache.wicket.ResourceReference;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.autocomplete.AutoCompleteBehavior;
import org.apache.wicket.extensions.ajax.markup.html.autocomplete.AutoCompleteSettings;
import org.apache.wicket.extensions.ajax.markup.html.autocomplete.IAutoCompleteRenderer;
import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.markup.html.resources.JavascriptResourceReference;
import org.apache.wicket.util.string.Strings;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public abstract class MyAutoCompleteBehavior<T> extends AutoCompleteBehavior<T> {

    private static final ResourceReference AUTOCOMPLETE_JS = new JavascriptResourceReference(
            MyAutoCompleteBehavior.class, "wicket-autocomplete.js");

    public MyAutoCompleteBehavior(IAutoCompleteRenderer<T> renderer, AutoCompleteSettings settings) {
        super(renderer, settings);
    }

    public MyAutoCompleteBehavior(IAutoCompleteRenderer<T> renderer, boolean preselect) {
        super(renderer, preselect);
    }

    public MyAutoCompleteBehavior(IAutoCompleteRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        // do not insert duplicate javascript files
        response.renderJavascriptReference(AUTOCOMPLETE_JS);
        final String elementId = getComponent().getMarkupId();

        String indicatorId = findIndicatorId();
        if (Strings.isEmpty(indicatorId)) {
            indicatorId = "null";
        } else {
            indicatorId = "'" + indicatorId + "'";
        }

        String initJS = String.format("new Wicket.AutoComplete('%s','%s',%s,%s);", elementId,
                getCallbackUrl(), constructSettingsJS(), indicatorId);
        response.renderOnDomReadyJavascript(initJS);
    }

    @Override
    protected void respond(AjaxRequestTarget target) {
        final RequestCycle requestCycle = RequestCycle.get();
        String val = requestCycle.getRequest().getParameter("sv");
//        String val = requestCycle.getRequest().getRequestParameters().getParameterValue("sv").toString();
        if (val != null)
            onSelectionChange(target, val);
        else {
            val = requestCycle.getRequest().getParameter("q");
//            val = requestCycle.getRequest().getRequestParameters().getParameterValue("q").toString();
            onRequest(val, requestCycle);
        }
    }

    protected abstract void onSelectionChange(AjaxRequestTarget target, String val);
}
