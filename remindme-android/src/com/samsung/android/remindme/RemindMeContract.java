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

import android.content.ContentResolver;
import android.content.UriMatcher;
import android.net.Uri;
import android.provider.BaseColumns;

/**
 * The contract between the RemindMe alerts provider and applications. Contains URI and column
 * definitions, along with helper methods for building URIs. See
 * {@link android.provider.ContactsContract} for more examples of this contract pattern.
 */
public class RemindMeContract {
    /**
     * The authority for Alert content.
     */
    public static final String AUTHORITY = "com.samsung.android.remindme";

    public static final Uri ROOT_URI = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY).appendPath("alerts").build();

    public static final String EMPTY_ACCOUNT_NAME = "-";

    public static Uri buildAlertListUri(String accountName) {
        return Uri.withAppendedPath(ROOT_URI,
                accountName == null ? EMPTY_ACCOUNT_NAME : accountName);
    }

    public static Uri buildAlertUri(String accountName, long alertId) {
        return Uri.withAppendedPath(buildAlertListUri(accountName), Long.toString(alertId));
    }


    public static String getAccountNameFromUri(Uri uri) {
        if (!uri.toString().startsWith(ROOT_URI.toString()))
            throw new IllegalArgumentException("Uri is not a RemindMe URI.");

        return uri.getPathSegments().get(1);
    }

    /**
     * Content type and column constants for the Alerts table.
     */
    public static class Alerts implements BaseColumns {
        /**
         * The MIME type of a directory of alerts.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.remindme.alert";
    
        /**
         * The MIME type of a single alert.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.remindme.alert";
    
        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = "targetId ASC";
    
        public static final String SERVER_ID = "serverId";
        public static final String TITLE = "targetId";
        public static final String BODY = "alert";
        public static final String ACCOUNT_NAME = "account";
        public static final String CREATED_DATE = "createdDate";
        public static final String MODIFIED_DATE = "modifiedDate";
        public static final String PENDING_DELETE = "pendingDelete";
    }
}
