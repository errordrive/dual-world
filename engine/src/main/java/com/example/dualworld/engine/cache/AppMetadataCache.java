package com.example.dualworld.engine.cache;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-backed persistent cache for parsed guest app metadata, component manifests,
 * installation timestamps, launch metrics, and hash-based cache invalidation.
 */
public class AppMetadataCache extends SQLiteOpenHelper {
    private static final String TAG = "AppMetadataCache";
    private static final String DB_NAME = "virtual_metadata_cache.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE_APPS = "cached_apps";
    public static final String COL_PKG = "package_name";
    public static final String COL_USER_ID = "user_id";
    public static final String COL_APP_NAME = "app_name";
    public static final String COL_APK_PATH = "apk_path";
    public static final String COL_APK_HASH = "apk_hash";
    public static final String COL_VERSION_NAME = "version_name";
    public static final String COL_VERSION_CODE = "version_code";
    public static final String COL_INSTALLED_AT = "installed_at";
    public static final String COL_LAST_LAUNCH_AT = "last_launch_at";
    public static final String COL_LAUNCH_COUNT = "launch_count";
    public static final String COL_COMPONENTS_JSON = "components_json";
    public static final String COL_PERMISSIONS_JSON = "permissions_json";

    public static class AppEntry {
        public String packageName;
        public int userId;
        public String appName;
        public String apkPath;
        public String apkHash;
        public String versionName;
        public int versionCode;
        public long installedAt;
        public long lastLaunchAt;
        public int launchCount;
        public String componentsJson;
        public String permissionsJson;
    }

    private static volatile AppMetadataCache sInstance;

    public static AppMetadataCache get(Context context) {
        if (sInstance == null) {
            synchronized (AppMetadataCache.class) {
                if (sInstance == null) {
                    Context safeContext = (context != null && context.getApplicationContext() != null)
                            ? context.getApplicationContext() : context;
                    sInstance = new AppMetadataCache(safeContext);
                }
            }
        }
        return sInstance;
    }

    private AppMetadataCache(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_APPS + " (" +
                COL_PKG + " TEXT NOT NULL, " +
                COL_USER_ID + " INTEGER NOT NULL, " +
                COL_APP_NAME + " TEXT, " +
                COL_APK_PATH + " TEXT, " +
                COL_APK_HASH + " TEXT, " +
                COL_VERSION_NAME + " TEXT, " +
                COL_VERSION_CODE + " INTEGER DEFAULT 0, " +
                COL_INSTALLED_AT + " INTEGER DEFAULT 0, " +
                COL_LAST_LAUNCH_AT + " INTEGER DEFAULT 0, " +
                COL_LAUNCH_COUNT + " INTEGER DEFAULT 0, " +
                COL_COMPONENTS_JSON + " TEXT, " +
                COL_PERMISSIONS_JSON + " TEXT, " +
                "PRIMARY KEY (" + COL_PKG + ", " + COL_USER_ID + ")" +
                ")";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_APPS);
        onCreate(db);
    }

    public synchronized void putApp(AppEntry entry) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_PKG, entry.packageName);
        cv.put(COL_USER_ID, entry.userId);
        cv.put(COL_APP_NAME, entry.appName);
        cv.put(COL_APK_PATH, entry.apkPath);
        cv.put(COL_APK_HASH, entry.apkHash);
        cv.put(COL_VERSION_NAME, entry.versionName);
        cv.put(COL_VERSION_CODE, entry.versionCode);
        cv.put(COL_INSTALLED_AT, entry.installedAt);
        cv.put(COL_LAST_LAUNCH_AT, entry.lastLaunchAt);
        cv.put(COL_LAUNCH_COUNT, entry.launchCount);
        cv.put(COL_COMPONENTS_JSON, entry.componentsJson);
        cv.put(COL_PERMISSIONS_JSON, entry.permissionsJson);

        db.insertWithOnConflict(TABLE_APPS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        Log.d(TAG, "Cached metadata for " + entry.packageName + " (user " + entry.userId + ")");
    }

    public synchronized AppEntry getApp(String packageName, int userId) {
        SQLiteDatabase db = getReadableDatabase();
        String selection = COL_PKG + " = ? AND " + COL_USER_ID + " = ?";
        String[] args = {packageName, String.valueOf(userId)};
        try (Cursor c = db.query(TABLE_APPS, null, selection, args, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return cursorToEntry(c);
            }
        }
        return null;
    }

    public synchronized List<AppEntry> getAllApps() {
        List<AppEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_APPS, null, null, null, null, null, COL_LAST_LAUNCH_AT + " DESC")) {
            if (c != null && c.moveToFirst()) {
                do {
                    list.add(cursorToEntry(c));
                } while (c.moveToNext());
            }
        }
        return list;
    }

    public synchronized boolean isHashValid(String packageName, int userId, String currentApkPath) {
        AppEntry entry = getApp(packageName, userId);
        if (entry == null || entry.apkHash == null) {
            return false;
        }
        String calculated = calculateSha256(new File(currentApkPath));
        return entry.apkHash.equalsIgnoreCase(calculated);
    }

    public synchronized void recordLaunch(String packageName, int userId) {
        SQLiteDatabase db = getWritableDatabase();
        String sql = "UPDATE " + TABLE_APPS + " SET " +
                COL_LAUNCH_COUNT + " = " + COL_LAUNCH_COUNT + " + 1, " +
                COL_LAST_LAUNCH_AT + " = ? " +
                "WHERE " + COL_PKG + " = ? AND " + COL_USER_ID + " = ?";
        db.execSQL(sql, new Object[]{System.currentTimeMillis(), packageName, userId});
    }

    public synchronized void updateLabel(String packageName, int userId, String newLabel) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_APP_NAME, newLabel);
        db.update(TABLE_APPS, cv, COL_PKG + " = ? AND " + COL_USER_ID + " = ?",
                new String[]{packageName, String.valueOf(userId)});
    }

    public synchronized void removeApp(String packageName, int userId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_APPS, COL_PKG + " = ? AND " + COL_USER_ID + " = ?",
                new String[]{packageName, String.valueOf(userId)});
    }

    public synchronized void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_APPS, null, null);
    }

    public static String calculateSha256(File file) {
        if (!file.exists()) return "";
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to compute file SHA-256", e);
            return "";
        }
    }

    private AppEntry cursorToEntry(Cursor c) {
        AppEntry e = new AppEntry();
        e.packageName = c.getString(c.getColumnIndexOrThrow(COL_PKG));
        e.userId = c.getInt(c.getColumnIndexOrThrow(COL_USER_ID));
        e.appName = c.getString(c.getColumnIndexOrThrow(COL_APP_NAME));
        e.apkPath = c.getString(c.getColumnIndexOrThrow(COL_APK_PATH));
        e.apkHash = c.getString(c.getColumnIndexOrThrow(COL_APK_HASH));
        e.versionName = c.getString(c.getColumnIndexOrThrow(COL_VERSION_NAME));
        e.versionCode = c.getInt(c.getColumnIndexOrThrow(COL_VERSION_CODE));
        e.installedAt = c.getLong(c.getColumnIndexOrThrow(COL_INSTALLED_AT));
        e.lastLaunchAt = c.getLong(c.getColumnIndexOrThrow(COL_LAST_LAUNCH_AT));
        e.launchCount = c.getInt(c.getColumnIndexOrThrow(COL_LAUNCH_COUNT));
        e.componentsJson = c.getString(c.getColumnIndexOrThrow(COL_COMPONENTS_JSON));
        e.permissionsJson = c.getString(c.getColumnIndexOrThrow(COL_PERMISSIONS_JSON));
        return e;
    }
}
