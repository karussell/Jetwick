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

import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.AjaxEditableLabel;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.util.convert.IConverter;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class MyEditableLabel<T> extends AjaxEditableLabel<T> {

    public MyEditableLabel(String id, IModel model) {
        super(id, model);
    }

    public MyEditableLabel(String id) {
        super(id);
    }

    @Override
    public void onEdit(AjaxRequestTarget target) {
        super.onEdit(target);
    }

//    protected FormComponent<T> newEditor(MarkupContainer parent, String componentId, IModel<T> model) {
//        TextField<T> editor = new TextField<T>(componentId, model) {
//
//            private static final long serialVersionUID = 1L;
//
//            @Override
//            public IConverter getConverter(Class<?> type) {
//                IConverter c = AjaxEditableLabel.this.getConverter(type);
//                return c != null ? c : super.getConverter(type);
//            }
//
//            @Override
//            protected void onModelChanged() {
//                super.onModelChanged();
//                AjaxEditableLabel.this.onModelChanged();
//            }
//
//            @Override
//            protected void onModelChanging() {
//                super.onModelChanging();
//                AjaxEditableLabel.this.onModelChanging();
//            }
//        };
//        editor.setOutputMarkupId(true);
//        editor.setVisible(false);
//        editor.add(new EditorAjaxBehavior());
//        return editor;
//    }
}
