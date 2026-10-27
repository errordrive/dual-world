package com.example.dualworld.engine.fs;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.example.dualworld.engine.cache.AppMetadataCache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Copies guest APK binaries into the private sandbox, extracts native shared libraries
 * (.so) for the target device ABI, and updates the metadata cache.
 */
public class ApkInstaller {
    private static final String TAG = "ApkInstaller";

    public static boolean installOrUpdate(Context context, SandboxFileSystem fs, String sourceApkPath, String packageName, int userId) {
        try {
            File srcFile = new File(sourceApkPath);
            if (!srcFile.exists()) {
                Log.e(TAG, "Source APK not found: " + sourceApkPath);
                return false;
            }

            File targetApk = fs.getBaseApk(packageName);
            copyFile(srcFile, targetApk);

            // Extract native libraries for device ABI
            String primaryAbi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "arm64-v8a";
            File libDir = fs.getNativeLibDir(packageName, primaryAbi);
            extractNativeLibs(targetApk, libDir, primaryAbi);

            // Ensure user data directories exist
            fs.getUserDataDir(packageName, userId);

            // Populate Metadata Cache
            PackageManager pm = context.getPackageManager();
            PackageInfo pInfo = pm.getPackageArchiveInfo(targetApk.getAbsolutePath(), PackageManager.GET_ACTIVITIES | PackageManager.GET_PERMISSIONS);
            String appName = packageName;
            String versionName = "1.0";
            int versionCode = 1;
            if (pInfo != null) {
                if (pInfo.versionName != null) versionName = pInfo.versionName;
                versionCode = pInfo.versionCode;
                try {
                    ApplicationInfo ai = pInfo.applicationInfo;
                    ai.sourceDir = targetApk.getAbsolutePath();
                    ai.publicSourceDir = targetApk.getAbsolutePath();
                    appName = pm.getApplicationLabel(ai).toString();
                } catch (Exception ignored) {}
            }

            AppMetadataCache.AppEntry entry = new AppMetadataCache.AppEntry();
            entry.packageName = packageName;
            entry.userId = userId;
            entry.appName = appName;
            entry.apkPath = targetApk.getAbsolutePath();
            entry.apkHash = AppMetadataCache.calculateSha256(targetApk);
            entry.versionName = versionName;
            entry.versionCode = versionCode;
            entry.installedAt = System.currentTimeMillis();
            entry.lastLaunchAt = 0;
            entry.launchCount = 0;

            AppMetadataCache.get(context).putApp(entry);

            Log.i(TAG, "Successfully installed " + packageName + " (user " + userId + ") into sandbox: " + targetApk.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to install APK into sandbox: " + packageName, e);
            return false;
        }
    }

    private static void copyFile(File src, File dst) throws Exception {
        if (src.getAbsolutePath().equals(dst.getAbsolutePath())) return;
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[16384];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private static void extractNativeLibs(File apkFile, File targetLibDir, String preferredAbi) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(apkFile))) {
            ZipEntry entry;
            String prefix = "lib/" + preferredAbi + "/";
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith(prefix) && name.endsWith(".so")) {
                    String soName = name.substring(prefix.length());
                    File outSo = new File(targetLibDir, soName);
                    try (FileOutputStream fos = new FileOutputStream(outSo)) {
                        int count;
                        while ((count = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, count);
                        }
                    }
                    Log.d(TAG, "Extracted native lib: " + soName + " to " + outSo.getAbsolutePath());
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            Log.w(TAG, "Native library extraction note for ABI " + preferredAbi + ": " + e.getMessage());
        }
    }
}
