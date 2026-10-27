package com.example.dualworld.engine.fs;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;

/**
 * Sandboxed filesystem routing engine that translates standard Android package paths
 * (/data/data/<pkg>, /data/user/<id>/<pkg>) to isolated per-user clone trees.
 */
public class SandboxFileSystem {
    private static final String TAG = "SandboxFileSystem";

    private final File mVirtualRoot;
    private final File mAppRoot;
    private final File mUsersRoot;

    public SandboxFileSystem(Context context) {
        File dataDir = context.getDataDir();
        this.mVirtualRoot = new File(dataDir, "virtual");
        this.mAppRoot = new File(mVirtualRoot, "app");
        this.mUsersRoot = new File(mVirtualRoot, "users");

        if (!mAppRoot.exists()) mAppRoot.mkdirs();
        if (!mUsersRoot.exists()) mUsersRoot.mkdirs();
    }

    public File getVirtualRoot() {
        return mVirtualRoot;
    }

    public File getAppDir(String packageName) {
        File dir = new File(mAppRoot, packageName);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public File getBaseApk(String packageName) {
        return new File(getAppDir(packageName), "base.apk");
    }

    public File getNativeLibDir(String packageName, String abi) {
        File dir = new File(new File(getAppDir(packageName), "lib"), abi);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public File getOatDir(String packageName) {
        File dir = new File(getAppDir(packageName), "oat");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public File getUserDataDir(String packageName, int userId) {
        File userDir = new File(mUsersRoot, String.valueOf(userId));
        File appDataDir = new File(userDir, packageName);
        if (!appDataDir.exists()) {
            appDataDir.mkdirs();
            new File(appDataDir, "files").mkdirs();
            new File(appDataDir, "databases").mkdirs();
            new File(appDataDir, "shared_prefs").mkdirs();
            new File(appDataDir, "cache").mkdirs();
            new File(appDataDir, "code_cache").mkdirs();
        }
        return appDataDir;
    }

    public File getUserExternalDataDir(Context context, String packageName, int userId) {
        File extRoot = context.getExternalFilesDir(null);
        if (extRoot == null) {
            extRoot = Environment.getExternalStorageDirectory();
        }
        File virtualExt = new File(extRoot, "virtual_storage/users/" + userId + "/" + packageName);
        if (!virtualExt.exists()) {
            virtualExt.mkdirs();
        }
        return virtualExt;
    }

    /**
     * Translates a guest-requested file path into its sandboxed equivalent.
     */
    public String redirectPath(String originalPath, String packageName, int userId) {
        if (originalPath == null) return null;

        String stdUserPath = "/data/user/" + userId + "/" + packageName;
        String stdDataPath = "/data/data/" + packageName;

        File sandboxDir = getUserDataDir(packageName, userId);

        if (originalPath.startsWith(stdUserPath)) {
            String relative = originalPath.substring(stdUserPath.length());
            return new File(sandboxDir, relative).getAbsolutePath();
        }
        if (originalPath.startsWith(stdDataPath)) {
            String relative = originalPath.substring(stdDataPath.length());
            return new File(sandboxDir, relative).getAbsolutePath();
        }

        return originalPath;
    }

    public void cleanUserSandbox(String packageName, int userId) {
        File dir = new File(new File(mUsersRoot, String.valueOf(userId)), packageName);
        deleteRecursive(dir);
        Log.i(TAG, "Purged sandbox for " + packageName + " (user " + userId + ")");
    }

    public void cleanAppContainers(String packageName) {
        File appDir = new File(mAppRoot, packageName);
        deleteRecursive(appDir);
        Log.i(TAG, "Purged app binaries for " + packageName);
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) return;
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }
}
