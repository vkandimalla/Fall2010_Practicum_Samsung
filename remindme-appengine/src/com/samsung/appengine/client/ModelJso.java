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

import com.google.gwt.core.client.JavaScriptObject;
import com.samsung.appengine.allshared.Model;

import java.util.Date;

/**
 * Data model implementation for the JumpNote GWT client. ModelJso serves more as a
 * namespace than a class, for convenience. The base interfaces are defined in
 * the {@link Model} class.
 */
public class ModelJso {
    public static final class Alert extends JavaScriptObject implements Model.Alert {
        protected Alert() {
        }

        public static native Alert create(String id, String targetId, String body) /*-{
            return {id: id, title: targetId, body: body};
        }-*/;

        public static native Alert create(String targetId, String body) /*-{
            return {title: targetId, body: body};
        }-*/;

        public native String getId() /*-{
            return this.id;
        }-*/;

        public native void setId(String id) /*-{
            this.id = id;
        }-*/;

        public native String getOwnerId() /*-{
            return this.owner_id;
        }-*/;

        public native void setOwnerId(String ownerId) /*-{
            this.owner_id = ownerId;
        }-*/;

        public native String getTargetId() /*-{
            return this.targetId;
        }-*/;

        public native void setTargetId(String targetId) /*-{
            this.targetId = targetId;
        }-*/;

        public native String getBody() /*-{
            return this.body;
        }-*/;

        public native void setBody(String body) /*-{
            this.body = body;
        }-*/;

        public native boolean isPendingDelete() /*-{
            return this.pendingDelete;
        }-*/;

        // TODO: return the real date value
        public native Date getCreatedDate() /*-{
            return new Date();
        }-*/;

        // TODO: return the real date value
        public native Date getModifiedDate() /*-{
            return new Date();
        }-*/;
    }

    public static final class UserInfo extends JavaScriptObject implements Model.UserInfo {
        protected UserInfo() {
        }

        public static native UserInfo create(String id, String email) /*-{
            return {id: id, email: email};
        }-*/;

        public native String getId() /*-{
            return this.id;
        }-*/;

        public native String getEmail() /*-{
            return this.email;
        }-*/;

    }
}
