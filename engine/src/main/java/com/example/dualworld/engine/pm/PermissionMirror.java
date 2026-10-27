package com.example.dualworld.engine.pm;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Mirrors system permissions held by the host application container to guest applications.
 */
public class PermissionMirror {
    private static final String TAG = "PermissionMirror";

    private final Context mContext;
    private final Set<String> mGrantedCache = new HashSet<>();

    public PermissionMirror(Context context) {
        this.mContext = context.getApplicationContext();
    }

    public boolean isPermissionGranted(String permission) {
        if (permission == null) return true;
        if (mGrantedCache.contains(permission)) return true;

        try {
            int result = mContext.checkSelfPermission(permission);
            if (result == PackageManager.PERMISSION_GRANTED) {
                mGrantedCache.add(permission);
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking permission: " + permission, e);
        }
        return false;
    }

    public int checkPermission(String permission, String guestPackage, int uid) {
        return isPermissionGranted(permission) ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
    }
}
