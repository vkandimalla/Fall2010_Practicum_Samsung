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

import com.example.jumpnote.allshared.Model;
import com.example.jumpnote.javashared.JsonSerializable;
import com.example.jumpnote.javashared.Util;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.users.User;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.jdo.annotations.Extension;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.NotPersistent;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

/**
 * Data model implementation for the JumpNote App Engine server. ModelImpl serves more as a
 * namespace than a class, for convenience. The base interfaces are defined in
 * the {@link Model} class.
 */
public class ModelImpl {
    @PersistenceCapable
    public static final class Note implements Model.Note, JsonSerializable {
        @PrimaryKey
        @Persistent(valueStrategy = IdGeneratorStrategy.IDENTITY)
        private Key key;

        @Persistent
        @Extension(vendorName = "datanucleus", key = "gae.parent-pk", value = "true")
        private Key ownerKey; // only set this when creating a note

        @Persistent
        private String title;

        @Persistent
        private Text body;

        @Persistent
        private Date createdDate;

        @Persistent
        private Date modifiedDate;

        @Persistent
        private boolean pendingDelete;

        /**
         * Used only during sync; when clients upload new entries, the server's
         * sync response will include a local ID and server-side ID so the
         * client can match them up.
         */
        @NotPersistent
        private String localId;

        public Note(String ownerId) {
            this.ownerKey = UserInfo.makeKey(ownerId);
            this.createdDate = new Date();
            this.modifiedDate = new Date();
            touch();
        }

        public Note(JSONObject json) throws JSONException {
            this.createdDate = new Date();
            this.modifiedDate = new Date();
            this.fromJSON(json);
        }

        public void fromJSON(Object object) throws JSONException {
            JSONObject json = (JSONObject) object;
            if (json.has("id"))
                this.key = Note.makeKey(json.getString("owner_id"), json.getString("id"));
            else
                this.ownerKey = UserInfo.makeKey(json.getString("owner_id"));
            this.localId = json.optString("local_id", this.localId);
            this.title = json.optString("title", this.title);
            if (json.has("body"))
                this.body = new Text(json.getString("body"));
            if (json.optBoolean("delete", false))
                markForDeletion();

            touch();
            try {
                if (json.has("date_created"))
                    this.createdDate = Util.parseDateISO8601(json.getString("date_created"));
                if (json.has("date_modified"))
                    this.modifiedDate = Util.parseDateISO8601(json.getString("date_modified"));
            } catch (ParseException e) {
                throw new JSONException("Invalid date.");
            }
        }

        public Object toJSON() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("id", getId());
            json.put("owner_id", getOwnerId());
            json.put("title", getTitle());
            json.put("body", getBody());
            json.put("date_created", Util.formatDateISO8601(getCreatedDate()));
            json.put("date_modified", Util.formatDateISO8601(getModifiedDate()));
            if (isPendingDelete())
                json.put("delete", true);
            if (getLocalId() != null)
                json.put("local_id", getLocalId());
            return json;
        }

        @Override
        public boolean equals(Object obj) {
            if (getKey() == null || ((Note) obj).getKey() == null)
                return false;

            if (obj instanceof Note)
                return ((Note) obj).getKey().equals(getKey());
            return false;
        }

        @Override
        public int hashCode() {
            return (getKey() == null) ? 0 : getKey().hashCode();
        }

        public Key getKey() {
            return key;
        }

        public String getLocalId() {
            return localId;
        }

        public static Key makeKey(String ownerId, String id) {
            return KeyFactory.createKey(UserInfo.makeKey(ownerId),
                    "ModelImpl$Note", Long.parseLong(id));
        }

        public String getId() {
            return Long.toString(key.getId());
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
            touch();
        }

        public String getBody() {
            return (body == null) ? null : body.getValue();
        }

        public void setBody(String body) {
            this.body = new Text(body);
        }

        public String getOwnerId() {
            return (this.ownerKey != null)
                       ? this.ownerKey.getName()
                       : (this.key != null ? this.key.getParent().getName() : null);
        }

        public Date getCreatedDate() {
            return createdDate;
        }

        public Date getModifiedDate() {
            return modifiedDate;
        }

        public boolean isPendingDelete() {
            return pendingDelete;
        }

        public void markForDeletion() {
            title = "";
            body = new Text("");
            pendingDelete = true;
            touch();
        }

        public void touch() {
            modifiedDate = new Date();
        }
    }

    @PersistenceCapable
    public static final class UserInfo implements Model.UserInfo, JsonSerializable {
        @PrimaryKey
        @Persistent(valueStrategy = IdGeneratorStrategy.IDENTITY)
        private String id;

        @Persistent
        private String email;

        public UserInfo(User user) {
            this.id = user.getUserId();
            this.email = user.getEmail();
        }

        public UserInfo(String userId, String email) {
            this.id = userId;
            this.email = email;
        }

        public UserInfo(JSONObject json) throws JSONException {
            this.fromJSON(json);
        }

        public void fromJSON(Object object) throws JSONException {
            JSONObject json = (JSONObject) object;
            this.id = json.getString("id");
            this.email = json.getString("email");
        }

        public JSONObject toJSON() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("id", getId());
            json.put("email", getEmail());
            return json;
        }

        public String getId() {
            return id;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof UserInfo)
                return ((UserInfo) obj).getKey().equals(getKey());
            return false;
        }

        public Key getKey() {
            return UserInfo.makeKey(this.id);
        }

        public static Key makeKey(String id) {
            return KeyFactory.createKey("ModelImpl$UserInfo", id);
        }

        public String getEmail() {
            return email;
        }
    }

    @PersistenceCapable
    public static final class DeviceRegistration implements Model.DeviceRegistration, JsonSerializable {
        public static final Set<String> KNOWN_DEVICE_TYPES = new HashSet<String>(
                Arrays.asList(new String[] { "android" }));

        @PrimaryKey
        @Persistent(valueStrategy = IdGeneratorStrategy.IDENTITY)
        private Key key;

        @Persistent
        @Extension(vendorName = "datanucleus", key = "gae.parent-pk", value = "true")
        private Key ownerKey; // only set this when creating a registration

        @Persistent
        private String deviceId;

        @Persistent
        private String deviceType;

        @Persistent
        private String registrationToken;

        public DeviceRegistration(String ownerId, String deviceId, String deviceType,
                String registrationToken) {
            this.ownerKey = UserInfo.makeKey(ownerId);
            this.deviceId = deviceId;
            this.deviceType = deviceType;
            this.registrationToken = registrationToken;
        }

        public DeviceRegistration(JSONObject json) throws JSONException {
            this.fromJSON(json);
        }

        public void fromJSON(Object object) throws JSONException {
            JSONObject json = (JSONObject) object;
            this.ownerKey = UserInfo.makeKey(json.getString("owner_id"));
            this.deviceId = json.getString("device_id");
            try {
                setDeviceType(json.getString("device_type"));
            } catch (IllegalArgumentException e) {
                throw new JSONException("Unknown device type.");
            }
            if (json.has("registration_token"))
                this.registrationToken = json.getString("registration_token");
        }

        public JSONObject toJSON() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("owner_id", getOwnerId());
            json.put("device_id", getDeviceId());
            json.put("device_type", getDeviceType());
            if (getRegistrationToken() != null)
                json.put("registration_token", getRegistrationToken());
            return json;
        }

        public Key getKey() {
            return key;
        }

        public String getOwnerId() {
            return (this.ownerKey != null)
                       ? this.ownerKey.getName()
                       : (this.key != null ? this.key.getParent().getName() : null);
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getDeviceType() {
            return deviceType;
        }

        public void setDeviceType(String deviceType) throws IllegalArgumentException {
            if (!KNOWN_DEVICE_TYPES.contains(deviceType))
                throw new IllegalArgumentException("Unknown device type.");
            this.deviceType = deviceType;
        }

        public String getRegistrationToken() {
            return registrationToken;
        }

        public void setRegistrationToken(String registrationToken) {
            this.registrationToken = registrationToken;
        }
    }
}
