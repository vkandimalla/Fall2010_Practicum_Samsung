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

package com.samsung.android.remindme;

import com.samsung.remindme.allshared.Model;
import com.samsung.remindme.javashared.JsonSerializable;
import com.samsung.remindme.javashared.Util;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;

import java.text.ParseException;
import java.util.Date;

/**
 * Data model implementation for the RemindMe Android client. ModelJava serves more as a
 * namespace than a class, for convenience. The base interfaces are defined in
 * the {@link Model} class.
 */
public class ModelJava {
    public static final class Alert implements Model.Alert, JsonSerializable {
        private String id; // Local ID
        private String serverId; // Server-side ID
        private String ownerId; // Unused on the client side
        private String targetId;
        private String body;
        private Date createdDate;
        private Date modifiedDate;
        private boolean pendingDelete;

        public Alert() {
            this.createdDate = new Date();
            touch();
        }

        public Alert(ContentValues values) {
            this.createdDate = new Date();
            this.fromContentValues(values);
        }

        public Alert(JSONObject json) throws JSONException {
            this.createdDate = new Date();
            this.fromJSON(json);
        }

        public void fromJSON(Object object) throws JSONException {
            JSONObject json = (JSONObject) object;
            if (json.has("owner_id"))
                this.ownerId = json.getString("owner_id");
            if (json.has("id"))
                this.serverId = json.getString("id");
            this.id = json.optString("local_id", this.id);
            this.targetId = json.optString("targetId", this.targetId);
            this.body = json.optString("body", this.body);
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
            json.put("local_id", getId());
            if (getServerId() != null) {
                json.put("id", getServerId());
            }
            if (getOwnerId() != null) {
                json.put("owner_id", getOwnerId());
            }
            json.put("targetId", getTargetId());
            json.put("body", getBody());
            json.put("date_created", Util.formatDateISO8601(getCreatedDate()));
            json.put("date_modified", Util.formatDateISO8601(getModifiedDate()));
            if (isPendingDelete())
                json.put("delete", true);
            return json;
        }

        public void fromContentValues(ContentValues values) {
            this.id = values.getAsString(RemindMeContract.Alerts._ID);
            this.serverId = values.getAsString(RemindMeContract.Alerts.SERVER_ID);
            this.targetId = values.getAsString(RemindMeContract.Alerts.TITLE);
            this.body = values.getAsString(RemindMeContract.Alerts.BODY);
            if (values.containsKey(RemindMeContract.Alerts.CREATED_DATE))
                this.createdDate = new Date(values.getAsLong(RemindMeContract.Alerts.CREATED_DATE));
            if (values.containsKey(RemindMeContract.Alerts.MODIFIED_DATE))
                this.modifiedDate = new Date(
                        values.getAsLong(RemindMeContract.Alerts.MODIFIED_DATE));
            if (values.containsKey(RemindMeContract.Alerts.PENDING_DELETE) && values.getAsInteger(
                    RemindMeContract.Alerts.PENDING_DELETE) == 1)
                this.pendingDelete = true;
        }

        public ContentValues toContentValues() {
            ContentValues values = new ContentValues();
            if (getId() != null) {
                values.put(RemindMeContract.Alerts._ID, getId());
            }
            if (getServerId() != null) {
                values.put(RemindMeContract.Alerts.SERVER_ID, getServerId());
            }
            values.put(RemindMeContract.Alerts.TITLE, getTargetId());
            values.put(RemindMeContract.Alerts.BODY, getBody());
            values.put(RemindMeContract.Alerts.CREATED_DATE, getCreatedDate().getTime());
            values.put(RemindMeContract.Alerts.MODIFIED_DATE, getModifiedDate().getTime());
            if (isPendingDelete())
                values.put(RemindMeContract.Alerts.PENDING_DELETE, 1);
            return values;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Alert)
                return ((Alert) obj).getId().equals(getId());
            return false;
        }

        @Override
        public int hashCode() {
            return getId().hashCode();
        }

        public String getServerId() {
            return serverId;
        }

        public String getId() {
            return id;
        }

        public String getTargetId() {
            return targetId;
        }

        public void setTargetId(String targetId) {
            this.targetId = targetId;
            touch();
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
            touch();
        }

        public String getOwnerId() {
            return ownerId;
        }

        public void setOwnerId(String ownerId) {
            this.ownerId = ownerId;
            touch();
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
            targetId = "";
            body = "";
            pendingDelete = true;
            touch();
        }

        public void touch() {
            modifiedDate = new Date();
        }
    }

    public static final class UserInfo implements Model.UserInfo, JsonSerializable {
        private String id;
        private String email;

        public UserInfo(String id, String email) {
            this.id = id;
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

        public boolean equals(Object obj) {
            if (obj instanceof UserInfo)
                return ((UserInfo) obj).getId().equals(getId());
            return false;
        }

        public String getEmail() {
            return email;
        }
    }

    public static final class DeviceRegistration implements Model.DeviceRegistration, JsonSerializable {
        private String ownerId; // Unused on the client side
        private String deviceId;
        private String deviceType;
        private String registrationToken;

        public DeviceRegistration() {
        }

        public DeviceRegistration(String deviceId, String deviceType, String registrationToken) {
            this.deviceId = deviceId;
            this.deviceType = deviceType;
            this.registrationToken = registrationToken;
        }

        public DeviceRegistration(JSONObject json) throws JSONException {
            this.fromJSON(json);
        }

        public void fromJSON(Object object) throws JSONException {
            JSONObject json = (JSONObject) object;
            if (json.has("owner_id"))
                this.ownerId = json.getString("owner_id");
            this.deviceId = json.getString("device_id");
            this.deviceType = json.getString("device_type");
            if (json.has("registration_token"))
                this.registrationToken = json.getString("registration_token");
        }

        public JSONObject toJSON() throws JSONException {
            JSONObject json = new JSONObject();
            if (getOwnerId() != null) {
                json.put("owner_id", getOwnerId());
            }
            json.put("device_id", getDeviceId());
            json.put("device_type", getDeviceType());
            if (getRegistrationToken() != null)
                json.put("registration_token", getRegistrationToken());
            return json;
        }

        public String getOwnerId() {
            return ownerId;
        }

        public void setOwnerId(String ownerId) {
            this.ownerId = ownerId;
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

        public void setDeviceType(String deviceType) {
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
