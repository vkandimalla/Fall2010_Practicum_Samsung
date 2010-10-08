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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * Provides access to a database of alerts. Each alert has a targetId, the alert
 * itself, a creation date and a modified data.
 */
public class RemindMeProvider extends ContentProvider {
    static final String TAG = Config.makeLogTag(RemindMeProvider.class);

    private static final String DATABASE_NAME = "remindme.db";
    private static final int DATABASE_VERSION = 5;
    private static final String NOTES_TABLE_NAME = "alerts";

    private static HashMap<String, String> sAlertsProjectionMap;
    private static final int NOTES = 1;
    private static final int NOTE_ID = 2;

    private static final UriMatcher sUriMatcher;

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        private Context mContext;
        
        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mContext = context;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + NOTES_TABLE_NAME + " ("
                    + RemindMeContract.Alerts._ID + " INTEGER PRIMARY KEY,"
                    + RemindMeContract.Alerts.SERVER_ID + " TEXT,"
                    + RemindMeContract.Alerts.ACCOUNT_NAME + " TEXT,"
                    + RemindMeContract.Alerts.TITLE + " TEXT,"
                    + RemindMeContract.Alerts.BODY + " TEXT NOT NULL DEFAULT '',"
                    + RemindMeContract.Alerts.CREATED_DATE + " INTEGER NOT NULL DEFAULT 0,"
                    + RemindMeContract.Alerts.PENDING_DELETE + " BOOLEAN NOT NULL DEFAULT 0,"
                    + RemindMeContract.Alerts.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT 0"
                    + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            SyncAdapter.clearSyncData(mContext);
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion
                    + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + NOTES_TABLE_NAME);
            onCreate(db);
        }
    }

    private DatabaseHelper mOpenHelper;

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(NOTES_TABLE_NAME);

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                qb.setProjectionMap(sAlertsProjectionMap);
                qb.appendWhere(RemindMeContract.Alerts.ACCOUNT_NAME + "=");
                qb.appendWhereEscapeString(uri.getPathSegments().get(1));
                break;

            case NOTE_ID:
                qb.setProjectionMap(sAlertsProjectionMap);
                qb.appendWhere(RemindMeContract.Alerts.ACCOUNT_NAME + "=");
                qb.appendWhereEscapeString(uri.getPathSegments().get(1));
                qb.appendWhere(" AND ");
                qb.appendWhere(RemindMeContract.Alerts._ID + "=" + uri.getPathSegments().get(2));
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // If no sort order is specified use the default
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = RemindMeContract.Alerts.DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }

        // Get the database and run the query
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);

        // Tell the cursor what uri to watch, so it knows when its source data
        // changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case NOTES:
                return RemindMeContract.Alerts.CONTENT_TYPE;

            case NOTE_ID:
                return RemindMeContract.Alerts.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        // Validate the requested uri
        if (sUriMatcher.match(uri) != NOTES) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        String accountName = uri.getPathSegments().get(1);
        values.put(RemindMeContract.Alerts.ACCOUNT_NAME, accountName);

        long now = System.currentTimeMillis();

        // Make sure that the fields are all set
        if (values.getAsLong(RemindMeContract.Alerts.CREATED_DATE) == null) {
            values.put(RemindMeContract.Alerts.CREATED_DATE, now);
        }

        if (values.getAsLong(RemindMeContract.Alerts.MODIFIED_DATE) == null) {
            values.put(RemindMeContract.Alerts.MODIFIED_DATE, now);
        }

        if (!values.containsKey(RemindMeContract.Alerts.TITLE)) {
            Resources r = Resources.getSystem();
            values.put(RemindMeContract.Alerts.TITLE, r.getString(android.R.string.untitled));
        }

        if (values.containsKey(RemindMeContract.Alerts.PENDING_DELETE) &&
                values.getAsBoolean(RemindMeContract.Alerts.PENDING_DELETE) == true) {
            throw new SQLException("Cannot insert a alert that is pending delete.");
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long rowId = db.insert(NOTES_TABLE_NAME, RemindMeContract.Alerts.BODY, values);
        if (rowId > 0) {
            Uri alertUri = RemindMeContract.buildAlertUri(accountName, rowId);
            boolean syncToNetwork = !hasCallerIsSyncAdapterParameter(uri);
            getContext().getContentResolver().notifyChange(alertUri, null, syncToNetwork);
            return alertUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
            case NOTES:
                count = db.delete(NOTES_TABLE_NAME, where, whereArgs);
                break;

            case NOTE_ID:
                String alertId = uri.getPathSegments().get(2);
                count = db.delete(NOTES_TABLE_NAME, RemindMeContract.Alerts._ID + "=" + alertId
                        + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        boolean syncToNetwork = !hasCallerIsSyncAdapterParameter(uri);
        getContext().getContentResolver().notifyChange(uri, null, syncToNetwork);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
            case NOTES:
                count = db.update(NOTES_TABLE_NAME, values, where, whereArgs);
                break;

            case NOTE_ID:
                String alertId = uri.getPathSegments().get(2);
                count = db.update(NOTES_TABLE_NAME, values, RemindMeContract.Alerts._ID + "="
                        + alertId + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
                        whereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        boolean syncToNetwork = !hasCallerIsSyncAdapterParameter(uri);
        getContext().getContentResolver().notifyChange(uri, null, syncToNetwork);
        return count;
    }

    private static boolean hasCallerIsSyncAdapterParameter(Uri uri) {
        return Boolean.parseBoolean(uri.getQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER));
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        // Match .../alerts/foo@gmail.com
        sUriMatcher.addURI(RemindMeContract.AUTHORITY, "alerts/*", NOTES);

        // Match .../alerts/foo@gmail.com/123 (alert ID)
        sUriMatcher.addURI(RemindMeContract.AUTHORITY, "alerts/*/#", NOTE_ID);

        sAlertsProjectionMap = new HashMap<String, String>();
        sAlertsProjectionMap.put(RemindMeContract.Alerts._ID,
                RemindMeContract.Alerts._ID);
        sAlertsProjectionMap.put(RemindMeContract.Alerts.SERVER_ID,
                RemindMeContract.Alerts.SERVER_ID);
        sAlertsProjectionMap.put(RemindMeContract.Alerts.ACCOUNT_NAME,
                RemindMeContract.Alerts.ACCOUNT_NAME);
        sAlertsProjectionMap.put(RemindMeContract.Alerts.TITLE,
                RemindMeContract.Alerts.TITLE);
        sAlertsProjectionMap.put(RemindMeContract.Alerts.BODY,
                RemindMeContract.Alerts.BODY);
        sAlertsProjectionMap.put(RemindMeContract.Alerts.CREATED_DATE,
                RemindMeContract.Alerts.CREATED_DATE);
        sAlertsProjectionMap.put(RemindMeContract.Alerts.MODIFIED_DATE,
                RemindMeContract.Alerts.MODIFIED_DATE);
        sAlertsProjectionMap.put(RemindMeContract.Alerts.PENDING_DELETE,
                RemindMeContract.Alerts.PENDING_DELETE);
    }
}
