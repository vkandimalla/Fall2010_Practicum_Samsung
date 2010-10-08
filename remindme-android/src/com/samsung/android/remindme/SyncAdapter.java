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

import com.samsung.remindme.allshared.JsonRpcClient;
import com.samsung.remindme.allshared.JsonRpcException;
import com.samsung.remindme.allshared.RemindMeProtocol;
import com.samsung.android.remindme.ModelJava.DeviceRegistration;
import com.samsung.android.remindme.jsonrpc.AuthenticatedJsonRpcJavaClient;
import com.samsung.android.remindme.jsonrpc.AuthenticatedJsonRpcJavaClient.InvalidAuthTokenException;
import com.samsung.android.remindme.jsonrpc.AuthenticatedJsonRpcJavaClient.RequestedUserAuthenticationException;
import com.samsung.remindme.javashared.Util;
import com.google.android.c2dm.C2DMessaging;

import org.apache.http.auth.AuthenticationException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OperationCanceledException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.content.SyncStats;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * RemindMe SyncAdapter implementation. The sync adapter does the following:
 * <ul>
 *   <li>Device registration/unregistration when auto-sync settings for the account
 *     (or global settings) have changed, via the <code>devices.register</code> (and similar)
 *     RPC method.</li>
 *   <li>Checking for locally modified alerts since the last successful sync time.</li>
 *   <li>Synchronization with the server, via the <code>alerts.sync</code> RPC method.</li>
 * </ul>
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {
    static final String TAG = Config.makeLogTag(SyncAdapter.class);

    public static final String GOOGLE_ACCOUNT_TYPE = "com.google";
    public static final String[] GOOGLE_ACCOUNT_REQUIRED_SYNCABILITY_FEATURES =
            new String[]{ "service_ah" };

    public static final String DEVICE_TYPE = "android";
    public static final String LAST_SYNC = "last_sync";
    public static final String SERVER_LAST_SYNC = "server_last_sync";
    public static final String DM_REGISTERED = "dm_registered";

    private static final String[] PROJECTION = new String[] {
        RemindMeContract.Alerts._ID, // 0
        RemindMeContract.Alerts.SERVER_ID, // 1
        RemindMeContract.Alerts.TITLE, // 2
        RemindMeContract.Alerts.BODY, // 3
        RemindMeContract.Alerts.CREATED_DATE, // 4
        RemindMeContract.Alerts.MODIFIED_DATE, // 5
        RemindMeContract.Alerts.PENDING_DELETE, // 6
    };

    private final Context mContext;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
    }

    @Override
    public void onPerformSync(final Account account, Bundle extras, String authority,
            final ContentProviderClient provider, final SyncResult syncResult) 
    {
    		Log.i(TAG, "onPerformSync called!");
        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(
                Context.TELEPHONY_SERVICE);
        String clientDeviceId = tm.getDeviceId();

        final long newSyncTime = System.currentTimeMillis();

        final boolean uploadOnly = extras.getBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, false);
        final boolean manualSync = extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);
        final boolean initialize = extras.getBoolean(ContentResolver.SYNC_EXTRAS_INITIALIZE, false);

        C2DMReceiver.refreshAppC2DMRegistrationState(mContext);

        Log.i(TAG, "Beginning " + (uploadOnly ? "upload-only" : "full") +
                " sync for account " + account.name);

        // Read this account's sync metadata
        final SharedPreferences syncMeta = mContext.getSharedPreferences("sync:" + account.name, 0);
        long lastSyncTime = syncMeta.getLong(LAST_SYNC, 0);
        long lastServerSyncTime = syncMeta.getLong(SERVER_LAST_SYNC, 0);

        // Check for changes in either app-wide auto sync registration information, or changes in
        // the user's preferences for auto sync on this account; if either changes, piggy back the
        // new registration information in this sync.
        long lastRegistrationChangeTime = C2DMessaging.getLastRegistrationChange(mContext);

        boolean autoSyncDesired = ContentResolver.getMasterSyncAutomatically() &&
                ContentResolver.getSyncAutomatically(account, RemindMeContract.AUTHORITY);
        boolean autoSyncEnabled = syncMeta.getBoolean(DM_REGISTERED, false);

        // Will be 0 for no change, -1 for unregister, 1 for register.
        final int deviceRegChange;
        JsonRpcClient.Call deviceRegCall = null;
        if (autoSyncDesired != autoSyncEnabled || lastRegistrationChangeTime > lastSyncTime ||
                initialize || manualSync) {

            String registrationId = C2DMessaging.getRegistrationId(mContext);
            deviceRegChange = (autoSyncDesired && registrationId != null) ? 1 : -1;

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Auto sync selection or registration information has changed, " +
                        (deviceRegChange == 1 ? "registering" : "unregistering") +
                        " messaging for this device, for account " + account.name);
            }

            try {
                if (deviceRegChange == 1) {
                    // Register device for auto sync on this account.
                    deviceRegCall = new JsonRpcClient.Call(RemindMeProtocol.DevicesRegister.METHOD);
                    JSONObject params = new JSONObject();

                    DeviceRegistration device = new DeviceRegistration(clientDeviceId,
                            DEVICE_TYPE, registrationId);
                    params.put(RemindMeProtocol.DevicesRegister.ARG_DEVICE, device.toJSON());
                    deviceRegCall.setParams(params);
                } else {
                    // Unregister device for auto sync on this account.
                    deviceRegCall = new JsonRpcClient.Call(RemindMeProtocol.DevicesUnregister.METHOD);
                    JSONObject params = new JSONObject();
                    params.put(RemindMeProtocol.DevicesUnregister.ARG_DEVICE_ID, clientDeviceId);
                    deviceRegCall.setParams(params);
                }
            } catch (JSONException e) {
                logErrorMessage("Error generating device registration remote RPC parameters.",
                        manualSync);
                e.printStackTrace();
                return;
            }
        } else {
            deviceRegChange = 0;
        }

        // Get the list of locally changed alerts. If this is an upload-only sync and there were
        // no local changes, cancel the sync.
        List<ModelJava.Alert> locallyChangedAlerts = null;
        try {
            locallyChangedAlerts = getLocallyChangedAlerts(provider, account, new Date(lastSyncTime));
        } catch (RemoteException e) {
            logErrorMessage("Remote exception accessing content provider: " + e.getMessage(),
                    manualSync);
            e.printStackTrace();
            syncResult.stats.numIoExceptions++;
            return;
        }

        if (uploadOnly && locallyChangedAlerts.isEmpty() && deviceRegCall == null) {
            Log.i(TAG, "No local changes; upload-only sync canceled.");
            return;
        }

        // Set up the RPC sync calls
        final AuthenticatedJsonRpcJavaClient jsonRpcClient = new AuthenticatedJsonRpcJavaClient(
                mContext, Config.SERVER_AUTH_URL_TEMPLATE, Config.SERVER_RPC_URL);
        try {
            jsonRpcClient.blockingAuthenticateAccount(account,
                    manualSync ? AuthenticatedJsonRpcJavaClient.NEED_AUTH_INTENT
                               : AuthenticatedJsonRpcJavaClient.NEED_AUTH_NOTIFICATION,
                    false);
        } catch (AuthenticationException e) {
            logErrorMessage("Authentication exception when attempting to sync. root cause: " + e.getMessage() , manualSync);
            e.printStackTrace();
            
            syncResult.stats.numAuthExceptions++;
            return;
        } catch (OperationCanceledException e) {
            Log.i(TAG, "Sync for account " + account.name + " manually canceled.");
            return;
        } catch (RequestedUserAuthenticationException e) {
            syncResult.stats.numAuthExceptions++;
            return;
        } catch (InvalidAuthTokenException e) {
            logErrorMessage("Invalid auth token provided by AccountManager when attempting to " +
                    "sync.", manualSync);
            e.printStackTrace();
            syncResult.stats.numAuthExceptions++;
            return;
        }

        // Set up the alerts sync call.
        JsonRpcClient.Call alertsSyncCall = new JsonRpcClient.Call(RemindMeProtocol.AlertsSync.METHOD);
        try {
            JSONObject params = new JSONObject();
            params.put(RemindMeProtocol.ARG_CLIENT_DEVICE_ID, clientDeviceId);
            params.put(RemindMeProtocol.AlertsSync.ARG_SINCE_DATE,
                    Util.formatDateISO8601(new Date(lastServerSyncTime)));

            JSONArray locallyChangedAlertsJson = new JSONArray();
            for (ModelJava.Alert locallyChangedAlert : locallyChangedAlerts) {
                locallyChangedAlertsJson.put(locallyChangedAlert.toJSON());
            }

            params.put(RemindMeProtocol.AlertsSync.ARG_LOCAL_NOTES, locallyChangedAlertsJson);
            alertsSyncCall.setParams(params);
        } catch (JSONException e) {
            logErrorMessage("Error generating sync remote RPC parameters.", manualSync);
            e.printStackTrace();
            syncResult.stats.numParseExceptions++;
            return;
        }

        List<JsonRpcClient.Call> jsonRpcCalls = new ArrayList<JsonRpcClient.Call>();
        jsonRpcCalls.add(alertsSyncCall);
        if (deviceRegChange != 0)
            jsonRpcCalls.add(deviceRegCall);

        jsonRpcClient.callBatch(jsonRpcCalls, new JsonRpcClient.BatchCallback() {
            public void onData(Object[] data) {
                if (data[0] != null) {
                    // Read alerts sync data.
                    JSONObject dataJson = (JSONObject) data[0];
                    try {
                        List<ModelJava.Alert> changedAlerts = new ArrayList<ModelJava.Alert>();
                        JSONArray alertsJson = dataJson.getJSONArray(RemindMeProtocol.AlertsSync.RET_NOTES);
                        for (int i = 0; i < alertsJson.length(); i++) {
                            changedAlerts.add(new ModelJava.Alert(alertsJson.getJSONObject(i)));
                        }

                        reconcileSyncedAlerts(provider, account, changedAlerts, syncResult.stats);

                        // If sync is successful (no exceptions thrown), update sync metadata
                        long newServerSyncTime = Util.parseDateISO8601(dataJson.getString(
                                RemindMeProtocol.AlertsSync.RET_NEW_SINCE_DATE)).getTime();
                        syncMeta.edit().putLong(LAST_SYNC, newSyncTime).commit();
                        syncMeta.edit().putLong(SERVER_LAST_SYNC, newServerSyncTime).commit();
                        Log.i(TAG, "Sync complete, setting last sync time to "
                                + Long.toString(newSyncTime));
                    } catch (JSONException e) {
                        logErrorMessage("Error parsing alert sync RPC response", manualSync);
                        e.printStackTrace();
                        syncResult.stats.numParseExceptions++;
                        return;
                    } catch (ParseException e) {
                        logErrorMessage("Error parsing alert sync RPC response", manualSync);
                        e.printStackTrace();
                        syncResult.stats.numParseExceptions++;
                        return;
                    } catch (RemoteException e) {
                        logErrorMessage("RemoteException in reconcileSyncedAlerts: " +
                                e.getMessage(), manualSync);
                        e.printStackTrace();
                        return;
                    } catch (OperationApplicationException e) {
                        logErrorMessage("Could not apply batch operations to content provider: " +
                                e.getMessage(), manualSync);
                        e.printStackTrace();
                        return;
                    } finally {
                        provider.release();
                    }
                }

                // Read device reg data.
                if (deviceRegChange != 0) {
                    // data[1] will be null in case of an error (successful unregisters
                    // will have an empty JSONObject, not null).
                    boolean registered = (data[1] != null && deviceRegChange == 1); 
                    syncMeta.edit().putBoolean(DM_REGISTERED, registered).commit();
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Stored account auto sync registration state: " +
                                Boolean.toString(registered));
                    }
                }
            }

            public void onError(int callIndex, JsonRpcException e) {
                if (e.getHttpCode() == 403) {
                    Log.w(TAG, "Got a 403 response, invalidating App Engine ACSID token");
                    jsonRpcClient.invalidateAccountAcsidToken(account);
                }

                provider.release();
                logErrorMessage("Error calling remote alert sync RPC", manualSync);
                e.printStackTrace();
            }
        });
    }

    public void reconcileSyncedAlerts(ContentProviderClient provider, Account account,
            List<ModelJava.Alert> changedAlerts, SyncStats syncStats)
            throws RemoteException, OperationApplicationException {
        Cursor alertCursor;
        ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
        for (ModelJava.Alert changedAlert : changedAlerts) {
            Uri alertUri = null;

            if (changedAlert.getId() != null) {
                alertUri = addCallerIsSyncAdapterParameter(
                        RemindMeContract.buildAlertUri(account.name, Long.parseLong(changedAlert
                        .getId())));
            } else {
                alertCursor = provider.query(RemindMeContract.buildAlertListUri(account.name),
                        PROJECTION, RemindMeContract.Alerts.SERVER_ID + " = ?", new String[] {
                            changedAlert.getServerId()
                        }, null);

                if (alertCursor.moveToNext()) {
                    alertUri = addCallerIsSyncAdapterParameter(
                            RemindMeContract.buildAlertUri(account.name, alertCursor.getLong(0)));
                }

                alertCursor.close();
            }

            if (changedAlert.isPendingDelete()) {
                // Handle server-side delete.
                if (alertUri != null) {
                    operations.add(ContentProviderOperation.newDelete(alertUri).build());
                    syncStats.numDeletes++;
                }
            } else {
                ContentValues values = changedAlert.toContentValues();
                if (alertUri != null) {
                    // Handle server-side update.
                    operations.add(ContentProviderOperation.newUpdate(alertUri).withValues(values)
                            .build());
                    syncStats.numUpdates++;
                } else {
                    // Handle server-side insert.
                    operations.add(ContentProviderOperation.newInsert(
                            addCallerIsSyncAdapterParameter(
                                    RemindMeContract.buildAlertListUri(account.name)))
                            .withValues(values).build());
                    syncStats.numInserts++;
                }
            }
        }

        provider.applyBatch(operations);
    }

    public List<ModelJava.Alert> getLocallyChangedAlerts(ContentProviderClient provider,
            Account account, Date sinceDate) throws RemoteException {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Getting local alerts changed since " + Long.toString(sinceDate.getTime()));
        }
        Cursor alertsCursor = provider.query(RemindMeContract.buildAlertListUri(account.name),
                PROJECTION, RemindMeContract.Alerts.MODIFIED_DATE + " > ?", new String[] {
                    Long.toString(sinceDate.getTime())
                }, null);

        List<ModelJava.Alert> locallyChangedAlerts = new ArrayList<ModelJava.Alert>();
        while (alertsCursor.moveToNext()) {
            ContentValues values = new ContentValues();
            DatabaseUtils.cursorRowToContentValues(alertsCursor, values);
            ModelJava.Alert changedAlert = new ModelJava.Alert(values);
            locallyChangedAlerts.add(changedAlert);
        }

        alertsCursor.close();
        return locallyChangedAlerts;
    }

    private static Uri addCallerIsSyncAdapterParameter(Uri uri) {
        return uri.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .build();
    }

    public static void clearSyncData(Context context) {
        AccountManager am = AccountManager.get(context);
        Account[] accounts = am.getAccounts();
        for (Account account : accounts) {
            final SharedPreferences syncMeta = context.getSharedPreferences(
                    "sync:" + account.name, 0);
            syncMeta.edit().clear().commit();
        }
    }

    private void logErrorMessage(final String message, boolean showToast) {
        Log.e(TAG, message);
        System.out.println(message);
        // Alert: in general, showing any form of UI from a service is bad. showToast should only
        // be true if this is a manual sync, i.e. the user has just invoked some UI that indicates
        // she wants to perform a sync.
        Looper mainLooper = mContext.getMainLooper();
        if (mainLooper != null) {
            new Handler(mainLooper).post(new Runnable() {
                public void run() {
                    Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
