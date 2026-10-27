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
        if (pi == null || pi.activities == null || pi.activities.length == 0) {
            return null;
        }

        String pkg = (pi.packageName != null) ? pi.packageName : "";
        String[] keywords = {"main", "launch", "splash", "home", "start", "entry"};

        // Pass 1: find an enabled activity whose simple name contains a launcher keyword
        for (ActivityInfo info : pi.activities) {
            if (info == null || !info.enabled) continue;
            String name = normalizeActivityName(pkg, info.name);
            String simpleName = getSimpleName(name).toLowerCase();
            for (String kw : keywords) {
                if (simpleName.contains(kw)) {
                    return name;
                }
            }
        }

        // Pass 2: fall back to first enabled activity
        for (ActivityInfo info : pi.activities) {
            if (info != null && info.enabled) {
                return normalizeActivityName(pkg, info.name);
            }
        }

        // Fallback to first declared activity
        return normalizeActivityName(pkg, pi.activities[0].name);
    }

    private String normalizeActivityName(String packageName, String name) {
        if (name == null) return null;
        if (name.startsWith(".")) {
            return packageName + name;
        }
        return name;
    }

    private String getSimpleName(String className) {
        if (className == null) return "";
        int dot = className.lastIndexOf('.');
        return (dot >= 0 && dot < className.length() - 1) ? className.substring(dot + 1) : className;
    }
}
