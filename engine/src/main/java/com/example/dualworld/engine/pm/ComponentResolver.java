package com.example.dualworld.engine.pm;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.util.Log;

/**
 * Resolves explicit and implicit intents targeting guest components using parsed guest metadata.
 */
public class ComponentResolver {
    private static final String TAG = "ComponentResolver";

    private final GuestPackageManager mGuestPm;

    public ComponentResolver(GuestPackageManager guestPm) {
        this.mGuestPm = guestPm;
    }

    public ActivityInfo resolveActivity(String apkPath, Intent intent) {
        if (apkPath == null || intent == null) return null;

        ComponentName component = intent.getComponent();
        if (component != null && component.getClassName() != null) {
            ActivityInfo direct = mGuestPm.resolveActivityInfo(apkPath, component.getClassName());
            if (direct != null) return direct;
        }

        // Implicit intent or launcher intent resolution
        PackageInfo pi = mGuestPm.getPackageInfo(apkPath, 0);
        if (pi != null && pi.activities != null && pi.activities.length > 0) {
            if (Intent.ACTION_MAIN.equals(intent.getAction())) {
                // Return first activity or launcher activity
                return pi.activities[0];
            }
            return pi.activities[0];
        }

        Log.w(TAG, "Unable to resolve guest activity for intent: " + intent);
        return null;
    }

    public String resolveMainActivityClassName(String apkPath) {
        PackageInfo pi = mGuestPm.getPackageInfo(apkPath, 0);
        if (pi != null && pi.activities != null && pi.activities.length > 0) {
            return pi.activities[0].name;
        }
        return null;
    }
}
