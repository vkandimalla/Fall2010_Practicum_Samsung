/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.samsung.appengine.client;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SimplePanel;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A panel that can show one {@link Screen} at a time.
 */
public class ScreenContainer extends SimplePanel {
    public Map<String, Screen> mScreens = new HashMap<String, Screen>();
    public Screen mCurrentScreen = null;
    public String mDefaultName = "";

    public void addScreen(String name, Screen screen) {
        mScreens.put(name, screen);
    }

    public void setDefault(String name) {
        if (!mScreens.containsKey(name)) {
            throw new IllegalArgumentException("Screen with name '" + name + "' not registered.");
        }

        mDefaultName = name;
    }

    public void install(RootPanel rootPanel) {
        rootPanel.add(this);

        if ("".equals(History.getToken())) {
            History.newItem(mDefaultName);
        }

        History.addValueChangeHandler(new ValueChangeHandler<String>() {
            public void onValueChange(ValueChangeEvent<String> event) {
                loadScreen(event.getValue());
            }
        });

        History.fireCurrentHistoryState();
    }

    private void loadScreen(String path) {
        List<String> pathElements = Arrays.asList(path.split("/"));
        String name = pathElements.get(0);
        if ("".equals(name)) {
            History.newItem(mDefaultName);
            return;
        }

        if (!mScreens.containsKey(name)) {
            loadErrorScreen();
            return;
        }

        Screen screen = mScreens.get(name);

        List<String> args = pathElements.subList(1, pathElements.size());

        Screen newScreen = screen.fillOrReplace(args);
        if (newScreen == null) {
            loadErrorScreen();
            return;
        }

        if (!screen.equals(newScreen)) {
            screen.removeFromParent();
            mScreens.remove(name);
            mScreens.put(name, newScreen);
        }

        if (mCurrentScreen != null) {
            remove(mCurrentScreen);
            mCurrentScreen = null;
        }

        add(newScreen);
        newScreen.onShow();
        mCurrentScreen = newScreen;
    }

    private void loadErrorScreen() {
        if (mCurrentScreen != null) {
            remove(mCurrentScreen);
            mCurrentScreen = null;

           Remindme_appengine.showMessage("Can't find that page.", false);
        }
    }
}
