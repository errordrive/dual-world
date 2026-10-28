package com.example.dualworld.engine.hook;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.example.dualworld.engine.core.VirtualMachine;
import com.example.dualworld.engine.fs.SandboxFileSystem;
import com.example.dualworld.engine.ipc.IpcRouter;
import com.example.dualworld.engine.ipc.ParcelRewriter;

import dalvik.system.DexClassLoader;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom Instrumentation proxy intercepting activity lifecycle creation, class resolution,
 * and context injection for virtualized guest apps.
 */
public class InstrumentationProxy extends Instrumentation {
    private static final String TAG = "InstrumentationProxy";

    private static final Map<String, ClassLoader> sGuestLoaders = new ConcurrentHashMap<>();

    private final Instrumentation mBase;
    private final Context mHostContext;

    public static InstrumentationProxy install(Context hostContext) {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method currentAtMethod = atClass.getDeclaredMethod("currentActivityThread");
            currentAtMethod.setAccessible(true);
            Object activityThread = currentAtMethod.invoke(null);

            Field mInstrumentationField = atClass.getDeclaredField("mInstrumentation");
            mInstrumentationField.setAccessible(true);
            Instrumentation original = (Instrumentation) mInstrumentationField.get(activityThread);

            if (!(original instanceof InstrumentationProxy)) {
                InstrumentationProxy proxy = new InstrumentationProxy(original, hostContext);
                mInstrumentationField.set(activityThread, proxy);
                Log.i(TAG, "InstrumentationProxy installed on ActivityThread");
                return proxy;
            } else {
                return (InstrumentationProxy) original;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install InstrumentationProxy", t);
            return null;
        }
    }

    public InstrumentationProxy(Instrumentation base, Context hostContext) {
        this.mBase = base;
        this.mHostContext = hostContext;
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        // Fast path: host UI and internal activities instantiate normally
        if (className != null && (
                className.startsWith("com.example.dualworld.ui.") ||
                className.equals("com.example.dualworld.MainActivity")
        )) {
            return mBase.newActivity(cl, className, intent);
        }

        Intent targetIntent = intent;
        String guestPkg = null;
        int userId = 0;
        String guestApkPath = null;
        String guestDataDir = null;
        String guestClassName = null;

        if (intent != null) {
            if (intent.hasExtra(ParcelRewriter.EXTRA_TARGET_INTENT)) {
                Intent target = intent.getParcelableExtra(ParcelRewriter.EXTRA_TARGET_INTENT);
                if (target != null) {
                    targetIntent = target;
                    if (target.getComponent() != null) {
                        guestClassName = target.getComponent().getClassName();
                    }
                }
            }
            if (intent.hasExtra(ParcelRewriter.EXTRA_GUEST_PKG)) {
                guestPkg = intent.getStringExtra(ParcelRewriter.EXTRA_GUEST_PKG);
            }
            if (intent.hasExtra(ParcelRewriter.EXTRA_USER_ID)) {
                userId = intent.getIntExtra(ParcelRewriter.EXTRA_USER_ID, 0);
            }
            if (intent.hasExtra(ParcelRewriter.EXTRA_GUEST_APK_PATH)) {
                guestApkPath = intent.getStringExtra(ParcelRewriter.EXTRA_GUEST_APK_PATH);
            }
            if (intent.hasExtra(ParcelRewriter.EXTRA_GUEST_DATA_DIR)) {
                guestDataDir = intent.getStringExtra(ParcelRewriter.EXTRA_GUEST_DATA_DIR);
            }
            if (intent.hasExtra(ParcelRewriter.EXTRA_GUEST_MAIN_CLASS)) {
                if (guestClassName == null) {
                    guestClassName = intent.getStringExtra(ParcelRewriter.EXTRA_GUEST_MAIN_CLASS);
                }
            }
        }

        if (guestClassName == null) {
            if (className != null && !className.startsWith("com.example.dualworld.")) {
                guestClassName = className;
            } else if (targetIntent != null && targetIntent.getComponent() != null) {
                guestClassName = targetIntent.getComponent().getClassName();
            }
        }

        if (guestPkg == null) {
            if (targetIntent != null && targetIntent.getComponent() != null) {
                String compPkg = targetIntent.getComponent().getPackageName();
                if (compPkg != null && !compPkg.equals(mHostContext != null ? mHostContext.getPackageName() : "com.example.dualworld")) {
                    guestPkg = compPkg;
                }
            }
        }

        // If guest target identified, load via composite split-aware DexClassLoader
        if (guestClassName != null && !guestClassName.startsWith("com.example.dualworld.")) {
            ClassLoader guestLoader = getGuestClassLoader(guestPkg, guestApkPath, cl);
            Log.i(TAG, "newActivity resolving guest activity " + guestClassName + " for " + guestPkg);

            try {
                Activity act = mBase.newActivity(guestLoader, guestClassName, targetIntent);
                Log.i(TAG, "Successfully instantiated guest activity: " + guestClassName);
                return act;
            } catch (ClassNotFoundException | NoClassDefFoundError cnfe) {
                Log.w(TAG, "Class " + guestClassName + " not found on guest loader: " + cnfe.getMessage());

                // Fallback 1: Query official launcher activity from system PackageManager
                if (guestPkg != null && mHostContext != null) {
                    try {
                        Intent launchIntent = mHostContext.getPackageManager().getLaunchIntentForPackage(guestPkg);
                        if (launchIntent != null && launchIntent.getComponent() != null) {
                            String altClass = launchIntent.getComponent().getClassName();
                            if (!altClass.equals(guestClassName)) {
                                Log.i(TAG, "Retrying with official system launcher class: " + altClass);
                                return mBase.newActivity(guestLoader, altClass, launchIntent);
                            }
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Fallback launcher retry note: " + t.getMessage());
                    }
                }

                // Fallback 2: Check active VirtualMachine
                if (guestPkg != null) {
                    VirtualMachine vm = IpcRouter.get().route(guestPkg, userId);
                    if (vm != null && vm.getClassLoader() != null && vm.getClassLoader() != guestLoader) {
                        try {
                            return mBase.newActivity(vm.getClassLoader(), guestClassName, targetIntent);
                        } catch (Throwable ignored) {}
                    }
                }

                // Fallback 3: Return stub activity to display user-friendly notice rather than crashing
                Log.e(TAG, "Cannot resolve guest activity " + guestClassName + "; falling back to stub UI", cnfe);
                try {
                    ClassLoader hostCl = (mHostContext != null) ? mHostContext.getClassLoader() : getClass().getClassLoader();
                    return mBase.newActivity(hostCl, "com.example.dualworld.stub.StubActivity$P0A0", intent != null ? intent : targetIntent);
                } catch (Throwable t) {
                    Log.e(TAG, "Failed stub UI fallback", t);
                    throw cnfe;
                }
            } catch (Throwable t) {
                Log.e(TAG, "Unexpected error instantiating guest activity " + guestClassName + ", falling back to stub UI", t);
                try {
                    ClassLoader hostCl = (mHostContext != null) ? mHostContext.getClassLoader() : getClass().getClassLoader();
                    return mBase.newActivity(hostCl, "com.example.dualworld.stub.StubActivity$P0A0", intent != null ? intent : targetIntent);
                } catch (Throwable ignored) {}
                throw new RuntimeException("Guest activity instantiation failed: " + guestClassName, t);
            }
        }

        return mBase.newActivity(cl, className, intent);
    }

    private ClassLoader getGuestClassLoader(String guestPkg, String apkPath, ClassLoader baseCl) {
        String cacheKey = (guestPkg != null && !guestPkg.isEmpty()) ? guestPkg : apkPath;
        if (cacheKey != null) {
            ClassLoader cached = sGuestLoaders.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        StringBuilder dexPathBuilder = new StringBuilder();
        if (apkPath != null && new File(apkPath).exists()) {
            dexPathBuilder.append(apkPath);
        }

        StringBuilder libPathBuilder = new StringBuilder();
        Context ctx = mHostContext;

        if (ctx != null) {
            SandboxFileSystem fs = new SandboxFileSystem(ctx);
            if (guestPkg != null && !guestPkg.isEmpty()) {
                File baseApk = fs.getBaseApk(guestPkg);
                if (baseApk.exists() && !dexPathBuilder.toString().contains(baseApk.getAbsolutePath())) {
                    if (dexPathBuilder.length() > 0) dexPathBuilder.append(File.pathSeparator);
                    dexPathBuilder.append(baseApk.getAbsolutePath());
                }

                // Include any split APKs in sandbox app dir
                File appDir = fs.getAppDir(guestPkg);
                if (appDir.exists() && appDir.isDirectory()) {
                    File[] files = appDir.listFiles((dir, name) -> name.endsWith(".apk") && !name.equals("base.apk"));
                    if (files != null) {
                        for (File f : files) {
                            if (dexPathBuilder.length() > 0) dexPathBuilder.append(File.pathSeparator);
                            dexPathBuilder.append(f.getAbsolutePath());
                        }
                    }
                }

                // Add sandboxed native library dir
                String primaryAbi = (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) ? Build.SUPPORTED_ABIS[0] : "arm64-v8a";
                File libDir = fs.getNativeLibDir(guestPkg, primaryAbi);
                if (libDir.exists()) {
                    libPathBuilder.append(libDir.getAbsolutePath());
                }

                // Also check installed app on host system for original APK and split APKs
                try {
                    ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(guestPkg, 0);
                    if (ai != null) {
                        if (dexPathBuilder.length() == 0 && ai.sourceDir != null && new File(ai.sourceDir).exists()) {
                            dexPathBuilder.append(ai.sourceDir);
                        }
                        if (ai.splitSourceDirs != null) {
                            for (String split : ai.splitSourceDirs) {
                                if (split != null && new File(split).exists() && !dexPathBuilder.toString().contains(split)) {
                                    if (dexPathBuilder.length() > 0) dexPathBuilder.append(File.pathSeparator);
                                    dexPathBuilder.append(split);
                                }
                            }
                        }
                        if (ai.nativeLibraryDir != null && new File(ai.nativeLibraryDir).exists()) {
                            if (libPathBuilder.length() > 0) libPathBuilder.append(File.pathSeparator);
                            libPathBuilder.append(ai.nativeLibraryDir);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        String dexPath = dexPathBuilder.toString();
        if (dexPath.isEmpty()) {
            Log.w(TAG, "No dex path found for guest " + guestPkg + ", fallback to base classloader");
            return baseCl != null ? baseCl : getClass().getClassLoader();
        }

        ClassLoader parent = baseCl != null ? baseCl : (ctx != null ? ctx.getClassLoader() : getClass().getClassLoader());
        String libPath = libPathBuilder.length() > 0 ? libPathBuilder.toString() : null;

        try {
            Log.i(TAG, "Constructed DexClassLoader for " + guestPkg + "\n  dexPath=" + dexPath + "\n  libPath=" + libPath);
            ClassLoader guestLoader = new DexClassLoader(dexPath, null, libPath, parent);
            if (cacheKey != null) {
                sGuestLoaders.put(cacheKey, guestLoader);
            }
            return guestLoader;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to instantiate DexClassLoader for " + guestPkg, t);
            return parent;
        }
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        Intent intent = activity.getIntent();
        if (intent != null && intent.hasExtra(ParcelRewriter.EXTRA_TARGET_INTENT)) {
            String guestPkg = intent.getStringExtra(ParcelRewriter.EXTRA_GUEST_PKG);
            int userId = intent.getIntExtra(ParcelRewriter.EXTRA_USER_ID, 0);
            VirtualMachine vm = IpcRouter.get().route(guestPkg, userId);

            if (vm != null) {
                Log.d(TAG, "callActivityOnCreate: Bound activity " + activity.getClass().getName() + " to VM " + guestPkg);
            }
        }

        mBase.callActivityOnCreate(activity, icicle);
    }

    @Override
    public void callApplicationOnCreate(Application app) {
        mBase.callApplicationOnCreate(app);
    }

    public Instrumentation getBase() {
        return mBase;
    }
}
