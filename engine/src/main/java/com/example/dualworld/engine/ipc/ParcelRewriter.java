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

    public static Intent wrapStubIntent(Intent original, ComponentName stubComponent, String guestPkg, int userId) {
        Intent stubIntent = new Intent();
        stubIntent.setComponent(stubComponent);
        if (original.getAction() != null) {
            stubIntent.setAction(original.getAction());
        }
        stubIntent.addFlags(original.getFlags());
        stubIntent.putExtra(EXTRA_TARGET_INTENT, original);
        stubIntent.putExtra(EXTRA_GUEST_PKG, guestPkg);
        stubIntent.putExtra(EXTRA_USER_ID, userId);
        stubIntent.putExtra(EXTRA_STUB_CLASS, stubComponent.getClassName());
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
