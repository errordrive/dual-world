package com.example.dualworld.server;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * Virtual Package Manager Service for Dual World.
 */
public class VPackageManagerService {
    private static final String TAG = "VPackageManagerService";
    private static final VPackageManagerService sInstance = new VPackageManagerService();

    private Context mContext;

    public static VPackageManagerService get() {
        return sInstance;
    }

    private VPackageManagerService() {}

    public void init(Context context) {
        mContext = context;
        Log.i(TAG, "Virtual Package Manager Service started");
    }

    public PackageInfo getPackageInfo(String packageName, int flags, int userId) {
        try {
            PackageManager pm = mContext.getPackageManager();
            PackageInfo hostPkgInfo = pm.getPackageInfo(packageName, flags);
            return spoofPackageInfo(hostPkgInfo, userId);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public ApplicationInfo getApplicationInfo(String packageName, int flags, int userId) {
        PackageInfo pkg = getPackageInfo(packageName, flags, userId);
        return pkg != null ? pkg.applicationInfo : null;
    }

    private PackageInfo spoofPackageInfo(PackageInfo original, int userId) {
        if (original == null || original.applicationInfo == null) return original;
        String virtualDataPath = mContext.getFilesDir().getParent() + "/virtual/user/" + userId + "/" + original.packageName;
        original.applicationInfo.dataDir = virtualDataPath;
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            original.applicationInfo.deviceProtectedDataDir = virtualDataPath;
        }
        return original;
    }
}
