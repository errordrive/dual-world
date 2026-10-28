package com.example.dualworld.engine.ipc;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Parcel;
import android.util.Log;

/**
 * Rewrites component identifiers, package names, and filesystem paths across
 * framework IPC boundaries.
 */
public class ParcelRewriter {
    private static final String TAG = "ParcelRewriter";

    public static final String EXTRA_TARGET_INTENT = "_dw_target_intent_";
    public static final String EXTRA_GUEST_PKG = "_dw_guest_pkg_";
    public static final String EXTRA_USER_ID = "_dw_user_id_";
    public static final String EXTRA_STUB_CLASS = "_dw_stub_class_";
    public static final String EXTRA_GUEST_APK_PATH = "_dw_guest_apk_path_";
    public static final String EXTRA_GUEST_DATA_DIR = "_dw_guest_data_dir_";
    public static final String EXTRA_GUEST_MAIN_CLASS = "_dw_guest_main_class_";

    public static Intent wrapStubIntent(Intent original, ComponentName stubComponent, String guestPkg, int userId) {
        return wrapStubIntent(original, stubComponent, guestPkg, userId, null, null, null);
    }

    public static Intent wrapStubIntent(Intent original, ComponentName stubComponent, String guestPkg, int userId,
                                        String guestApkPath, String guestDataDir, String guestMainClass) {
        Intent stubIntent = new Intent();
        stubIntent.setComponent(stubComponent);
        if (original != null && original.getAction() != null) {
            stubIntent.setAction(original.getAction());
        }
        if (original != null) {
            stubIntent.addFlags(original.getFlags());
            stubIntent.putExtra(EXTRA_TARGET_INTENT, original);

            // Also mirror guest metadata inside original intent so unwrapped intents retain it
            original.putExtra(EXTRA_GUEST_PKG, guestPkg);
            original.putExtra(EXTRA_USER_ID, userId);
            original.putExtra(EXTRA_STUB_CLASS, stubComponent.getClassName());
            if (guestApkPath != null) original.putExtra(EXTRA_GUEST_APK_PATH, guestApkPath);
            if (guestDataDir != null) original.putExtra(EXTRA_GUEST_DATA_DIR, guestDataDir);
            if (guestMainClass != null) original.putExtra(EXTRA_GUEST_MAIN_CLASS, guestMainClass);
        }
        stubIntent.putExtra(EXTRA_GUEST_PKG, guestPkg);
        stubIntent.putExtra(EXTRA_USER_ID, userId);
        stubIntent.putExtra(EXTRA_STUB_CLASS, stubComponent.getClassName());
        if (guestApkPath != null) stubIntent.putExtra(EXTRA_GUEST_APK_PATH, guestApkPath);
        if (guestDataDir != null) stubIntent.putExtra(EXTRA_GUEST_DATA_DIR, guestDataDir);
        if (guestMainClass != null) stubIntent.putExtra(EXTRA_GUEST_MAIN_CLASS, guestMainClass);
        return stubIntent;
    }

    public static Intent unwrapStubIntent(Intent stubIntent) {
        if (stubIntent == null) return null;
        if (stubIntent.hasExtra(EXTRA_TARGET_INTENT)) {
            Intent original = stubIntent.getParcelableExtra(EXTRA_TARGET_INTENT);
            if (original != null) {
                return original;
            }
        }
        return stubIntent;
    }

    public static void rewriteApplicationInfo(ApplicationInfo info, String sandboxedDataDir) {
        if (info != null && sandboxedDataDir != null) {
            info.dataDir = sandboxedDataDir;
            info.deviceProtectedDataDir = sandboxedDataDir;
            try {
                java.lang.reflect.Field field = ApplicationInfo.class.getDeclaredField("credentialProtectedDataDir");
                field.setAccessible(true);
                field.set(info, sandboxedDataDir);
            } catch (Throwable ignored) {}
        }
    }

    public static void rewritePackageInfo(PackageInfo info, String sandboxedDataDir) {
        if (info != null && info.applicationInfo != null) {
            rewriteApplicationInfo(info.applicationInfo, sandboxedDataDir);
        }
    }
}
