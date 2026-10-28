package com.example.dualworld.engine.pm;

import android.content.ComponentName;
import android.content.Context;
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
            String mainClass = resolveMainActivityClassName(apkPath);
            if (mainClass != null) {
                for (ActivityInfo info : pi.activities) {
                    if (mainClass.equals(info.name)) {
                        return info;
                    }
                }
            }
            return pi.activities[0];
        }

        Log.w(TAG, "Unable to resolve guest activity for intent: " + intent);
        return null;
    }

    public String resolveMainActivityClassName(String apkPath) {
        PackageInfo pi = mGuestPm.getPackageInfo(apkPath, 0);
        if (pi == null) {
            return null;
        }

        String pkg = (pi.packageName != null) ? pi.packageName : "";

        // Strategy 1: Query system PackageManager for official user-facing launcher activity
        Context context = mGuestPm.getContext();
        if (context != null && !pkg.isEmpty()) {
            try {
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(pkg);
                if (launchIntent != null && launchIntent.getComponent() != null) {
                    String officialClass = launchIntent.getComponent().getClassName();
                    Log.i(TAG, "Resolved official launcher activity via system for " + pkg + ": " + officialClass);
                    return officialClass;
                }
            } catch (Throwable t) {
                Log.w(TAG, "System package manager launch intent lookup failed for " + pkg + ": " + t.getMessage());
            }
        }

        if (pi.activities == null || pi.activities.length == 0) {
            return null;
        }

        // Strategy 2: Check for exact standard entry point names
        String[] exactNames = {
                "MainActivity", "HomeActivity", "LauncherActivity",
                "StartActivity", "SplashActivity", "EntryActivity"
        };
        for (ActivityInfo info : pi.activities) {
            if (info == null || !info.enabled) continue;
            String name = normalizeActivityName(pkg, info.name);
            String simpleName = getSimpleName(name);
            for (String exact : exactNames) {
                if (simpleName.equalsIgnoreCase(exact)) {
                    Log.i(TAG, "Resolved launcher activity by exact name match: " + name);
                    return name;
                }
            }
        }

        // Strategy 3: Check launcher keywords while excluding non-phone targets (vr, wear, tv, auto)
        String[] keywords = {"main", "launch", "splash", "home", "start", "entry"};
        String[] nonPhoneExclusions = {"vr", "wear", "leanback", "tv", "auto", "watch", "car", "glass"};

        for (ActivityInfo info : pi.activities) {
            if (info == null || !info.enabled) continue;
            String name = normalizeActivityName(pkg, info.name);
            String simpleName = getSimpleName(name).toLowerCase();

            boolean hasExclusion = false;
            for (String ex : nonPhoneExclusions) {
                if (simpleName.contains(ex)) {
                    hasExclusion = true;
                    break;
                }
            }
            if (hasExclusion) continue;

            for (String kw : keywords) {
                if (simpleName.contains(kw)) {
                    Log.i(TAG, "Resolved launcher activity by keyword match: " + name);
                    return name;
                }
            }
        }

        // Strategy 4: Fall back to first enabled activity not containing non-phone exclusions
        for (ActivityInfo info : pi.activities) {
            if (info != null && info.enabled) {
                String name = normalizeActivityName(pkg, info.name);
                String simpleName = getSimpleName(name).toLowerCase();
                boolean hasExclusion = false;
                for (String ex : nonPhoneExclusions) {
                    if (simpleName.contains(ex)) {
                        hasExclusion = true;
                        break;
                    }
                }
                if (!hasExclusion) {
                    return name;
                }
            }
        }

        // Strategy 5: Absolute fallback to first declared activity
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
