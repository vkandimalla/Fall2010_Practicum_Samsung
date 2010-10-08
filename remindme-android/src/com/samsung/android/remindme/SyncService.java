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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Service that handles sync. It simply instantiates a SyncAdapter and returns its IBinder.
 */
public class SyncService extends Service {
    private static final Object sSyncAdapterLock = new Object();
    private static SyncAdapter sSyncAdapter = null;
    private static final String TAG = Config.makeLogTag(SyncService.class);

    @Override
    public void onCreate() {
        synchronized (sSyncAdapterLock) {
        		Log.i(TAG, "creating SyncService, sSyncAdapter: " + sSyncAdapter);
            if (sSyncAdapter == null) {
                sSyncAdapter = new SyncAdapter(getApplicationContext(), false);             
            }
        }
        Log.i(TAG, "created SyncService, sSyncAdapter: " + sSyncAdapter);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }
}
