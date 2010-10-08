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

import com.google.gwt.user.client.ui.Composite;

import java.util.List;

/**
 * An abstract class defining a single screen (i.e. the welcome screen, list screen, edit screen).
 * Similar to an Activity on Android. Screens are generally associated with a particular URL
 * pattern.
 */
public abstract class Screen extends Composite {
    public abstract Screen fillOrReplace(List<String> args);
    public void onShow(){}
}
