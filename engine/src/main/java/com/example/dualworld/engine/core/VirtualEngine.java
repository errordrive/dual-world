package com.example.dualworld.engine.core;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.dualworld.engine.cache.AppMetadataCache;
import com.example.dualworld.engine.cache.DexOptCache;
import com.example.dualworld.engine.cache.IconCache;
import com.example.dualworld.engine.fs.ApkInstaller;
import com.example.dualworld.engine.fs.SandboxFileSystem;
import com.example.dualworld.engine.hook.ActivityThreadInterceptor;
import com.example.dualworld.engine.hook.ClassLoaderInjector;
import com.example.dualworld.engine.hook.InstrumentationProxy;
import com.example.dualworld.engine.ipc.BinderProxyManager;
import com.example.dualworld.engine.ipc.IpcRouter;
import com.example.dualworld.engine.ipc.ParcelRewriter;
import com.example.dualworld.engine.pm.ComponentResolver;
import com.example.dualworld.engine.pm.GuestPackageManager;
import com.example.dualworld.engine.pm.PermissionMirror;
import com.example.dualworld.engine.process.AgentProcessManager;
import com.example.dualworld.engine.process.StubCoordinator;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Central engine orchestrator uniting core virtualization, isolated file systems,
 * Binder IPC interception, component proxying, and caching.
 * Fully guarded with null-safety and degraded mode resilience.
 */
public class VirtualEngine {
    private static final String TAG = "VirtualEngine";
    private static final VirtualEngine sInstance = new VirtualEngine();

    public enum ProcessRole {
        MAIN,
        SERVER,
        AGENT
    }

    public enum LaunchResult {
        SUCCESS,
        ERROR_NOT_INSTALLED,
        ERROR_APK_MISSING,
        ERROR_NO_LAUNCHER,
        ERROR_AGENT_UNAVAILABLE,
        ERROR_EXCEPTION
    }

    public interface LaunchCallback {
        void onResult(LaunchResult result);
    }

    private static int sLogLevel = Log.INFO;

    public static void setLogLevel(int level) {
        sLogLevel = level;
    }

    public static int getLogLevel() {
        return sLogLevel;
    }

    private Context mContext;
    private String mProcessName;
    private ProcessRole mProcessRole = ProcessRole.MAIN;
    private boolean mInitialized = false;

    private SandboxFileSystem mFileSystem;
    private AppMetadataCache mMetadataCache;
    private IconCache mIconCache;
    private GuestPackageManager mGuestPm;
    private ComponentResolver mComponentResolver;
    private PermissionMirror mPermissionMirror;
    private AgentProcessManager mAgentProcessManager;
    private StubCoordinator mStubCoordinator;

    public static VirtualEngine get() {
        return sInstance;
    }

    private VirtualEngine() {}

    public synchronized void init(Application application) {
        if (mInitialized) return;
        try {
            this.mContext = application;
            this.mProcessName = resolveProcessName(application);
            this.mProcessRole = detectProcessRole(mProcessName, application.getPackageName());
            this.mInitialized = true;

            Log.i(TAG, "VirtualEngine initialized for process: " + mProcessName + " (" + mProcessRole + ")");

            if (mProcessRole == ProcessRole.AGENT) {
                initAgentProcess(application);
            } else if (mProcessRole == ProcessRole.SERVER) {
                initServerProcess(application);
            }
        } catch (Throwable t) {
            Log.e(TAG, "VirtualEngine initialization encountered non-fatal warning; operating in degraded mode", t);
        }
    }

    private void initAgentProcess(Application app) {
        Log.i(TAG, "Configuring virtualization hooks in worker process...");

        try {
            // Inject Binder Proxies for AMS & PMS
            BinderProxyManager.injectAll(
                    app,
                    (proxy, method, args, original) -> {
                        String name = method.getName();
                        if ("startActivity".equals(name) && args != null) {
                            for (int i = 0; i < args.length; i++) {
                                if (args[i] instanceof Intent) {
                                    Intent intent = (Intent) args[i];
                                    if (intent.getComponent() != null && !intent.getComponent().getPackageName().equals(mContext.getPackageName())) {
                                        int agent = getAgentProcessManager().acquireAgent(intent.getComponent().getPackageName(), 0);
                                        ComponentName stub = getStubCoordinator().allocateActivityStub(
                                                mContext.getPackageName(), agent,
                                                intent.getComponent().getPackageName(), 0,
                                                intent.getComponent().getClassName()
                                        );
                                        args[i] = ParcelRewriter.wrapStubIntent(intent, stub, intent.getComponent().getPackageName(), 0);
                                        Log.d(TAG, "Intercepted outbound startActivity -> wrapped with stub " + stub);
                                    }
                                }
                            }
                        }
                        return method.invoke(original, args);
                    },
                    (proxy, method, args, original) -> {
                        String name = method.getName();
                        if ("checkPermission".equals(name) && args != null && args.length >= 1) {
                            PermissionMirror pm = getPermissionMirror();
                            return (pm != null) ? pm.checkPermission((String) args[0], null, 0) : 0;
                        }
                        return method.invoke(original, args);
                    }
            );
        } catch (Throwable t) {
            Log.w(TAG, "BinderProxyManager setup note: " + t.getMessage());
        }
    }

    private void initServerProcess(Application app) {
        Log.i(TAG, "Configuring server daemon registries...");
    }

    private ProcessRole detectProcessRole(String processName, String hostPackage) {
        if (processName == null || processName.equals(hostPackage)) {
            return ProcessRole.MAIN;
        }
        if (processName.endsWith(":service")) {
            return ProcessRole.SERVER;
        }
        if (processName.contains(":agent")) {
            return ProcessRole.AGENT;
        }
        return ProcessRole.MAIN;
    }

    private String resolveProcessName(Context context) {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                return Application.getProcessName();
            } catch (Throwable ignored) {}
        }
        return context != null ? context.getPackageName() : "com.example.dualworld";
    }

    public boolean installClone(String packageName, String sourceApkPath, int userId) {
        try {
            return ApkInstaller.installOrUpdate(mContext, getFileSystem(), sourceApkPath, packageName, userId);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install clone: " + packageName, t);
            return false;
        }
    }

    public void launchCloneAsync(Context ctx, String pkg, int userId, LaunchCallback cb) {
        new Thread(() -> {
            LaunchResult r = launchCloneInternal(ctx, pkg, userId);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (cb != null) cb.onResult(r);
            });
        }, "dw-launch-" + pkg).start();
    }

    public LaunchResult launchClone(Context context, String packageName, int userId) {
        return launchCloneInternal(context, packageName, userId);
    }

    public LaunchResult launchCloneInternal(Context context, String packageName, int userId) {
        try {
            AppMetadataCache cache = getMetadataCache();
            AppMetadataCache.AppEntry entry = (cache != null) ? cache.getApp(packageName, userId) : null;

            File baseApk = getFileSystem().getBaseApk(packageName);

            // Check disk if cache misses before reporting uninstalled
            if (entry == null) {
                if (baseApk.exists()) {
                    PackageManager pm = context.getPackageManager();
                    PackageInfo pInfo = pm.getPackageArchiveInfo(baseApk.getAbsolutePath(), PackageManager.GET_ACTIVITIES | PackageManager.GET_PERMISSIONS);
                    String appName = packageName;
                    String versionName = "1.0";
                    int versionCode = 1;
                    if (pInfo != null) {
                        if (pInfo.versionName != null) versionName = pInfo.versionName;
                        versionCode = pInfo.versionCode;
                        try {
                            ApplicationInfo ai = pInfo.applicationInfo;
                            ai.sourceDir = baseApk.getAbsolutePath();
                            ai.publicSourceDir = baseApk.getAbsolutePath();
                            appName = pm.getApplicationLabel(ai).toString();
                        } catch (Exception ignored) {}
                    }
                    entry = new AppMetadataCache.AppEntry();
                    entry.packageName = packageName;
                    entry.userId = userId;
                    entry.appName = appName;
                    entry.apkPath = baseApk.getAbsolutePath();
                    entry.apkHash = AppMetadataCache.calculateSha256(baseApk);
                    entry.versionName = versionName;
                    entry.versionCode = versionCode;
                    entry.installedAt = System.currentTimeMillis();
                    entry.lastLaunchAt = 0;
                    entry.launchCount = 0;
                    if (cache != null) {
                        cache.putApp(entry);
                    }
                } else {
                    Log.e(TAG, "Cannot launch uninstalled clone: " + packageName);
                    return LaunchResult.ERROR_NOT_INSTALLED;
                }
            }

            if (!baseApk.exists()) {
                Log.e(TAG, "Clone base.apk missing: " + baseApk.getAbsolutePath());
                return LaunchResult.ERROR_APK_MISSING;
            }

            String mainActivityClass = getComponentResolver().resolveMainActivityClassName(baseApk.getAbsolutePath());
            if (mainActivityClass == null) {
                Log.e(TAG, "No launcher activity discovered for " + packageName);
                return LaunchResult.ERROR_NO_LAUNCHER;
            }

            // Acquire worker process & stub
            int agentIndex = getAgentProcessManager().acquireAgent(packageName, userId);
            if (agentIndex < 0 || agentIndex >= AgentProcessManager.MAX_AGENTS) {
                Log.e(TAG, "No agent process available for " + packageName);
                return LaunchResult.ERROR_AGENT_UNAVAILABLE;
            }
            String agentProcess = AgentProcessManager.getProcessNameForAgent(context.getPackageName(), agentIndex);

            ComponentName stubComponent = getStubCoordinator().allocateActivityStub(
                    context.getPackageName(), agentIndex, packageName, userId, mainActivityClass
            );
            if (stubComponent == null) {
                Log.e(TAG, "Could not allocate stub for " + packageName);
                return LaunchResult.ERROR_AGENT_UNAVAILABLE;
            }

            // Construct guest launch intent
            Intent guestIntent = new Intent(Intent.ACTION_MAIN);
            guestIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            guestIntent.setComponent(new ComponentName(packageName, mainActivityClass));
            guestIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            File userSandboxDir = getFileSystem().getUserDataDir(packageName, userId);

            // Wrap inside stub intent with complete guest metadata
            Intent launchStubIntent = ParcelRewriter.wrapStubIntent(
                    guestIntent,
                    stubComponent,
                    packageName,
                    userId,
                    baseApk.getAbsolutePath(),
                    userSandboxDir.getAbsolutePath(),
                    mainActivityClass
            );
            launchStubIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Fire stub activity launch
            context.startActivity(launchStubIntent);
            if (cache != null) {
                cache.recordLaunch(packageName, userId);
            }

            Log.i(TAG, "Launched clone " + packageName + " (user " + userId + ") via " + stubComponent + " in " + agentProcess);
            return LaunchResult.SUCCESS;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to launch clone: " + packageName, t);
            return LaunchResult.ERROR_EXCEPTION;
        }
    }

    public boolean uninstallClone(String packageName, int userId) {
        try {
            getFileSystem().cleanUserSandbox(packageName, userId);
            AppMetadataCache cache = getMetadataCache();
            if (cache != null) cache.removeApp(packageName, userId);
            IconCache icons = getIconCache();
            if (icons != null) icons.evict(packageName, userId);
            getAgentProcessManager().releaseAgent(packageName, userId);
            IpcRouter.get().unregisterVm(packageName, userId);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Error uninstalling clone", t);
            return false;
        }
    }

    public boolean renameClone(String packageName, int userId, String newName) {
        try {
            AppMetadataCache cache = getMetadataCache();
            if (cache != null) cache.updateLabel(packageName, userId, newName);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Error renaming clone", t);
            return false;
        }
    }

    public List<AppMetadataCache.AppEntry> getAllClones() {
        try {
            AppMetadataCache cache = getMetadataCache();
            return (cache != null) ? cache.getAllApps() : Collections.emptyList();
        } catch (Throwable t) {
            Log.e(TAG, "Error fetching clones", t);
            return Collections.emptyList();
        }
    }

    public Context getContext() { return mContext; }
    public String getProcessName() { return mProcessName; }
    public ProcessRole getProcessRole() { return mProcessRole; }

    public synchronized SandboxFileSystem getFileSystem() {
        if (mFileSystem == null && mContext != null) {
            mFileSystem = new SandboxFileSystem(mContext);
        }
        return mFileSystem;
    }

    public synchronized AppMetadataCache getMetadataCache() {
        if (mMetadataCache == null && mContext != null) {
            mMetadataCache = AppMetadataCache.get(mContext);
        }
        return mMetadataCache;
    }

    public synchronized IconCache getIconCache() {
        if (mIconCache == null && mContext != null) {
            mIconCache = IconCache.get(mContext);
        }
        return mIconCache;
    }

    public synchronized GuestPackageManager getGuestPm() {
        if (mGuestPm == null && mContext != null) {
            mGuestPm = new GuestPackageManager(mContext);
        }
        return mGuestPm;
    }

    public synchronized ComponentResolver getComponentResolver() {
        if (mComponentResolver == null) {
            mComponentResolver = new ComponentResolver(getGuestPm());
        }
        return mComponentResolver;
    }

    public synchronized PermissionMirror getPermissionMirror() {
        if (mPermissionMirror == null && mContext != null) {
            mPermissionMirror = new PermissionMirror(mContext);
        }
        return mPermissionMirror;
    }

    public synchronized AgentProcessManager getAgentProcessManager() {
        if (mAgentProcessManager == null) {
            mAgentProcessManager = AgentProcessManager.get();
        }
        return mAgentProcessManager;
    }

    public synchronized StubCoordinator getStubCoordinator() {
        if (mStubCoordinator == null) {
            mStubCoordinator = StubCoordinator.get();
        }
        return mStubCoordinator;
    }
}
