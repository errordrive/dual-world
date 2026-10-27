package com.example.dualworld.engine.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Two-level (Memory LruCache + Disk LRU) cache for decoded guest launcher icons.
 * Caps disk consumption at 50 MB and provides asynchronous icon extraction.
 */
public class IconCache {
    private static final String TAG = "IconCache";
    private static final long MAX_DISK_CACHE_BYTES = 50 * 1024 * 1024; // 50 MB
    private static final int MEM_CACHE_SIZE_KB = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8); // 1/8th of heap

    private static volatile IconCache sInstance;

    private final Context mContext;
    private final File mDiskDir;
    private final LruCache<String, Bitmap> mMemoryCache;
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(2);
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public interface IconCallback {
        void onIconLoaded(Bitmap bitmap);
    }

    public static IconCache get(Context context) {
        if (sInstance == null) {
            synchronized (IconCache.class) {
                if (sInstance == null) {
                    Context safeContext = (context != null && context.getApplicationContext() != null)
                            ? context.getApplicationContext() : context;
                    sInstance = new IconCache(safeContext);
                }
            }
        }
        return sInstance;
    }

    private IconCache(Context context) {
        mContext = context;
        File cacheDir = (context != null) ? context.getCacheDir() : null;
        if (cacheDir == null && context != null && context.getFilesDir() != null) {
            cacheDir = new File(context.getFilesDir(), "cache");
        }
        mDiskDir = (cacheDir != null) ? new File(cacheDir, "virtual_icon_cache") : new File("/sdcard/virtual_icon_cache");
        if (!mDiskDir.exists()) {
            mDiskDir.mkdirs();
        }

        mMemoryCache = new LruCache<String, Bitmap>(MEM_CACHE_SIZE_KB) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public Bitmap getCachedSync(String packageName, int userId) {
        String key = makeKey(packageName, userId);
        Bitmap memBitmap = mMemoryCache.get(key);
        if (memBitmap != null && !memBitmap.isRecycled()) {
            return memBitmap;
        }

        File file = new File(mDiskDir, key + ".png");
        if (file.exists()) {
            try {
                Bitmap diskBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (diskBitmap != null) {
                    mMemoryCache.put(key, diskBitmap);
                    return diskBitmap;
                }
            } catch (Exception e) {
                Log.w(TAG, "Corrupt disk icon for " + key, e);
            }
        }
        return null;
    }

    public void loadAsync(String packageName, int userId, IconCallback callback) {
        String key = makeKey(packageName, userId);
        Bitmap cached = getCachedSync(packageName, userId);
        if (cached != null) {
            callback.onIconLoaded(cached);
            return;
        }

        mExecutor.execute(() -> {
            Bitmap loaded = null;
            try {
                Drawable d = mContext.getPackageManager().getApplicationIcon(packageName);
                loaded = drawableToBitmap(d);
                if (loaded != null) {
                    put(packageName, userId, loaded);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not load system icon for " + packageName, e);
            }

            final Bitmap finalResult = loaded;
            mMainHandler.post(() -> callback.onIconLoaded(finalResult));
        });
    }

    public void put(String packageName, int userId, Bitmap bitmap) {
        if (bitmap == null) return;
        String key = makeKey(packageName, userId);
        mMemoryCache.put(key, bitmap);

        mExecutor.execute(() -> {
            try {
                enforceDiskLimit();
                File file = new File(mDiskDir, key + ".png");
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to write icon to disk for " + key, e);
            }
        });
    }

    public void evict(String packageName, int userId) {
        String key = makeKey(packageName, userId);
        mMemoryCache.remove(key);
        File file = new File(mDiskDir, key + ".png");
        if (file.exists()) {
            file.delete();
        }
    }

    public void clearAll() {
        mMemoryCache.evictAll();
        File[] files = mDiskDir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
    }

    public long getDiskUsageBytes() {
        long total = 0;
        File[] files = mDiskDir.listFiles();
        if (files != null) {
            for (File f : files) {
                total += f.length();
            }
        }
        return total;
    }

    private void enforceDiskLimit() {
        long usage = getDiskUsageBytes();
        if (usage <= MAX_DISK_CACHE_BYTES) return;

        File[] files = mDiskDir.listFiles();
        if (files == null) return;

        // Oldest file first
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : files) {
            usage -= f.length();
            f.delete();
            if (usage < MAX_DISK_CACHE_BYTES * 0.8) break;
        }
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        int width = Math.max(drawable.getIntrinsicWidth(), 96);
        int height = Math.max(drawable.getIntrinsicHeight(), 96);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private String makeKey(String packageName, int userId) {
        return packageName + "_u" + userId;
    }
}
