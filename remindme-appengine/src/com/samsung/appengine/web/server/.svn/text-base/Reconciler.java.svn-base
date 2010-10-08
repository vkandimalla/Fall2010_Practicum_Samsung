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

package com.example.jumpnote.web.server;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple helper class that allows for merging (or 'reconciling') lists of objects, for use
 * during sync. Extending classes simply need to define a {@link Reconciler#reconcile} method,
 * and {@link Reconciler#reconcileLists} will do the actual work of merging two lists. Equality
 * comparison is done by {@link Map} key comparison.
 */
public abstract class Reconciler<T> {

    public Collection<T> reconcileLists(Collection<T> list1, Collection<T> list2) {
        Map<T, T> finalMap = new HashMap<T, T>();

        for (T o1 : list1) {
            finalMap.put(o1, o1);
        }

        for (T o2 : list2) {
            if (finalMap.containsKey(o2)) {
                // collision
                final T o1 = finalMap.remove(o2); // remove previously stored value
                finalMap.put(o2, reconcile(o1, o2));
            } else {
                finalMap.put(o2, o2);
            }
        }

        return finalMap.values();
    }

    public abstract T reconcile(final T o1, final T o2);
}
