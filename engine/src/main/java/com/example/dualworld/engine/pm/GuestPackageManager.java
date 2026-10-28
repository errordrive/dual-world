package com.example.dualworld.engine.pm;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.util.Log;

import com.example.dualworld.engine.cache.AppMetadataCache;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages parsing, inspection, and metadata caching for guest APK packages
 * without triggering system-level installation.
 */
public class GuestPackageManager {
    private static final String TAG = "GuestPackageManager";

    private final Context mContext;
    private final Map<String, PackageInfo> mPackageInfoCache = new HashMap<>();

    public GuestPackageManager(Context context) {
        this.mContext = context != null ? context.getApplicationContext() : null;
    }

    public Context getContext() {
        return mContext;
    }

    public synchronized PackageInfo getPackageInfo(String apkPath, int flags) {
        if (apkPath == null) return null;

        if (mPackageInfoCache.containsKey(apkPath)) {
            return mPackageInfoCache.get(apkPath);
        }

        try {
            PackageManager pm = mContext.getPackageManager();
            int queryFlags = flags | PackageManager.GET_ACTIVITIES |
                    PackageManager.GET_SERVICES |
                    PackageManager.GET_PROVIDERS |
                    PackageManager.GET_RECEIVERS |
                    PackageManager.GET_PERMISSIONS;

            PackageInfo pInfo = pm.getPackageArchiveInfo(apkPath, queryFlags);
            if (pInfo != null) {
                if (pInfo.applicationInfo != null) {
                    pInfo.applicationInfo.sourceDir = apkPath;
                    pInfo.applicationInfo.publicSourceDir = apkPath;
                }
                mPackageInfoCache.put(apkPath, pInfo);
                return pInfo;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse guest package at " + apkPath, e);
        }
        return null;
    }

    public ActivityInfo resolveActivityInfo(String apkPath, String activityClassName) {
        PackageInfo pi = getPackageInfo(apkPath, 0);
        if (pi != null && pi.activities != null) {
            for (ActivityInfo info : pi.activities) {
                if (info.name.equals(activityClassName)) {
                    return info;
                }
            }
        }
        return null;
    }

    public ServiceInfo resolveServiceInfo(String apkPath, String serviceClassName) {
        PackageInfo pi = getPackageInfo(apkPath, 0);
        if (pi != null && pi.services != null) {
            for (ServiceInfo info : pi.services) {
                if (info.name.equals(serviceClassName)) {
                    return info;
                }
            }
        }
        return null;
    }

    public ProviderInfo resolveProviderInfo(String apkPath, String authority) {
        PackageInfo pi = getPackageInfo(apkPath, 0);
        if (pi != null && pi.providers != null) {
            for (ProviderInfo info : pi.providers) {
                if (authority.equals(info.authority)) {
                    return info;
                }
            }
        }
        return null;
    }

    public void clearCache(String apkPath) {
        if (apkPath != null) {
            mPackageInfoCache.remove(apkPath);
        } else {
            mPackageInfoCache.clear();
        }
    }
}
