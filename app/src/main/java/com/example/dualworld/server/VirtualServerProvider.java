package com.example.dualworld.server;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * IPC Gateway ContentProvider for the :service process.
 */
public class VirtualServerProvider extends ContentProvider {
    private static final String TAG = "VirtualServerProvider";
    public static final String ACTION_GET_BINDER = "get_service_binder";

    @Override
    public boolean onCreate() {
        Log.i(TAG, "VirtualServerProvider initialized in :service daemon process");
        VPackageManagerService.get().init(getContext());
        VActivityManagerService.get().init(getContext());
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (ACTION_GET_BINDER.equals(method)) {
            Bundle bundle = new Bundle();
            bundle.putString("status", "ok");
            return bundle;
        }
        return super.call(method, arg, extras);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
