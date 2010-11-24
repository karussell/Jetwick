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

import java.util.Iterator;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.autocomplete.AutoCompleteBehavior;
import org.apache.wicket.extensions.ajax.markup.html.autocomplete.AutoCompleteSettings;
import org.apache.wicket.extensions.ajax.markup.html.autocomplete.AutoCompleteTextField;
import org.apache.wicket.extensions.ajax.markup.html.autocomplete.IAutoCompleteRenderer;
import org.apache.wicket.model.IModel;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public abstract class MyAutoCompleteTextField<T> extends AutoCompleteTextField<T> {

    public MyAutoCompleteTextField(String id, AutoCompleteSettings settings) {
        super(id, settings);
    }

    public MyAutoCompleteTextField(String id, IModel<T> object, AutoCompleteSettings settings) {
        super(id, object, settings);
    }

    /*
     * for instant changes
     */
    protected abstract void onSelectionChange(AjaxRequestTarget target, String newValue);

    @Override
    protected AutoCompleteBehavior<T> newAutoCompleteBehavior(IAutoCompleteRenderer<T> renderer,
            AutoCompleteSettings settings) {
        return new MyAutoCompleteBehavior(renderer, settings) {

            private static final long serialVersionUID = 1L;

            @Override
            protected Iterator<T> getChoices(String input) {
                return MyAutoCompleteTextField.this.getChoices(input);
            }

            @Override
            protected void onSelectionChange(AjaxRequestTarget target, String newValue) {
                MyAutoCompleteTextField.this.onSelectionChange(target, newValue);
            }
        };
    }
}
