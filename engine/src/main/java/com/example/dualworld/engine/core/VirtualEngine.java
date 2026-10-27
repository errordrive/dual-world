package com.example.dualworld.engine.core;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
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
import java.util.List;

/**
 * Central engine orchestrator uniting core virtualization, isolated file systems,
 * Binder IPC interception, component proxying, and caching.
 */
public class VirtualEngine {
    private static final String TAG = "VirtualEngine";
    private static final VirtualEngine sInstance = new VirtualEngine();

    public enum ProcessRole {
        MAIN,
        SERVER,
        AGENT
    }

    private Context mContext;
    private String mProcessName;
    private ProcessRole mProcessRole = ProcessRole.MAIN;

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

    public void init(Application application) {
        this.mContext = application;
        this.mProcessName = resolveProcessName(application);
        this.mProcessRole = detectProcessRole(mProcessName, application.getPackageName());

        this.mFileSystem = new SandboxFileSystem(application);
        this.mMetadataCache = AppMetadataCache.get(application);
        this.mIconCache = IconCache.get(application);
        this.mGuestPm = new GuestPackageManager(application);
        this.mComponentResolver = new ComponentResolver(mGuestPm);
        this.mPermissionMirror = new PermissionMirror(application);
        this.mAgentProcessManager = AgentProcessManager.get();
        this.mStubCoordinator = StubCoordinator.get();

        Log.i(TAG, "VirtualEngine initialized for process: " + mProcessName + " (" + mProcessRole + ")");

        if (mProcessRole == ProcessRole.AGENT) {
            initAgentProcess(application);
        } else if (mProcessRole == ProcessRole.SERVER) {
            initServerProcess(application);
        }
    }

    private void initAgentProcess(Application app) {
        Log.i(TAG, "Configuring virtualization hooks in worker process...");

        // 1. Install ActivityThread interceptor
        ActivityThreadInterceptor.install(app);

        // 2. Install Instrumentation proxy
        InstrumentationProxy.install(app);

        // 3. Inject Binder Proxies for AMS & PMS
        BinderProxyManager.injectAll(
                app,
                (proxy, method, args, original) -> {
                    String name = method.getName();
                    if ("startActivity".equals(name)) {
                        for (int i = 0; i < args.length; i++) {
                            if (args[i] instanceof Intent) {
                                Intent intent = (Intent) args[i];
                                if (intent.getComponent() != null && !intent.getComponent().getPackageName().equals(mContext.getPackageName())) {
                                    int agent = mAgentProcessManager.acquireAgent(intent.getComponent().getPackageName(), 0);
                                    ComponentName stub = mStubCoordinator.allocateActivityStub(
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
                        return mPermissionMirror.checkPermission((String) args[0], null, 0);
                    }
                    return method.invoke(original, args);
                }
        );
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
            return Application.getProcessName();
        }
        return context.getPackageName();
    }

    public boolean installClone(String packageName, String sourceApkPath, int userId) {
        return ApkInstaller.installOrUpdate(mContext, mFileSystem, sourceApkPath, packageName, userId);
    }

    public boolean launchClone(Context context, String packageName, int userId) {
        try {
            AppMetadataCache.AppEntry entry = mMetadataCache.getApp(packageName, userId);
            if (entry == null) {
                Log.e(TAG, "Cannot launch uninstalled clone: " + packageName);
                return false;
            }

            File baseApk = mFileSystem.getBaseApk(packageName);
            if (!baseApk.exists()) {
                Log.e(TAG, "Clone base.apk missing: " + baseApk.getAbsolutePath());
                return false;
            }

            String mainActivityClass = mComponentResolver.resolveMainActivityClassName(baseApk.getAbsolutePath());
            if (mainActivityClass == null) {
                Log.e(TAG, "No launcher activity discovered for " + packageName);
                return false;
            }

            // Acquire worker process & stub
            int agentIndex = mAgentProcessManager.acquireAgent(packageName, userId);
            String agentProcess = AgentProcessManager.getProcessNameForAgent(context.getPackageName(), agentIndex);

            ComponentName stubComponent = mStubCoordinator.allocateActivityStub(
                    context.getPackageName(), agentIndex, packageName, userId, mainActivityClass
            );

            // Construct guest launch intent
            Intent guestIntent = new Intent(Intent.ACTION_MAIN);
            guestIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            guestIntent.setComponent(new ComponentName(packageName, mainActivityClass));
            guestIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Wrap inside stub intent
            Intent launchStubIntent = ParcelRewriter.wrapStubIntent(guestIntent, stubComponent, packageName, userId);
            launchStubIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Setup VM in router
            VirtualMachine vm = new VirtualMachine(packageName, userId, agentIndex, agentProcess);
            File userSandboxDir = mFileSystem.getUserDataDir(packageName, userId);
            File oatDir = mFileSystem.getOatDir(packageName);
            File libDir = mFileSystem.getNativeLibDir(packageName, Build.SUPPORTED_ABIS[0]);

            ClassLoaderInjector.DelegateClassLoader delegateLoader = ClassLoaderInjector.createDelegate(
                    context, baseApk, oatDir, libDir, packageName
            );
            vm.create(baseApk, userSandboxDir, delegateLoader);
            IpcRouter.get().registerVm(vm);

            // Fire stub activity launch
            context.startActivity(launchStubIntent);
            mMetadataCache.recordLaunch(packageName, userId);

            Log.i(TAG, "Launched clone " + packageName + " (user " + userId + ") via " + stubComponent + " in " + agentProcess);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch clone: " + packageName, e);
            return false;
        }
    }

    public boolean uninstallClone(String packageName, int userId) {
        mFileSystem.cleanUserSandbox(packageName, userId);
        mMetadataCache.removeApp(packageName, userId);
        mIconCache.evict(packageName, userId);
        mAgentProcessManager.releaseAgent(packageName, userId);
        IpcRouter.get().unregisterVm(packageName, userId);
        return true;
    }

    public boolean renameClone(String packageName, int userId, String newName) {
        mMetadataCache.updateLabel(packageName, userId, newName);
        return true;
    }

    public List<AppMetadataCache.AppEntry> getAllClones() {
        return mMetadataCache.getAllApps();
    }

    public Context getContext() { return mContext; }
    public String getProcessName() { return mProcessName; }
    public ProcessRole getProcessRole() { return mProcessRole; }
    public SandboxFileSystem getFileSystem() { return mFileSystem; }
    public AppMetadataCache getMetadataCache() { return mMetadataCache; }
    public IconCache getIconCache() { return mIconCache; }
    public GuestPackageManager getGuestPm() { return mGuestPm; }
    public ComponentResolver getComponentResolver() { return mComponentResolver; }
    public AgentProcessManager getAgentProcessManager() { return mAgentProcessManager; }
    public StubCoordinator getStubCoordinator() { return mStubCoordinator; }
}
