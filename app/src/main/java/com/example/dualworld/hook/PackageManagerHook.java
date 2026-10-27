package com.example.dualworld.hook;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.util.Log;

import com.example.dualworld.client.VirtualClient;
import com.example.dualworld.core.VirtualCore;

import java.lang.reflect.Method;

public class PackageManagerHook {
    private static final String TAG = "PackageManagerHook";

    public static class GetPackageInfoHook extends BaseHook {
        private final Context mContext;

        public GetPackageInfoHook(Context context) {
            super("getPackageInfo");
            mContext = context;
        }

        @Override
        public Object call(Object base, Method method, Object[] args) throws Throwable {
            Object result = method.invoke(base, args);
            if (result instanceof PackageInfo) {
                PackageInfo info = (PackageInfo) result;
                String guestPkg = VirtualClient.get().getCurrentPackageName();
                int userId = VirtualClient.get().getCurrentUserId();

                if (guestPkg != null && guestPkg.equals(info.packageName)) {
                    String sandboxedDataDir = VirtualCore.get().getStorage().getDataDir(guestPkg, userId).getAbsolutePath();
                    if (info.applicationInfo != null) {
                        info.applicationInfo.dataDir = sandboxedDataDir;
                    }
                    Log.d(TAG, "Spoofed getPackageInfo dataDir=" + sandboxedDataDir);
                }
            }
            return result;
        }
    }

    public static class GetApplicationInfoHook extends BaseHook {
        public GetApplicationInfoHook() {
            super("getApplicationInfo");
        }

        @Override
        public Object call(Object base, Method method, Object[] args) throws Throwable {
            Object result = method.invoke(base, args);
            if (result instanceof ApplicationInfo) {
                ApplicationInfo info = (ApplicationInfo) result;
                String guestPkg = VirtualClient.get().getCurrentPackageName();
                int userId = VirtualClient.get().getCurrentUserId();

                if (guestPkg != null && guestPkg.equals(info.packageName)) {
                    info.dataDir = VirtualCore.get().getStorage().getDataDir(guestPkg, userId).getAbsolutePath();
                }
            }
            return result;
        }
    }
}
