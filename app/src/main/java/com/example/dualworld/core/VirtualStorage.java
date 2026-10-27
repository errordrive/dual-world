package com.example.dualworld.core;

import android.content.Context;
import java.io.File;

/**
 * Sandboxed Filesystem Directory Manager for Dual World.
 * Ensures guest apps write to their dedicated per-user sandbox.
 */
public class VirtualStorage {
    private final File mVirtualBaseDir;
    private final File mVirtualExternalDir;

    public VirtualStorage(Context context) {
        // Internal data root: /data/data/com.example.dualworld/virtual/
        mVirtualBaseDir = new File(context.getFilesDir().getParentFile(), "virtual");
        if (!mVirtualBaseDir.exists()) {
            mVirtualBaseDir.mkdirs();
        }

        // External data root: /sdcard/Android/data/com.example.dualworld/files/virtual/
        File externalFiles = context.getExternalFilesDir(null);
        if (externalFiles != null) {
            mVirtualExternalDir = new File(externalFiles, "virtual");
        } else {
            mVirtualExternalDir = new File(mVirtualBaseDir, "external");
        }
        if (!mVirtualExternalDir.exists()) {
            mVirtualExternalDir.mkdirs();
        }
    }

    public File getDataDir(String packageName, int userId) {
        File userDir = new File(mVirtualBaseDir, "user/" + userId);
        File appDir = new File(userDir, packageName);
        if (!appDir.exists()) {
            appDir.mkdirs();
            new File(appDir, "files").mkdirs();
            new File(appDir, "databases").mkdirs();
            new File(appDir, "shared_prefs").mkdirs();
            new File(appDir, "cache").mkdirs();
        }
        return appDir;
    }

    public File getExternalDataDir(String packageName, int userId) {
        File userExt = new File(mVirtualExternalDir, "user/" + userId);
        File appExt = new File(userExt, packageName);
        if (!appExt.exists()) {
            appExt.mkdirs();
        }
        return appExt;
    }

    public String redirectPath(String originalPath, String guestPackageName, int userId) {
        if (originalPath == null) return null;
        String guestDataPrefix = "/data/data/" + guestPackageName;
        String guestUserPrefix = "/data/user/0/" + guestPackageName;

        File sandboxedDir = getDataDir(guestPackageName, userId);
        if (originalPath.startsWith(guestDataPrefix)) {
            return originalPath.replace(guestDataPrefix, sandboxedDir.getAbsolutePath());
        }
        if (originalPath.startsWith(guestUserPrefix)) {
            return originalPath.replace(guestUserPrefix, sandboxedDir.getAbsolutePath());
        }
        return originalPath;
    }
}
