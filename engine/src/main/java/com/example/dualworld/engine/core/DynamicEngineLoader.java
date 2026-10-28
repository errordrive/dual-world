package com.example.dualworld.engine.core;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import com.example.dualworld.engine.cache.LaunchDiagnostics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

/**
 * Dynamic core engine loader inspired by modern virtualization architectures.
 * Discovers and extracts the core engine payload (assets/engine-core.apk) into an
 * isolated private directory at runtime, links native libraries, constructs a DexClassLoader,
 * and initializes the core virtualization framework dynamically with graceful in-tree fallback.
 */
public class DynamicEngineLoader {
    private static final String TAG = "DynamicEngineLoader";
    private static final String ASSET_NAME = "engine-core.apk";
    private static final String ENGINE_DIR = "engine_core";
    private static final String ENGINE_OPT_DIR = "engine_core_opt";
    private static final String ENGINE_LIB_DIR = "lib";

    private static volatile boolean sLoaded = false;
    private static volatile boolean sInitialized = false;
    private static ClassLoader sClassLoader = null;
    private static Object sEngineInstance = null;

    /**
     * Installs and prepares the dynamic core engine from assets.
     * Must be called in attachBaseContext before components initialize.
     *
     * @param context Host application context
     * @return true if the dynamic engine was successfully extracted and loaded
     */
    public static synchronized boolean install(Context context) {
        if (sLoaded) {
            return true;
        }

        LaunchDiagnostics.log(TAG, "Starting dynamic core engine loader initialization...");

        try {
            AssetManager assets = context.getAssets();
            boolean assetExists = false;
            try (InputStream testStream = assets.open(ASSET_NAME)) {
                if (testStream != null) {
                    assetExists = true;
                }
            } catch (Throwable t) {
                assetExists = false;
            }

            if (!assetExists) {
                LaunchDiagnostics.log(TAG, "Asset '" + ASSET_NAME + "' not present; using in-tree engine directly.");
                return false;
            }

            File engineDir = context.getDir(ENGINE_DIR, Context.MODE_PRIVATE);
            if (!engineDir.exists() && !engineDir.mkdirs()) {
                Log.w(TAG, "Could not create private engine dir: " + engineDir.getAbsolutePath());
            }

            File targetApk = new File(engineDir, ASSET_NAME);
            boolean needsExtraction = !targetApk.exists() || targetApk.length() == 0;

            if (!needsExtraction) {
                try {
                    AssetFileDescriptor afd = assets.openFd(ASSET_NAME);
                    if (afd.getLength() > 0 && targetApk.length() != afd.getLength()) {
                        needsExtraction = true;
                    }
                    afd.close();
                } catch (Throwable ignored) {
                    // Asset compressed; file length check suffices
                }
            }

            if (needsExtraction) {
                LaunchDiagnostics.log(TAG, "Extracting dynamic engine asset '" + ASSET_NAME + "' to " + targetApk.getAbsolutePath());
                File tempApk = new File(engineDir, ASSET_NAME + ".tmp");
                try (InputStream in = assets.open(ASSET_NAME);
                     FileOutputStream out = new FileOutputStream(tempApk)) {
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                }

                if (targetApk.exists()) {
                    targetApk.delete();
                }

                if (!tempApk.renameTo(targetApk)) {
                    LaunchDiagnostics.log(TAG, "Failed to atomically commit extracted engine payload");
                    return false;
                }
                LaunchDiagnostics.log(TAG, "Dynamic engine payload successfully committed (" + targetApk.length() + " bytes)");
            }

            // Extract ABI native libraries from target APK if present
            File libDir = new File(engineDir, ENGINE_LIB_DIR);
            extractNativeLibraries(targetApk, libDir);

            // Configure optimization and class loading
            File optDir = context.getDir(ENGINE_OPT_DIR, Context.MODE_PRIVATE);
            if (!optDir.exists() && !optDir.mkdirs()) {
                Log.w(TAG, "Could not create dexopt dir: " + optDir.getAbsolutePath());
            }

            String nativeLibPath = resolveNativeLibraryPath(libDir);
            ClassLoader parent = context.getClassLoader();

            LaunchDiagnostics.log(TAG, "Instantiating DexClassLoader for dynamic engine: " + targetApk.getAbsolutePath());
            DexClassLoader dexClassLoader = new DexClassLoader(
                    targetApk.getAbsolutePath(),
                    optDir.getAbsolutePath(),
                    nativeLibPath,
                    parent
            );

            sClassLoader = dexClassLoader;

            // Reflectively obtain the dynamic VirtualEngine instance
            Class<?> engineClazz = dexClassLoader.loadClass("com.example.dualworld.engine.core.VirtualEngine");
            Method getMethod = engineClazz.getMethod("get");
            sEngineInstance = getMethod.invoke(null);

            sLoaded = true;
            LaunchDiagnostics.log(TAG, "Dynamic core engine successfully loaded from asset payload!");
            return true;
        } catch (Throwable t) {
            LaunchDiagnostics.log(TAG, "Dynamic engine load warning: " + t.getMessage() + "; falling back gracefully to in-tree engine.");
            Log.w(TAG, "DynamicEngineLoader installation fallback", t);
            return false;
        }
    }

    /**
     * Initializes the core virtualization engine.
     * Dispatches to the dynamically loaded payload if active, otherwise delegates to the in-tree engine.
     *
     * @param application Main Application context
     */
    public static synchronized void init(Application application) {
        if (sInitialized) {
            return;
        }

        if (sLoaded && sEngineInstance != null) {
            try {
                LaunchDiagnostics.log(TAG, "Initializing dynamic VirtualEngine from asset payload...");
                Method initMethod = sEngineInstance.getClass().getMethod("init", Application.class);
                initMethod.invoke(sEngineInstance, application);
                sInitialized = true;
                LaunchDiagnostics.log(TAG, "Dynamic core engine initialized successfully!");
                return;
            } catch (Throwable t) {
                LaunchDiagnostics.log(TAG, "Dynamic engine init warning: " + t.getMessage() + "; falling back to in-tree VirtualEngine.");
                Log.e(TAG, "Dynamic engine init error", t);
            }
        }

        // Graceful fallback to compiled in-tree engine
        try {
            LaunchDiagnostics.log(TAG, "Initializing in-tree VirtualEngine fallback...");
            VirtualEngine.get().init(application);
            sInitialized = true;
            LaunchDiagnostics.log(TAG, "In-tree VirtualEngine fallback initialized successfully.");
        } catch (Throwable t) {
            LaunchDiagnostics.log(TAG, "In-tree VirtualEngine init warning: " + t.getMessage());
            Log.e(TAG, "Fallback engine init error", t);
        }
    }

    /**
     * Returns true if the dynamic engine payload is actively loaded and engaged.
     */
    public static boolean isDynamicLoaded() {
        return sLoaded && sEngineInstance != null;
    }

    /**
     * Returns the active engine ClassLoader (DexClassLoader if dynamic, or host ClassLoader).
     */
    public static ClassLoader getClassLoader() {
        return sClassLoader != null ? sClassLoader : DynamicEngineLoader.class.getClassLoader();
    }

    /**
     * Returns the active engine instance (dynamic if loaded, otherwise in-tree singleton).
     */
    public static Object getEngineInstance() {
        return sEngineInstance != null ? sEngineInstance : VirtualEngine.get();
    }

    private static void extractNativeLibraries(File apkFile, File outLibDir) {
        if (!apkFile.exists()) return;
        try (ZipFile zip = new ZipFile(apkFile)) {
            String[] supportedAbis = Build.SUPPORTED_ABIS;
            if (supportedAbis == null || supportedAbis.length == 0) return;

            String selectedAbi = null;
            for (String abi : supportedAbis) {
                String prefix = "lib/" + abi + "/";
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().startsWith(prefix) && entry.getName().endsWith(".so")) {
                        selectedAbi = abi;
                        break;
                    }
                }
                if (selectedAbi != null) break;
            }

            if (selectedAbi == null) {
                return;
            }

            File abiDir = new File(outLibDir, selectedAbi);
            if (!abiDir.exists() && !abiDir.mkdirs()) {
                return;
            }

            String prefix = "lib/" + selectedAbi + "/";
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().startsWith(prefix) && entry.getName().endsWith(".so")) {
                    String soName = entry.getName().substring(prefix.length());
                    File targetSo = new File(abiDir, soName);
                    if (!targetSo.exists() || targetSo.length() != entry.getSize()) {
                        try (InputStream in = zip.getInputStream(entry);
                             FileOutputStream out = new FileOutputStream(targetSo)) {
                            byte[] buf = new byte[8192];
                            int r;
                            while ((r = in.read(buf)) != -1) {
                                out.write(buf, 0, r);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Native library extraction note: " + t.getMessage());
        }
    }

    private static String resolveNativeLibraryPath(File libDir) {
        if (!libDir.exists()) return null;
        if (Build.SUPPORTED_ABIS != null) {
            for (String abi : Build.SUPPORTED_ABIS) {
                File abiDir = new File(libDir, abi);
                if (abiDir.exists() && abiDir.isDirectory()) {
                    return abiDir.getAbsolutePath();
                }
            }
        }
        return libDir.getAbsolutePath();
    }
}
