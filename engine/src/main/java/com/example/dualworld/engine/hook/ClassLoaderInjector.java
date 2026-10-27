package com.example.dualworld.engine.hook;

import android.content.Context;
import android.util.Log;

import dalvik.system.DexClassLoader;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * Constructs and injects a DelegateClassLoader chain resolving guest APK classes
 * and native shared libraries while preserving host framework access.
 */
public class ClassLoaderInjector {
    private static final String TAG = "ClassLoaderInjector";

    public static class DelegateClassLoader extends ClassLoader {
        private final ClassLoader mHostClassLoader;
        private final DexClassLoader mGuestClassLoader;
        private final String mGuestPackage;

        public DelegateClassLoader(ClassLoader hostClassLoader, DexClassLoader guestClassLoader, String guestPackage) {
            super(hostClassLoader);
            this.mHostClassLoader = hostClassLoader;
            this.mGuestClassLoader = guestClassLoader;
            this.mGuestPackage = guestPackage;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            // Give preference to host framework & engine classes
            if (name.startsWith("com.example.dualworld.") || name.startsWith("android.") || name.startsWith("androidx.")) {
                try {
                    return mHostClassLoader.loadClass(name);
                } catch (ClassNotFoundException ignored) {}
            }

            // Attempt to load through guest DexClassLoader
            if (mGuestClassLoader != null) {
                try {
                    return mGuestClassLoader.loadClass(name);
                } catch (ClassNotFoundException ignored) {}
            }

            return super.findClass(name);
        }

        public DexClassLoader getGuestClassLoader() {
            return mGuestClassLoader;
        }

        public String getGuestPackage() {
            return mGuestPackage;
        }
    }

    public static DelegateClassLoader createDelegate(Context hostContext, File guestApk, File oatDir, File nativeLibDir, String guestPackage) {
        ClassLoader hostClassLoader = (hostContext != null) ? hostContext.getClassLoader() : ClassLoader.getSystemClassLoader();
        if (guestApk == null || !guestApk.exists()) {
            Log.e(TAG, "Cannot create DexClassLoader: guest APK file does not exist at " + (guestApk != null ? guestApk.getAbsolutePath() : "null"));
            return new DelegateClassLoader(hostClassLoader, null, guestPackage);
        }

        String dexPath = guestApk.getAbsolutePath();
        String librarySearchPath = (nativeLibDir != null && nativeLibDir.exists()) ? nativeLibDir.getAbsolutePath() : null;

        DexClassLoader guestLoader = null;
        try {
            guestLoader = new DexClassLoader(
                    dexPath,
                    null,
                    librarySearchPath,
                    hostClassLoader
            );
        } catch (Throwable t) {
            Log.e(TAG, "Failed to instantiate DexClassLoader for " + guestPackage, t);
        }

        DelegateClassLoader delegate = new DelegateClassLoader(hostClassLoader, guestLoader, guestPackage);
        Log.i(TAG, "Created DelegateClassLoader for " + guestPackage + " dex=" + dexPath);
        return delegate;
    }

    public static void injectIntoLoadedApk(Context context, ClassLoader delegate) {
        try {
            // context is ContextImpl -> mPackageInfo is LoadedApk
            Class<?> contextImplClass = Class.forName("android.app.ContextImpl");
            Field mPackageInfoField = contextImplClass.getDeclaredField("mPackageInfo");
            mPackageInfoField.setAccessible(true);
            Object loadedApk = mPackageInfoField.get(context);

            if (loadedApk != null) {
                Class<?> loadedApkClass = Class.forName("android.app.LoadedApk");
                Field mClassLoaderField = loadedApkClass.getDeclaredField("mClassLoader");
                mClassLoaderField.setAccessible(true);
                mClassLoaderField.set(loadedApk, delegate);
                Log.d(TAG, "Spliced DelegateClassLoader into ContextImpl.mPackageInfo");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not inject into LoadedApk directly: " + t.getMessage());
        }
    }
}
