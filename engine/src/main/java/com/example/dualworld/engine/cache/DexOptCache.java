package com.example.dualworld.engine.cache;

import android.util.Log;

import java.io.File;

/**
 * Tracks DEX optimization (odex/vdex) artifacts per clone container so that
 * subsequent app launches skip redundant runtime compilation.
 */
public class DexOptCache {
    private static final String TAG = "DexOptCache";

    public static boolean isDexOptValid(File apkFile, File oatDir) {
        if (apkFile == null || !apkFile.exists() || oatDir == null || !oatDir.exists()) {
            return false;
        }

        File[] artifacts = oatDir.listFiles();
        if (artifacts == null || artifacts.length == 0) {
            return false;
        }

        long apkLastModified = apkFile.lastModified();
        for (File artifact : artifacts) {
            String name = artifact.getName();
            if (name.endsWith(".odex") || name.endsWith(".vdex") || name.endsWith(".dex")) {
                if (artifact.lastModified() >= apkLastModified && artifact.length() > 0) {
                    Log.d(TAG, "Valid DexOpt artifact found: " + artifact.getName());
                    return true;
                }
            }
        }
        return false;
    }

    public static void markDexOptComplete(File oatDir) {
        if (oatDir != null && oatDir.exists()) {
            File marker = new File(oatDir, ".dexopt_complete");
            try {
                marker.createNewFile();
                marker.setLastModified(System.currentTimeMillis());
            } catch (Exception e) {
                Log.w(TAG, "Failed to create dexopt marker", e);
            }
        }
    }

    public static void invalidate(File oatDir) {
        if (oatDir != null && oatDir.exists()) {
            File[] files = oatDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            Log.d(TAG, "Invalidated DexOpt artifacts in " + oatDir.getAbsolutePath());
        }
    }
}
