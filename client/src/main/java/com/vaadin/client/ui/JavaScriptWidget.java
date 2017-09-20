/*
 * Copyright 2000-2016 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.client.ui;

import java.util.List;

import com.google.gwt.dom.client.Document;
import com.google.gwt.user.client.ui.Widget;

public class JavaScriptWidget extends Widget {

    /**
     * Creates a JavaScriptWidget based on a &lt;div&gt; element
     */
    public JavaScriptWidget() {
        this("div");
    }

    /**
     * Creates a JavaScriptWidget based on an element with the given tag
     * 
     * @param tagName
     *            the tag to use for the element
     */
    public JavaScriptWidget(String tagName) {
        setElement(Document.get().createElement(tagName));
    }

    public void showNoInitFound(List<String> attemptedNames) {
        String message = "Could not initialize JavaScriptConnector because no JavaScript init function was found. Make sure one of these functions are defined: <ul>";
        for (String name : attemptedNames) {
            message += "<li>" + name + "</li>";
        }
        message += "</ul>";

        getElement().setInnerHTML(message);
    }
}
