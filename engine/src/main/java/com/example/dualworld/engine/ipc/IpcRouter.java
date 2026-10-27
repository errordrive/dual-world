package com.example.dualworld.engine.ipc;

import android.util.Log;

import com.example.dualworld.engine.core.VirtualMachine;

import java.util.HashMap;
import java.util.Map;

/**
 * Dispatches intercepted IPC calls and binder transactions to the corresponding
 * active VirtualMachine instance.
 */
public class IpcRouter {
    private static final String TAG = "IpcRouter";
    private static final IpcRouter sInstance = new IpcRouter();

    // "packageName_u<userId>" -> VirtualMachine
    private final Map<String, VirtualMachine> mActiveVms = new HashMap<>();

    public static IpcRouter get() {
        return sInstance;
    }

    private IpcRouter() {}

    public synchronized void registerVm(VirtualMachine vm) {
        String key = makeKey(vm.packageName, vm.userId);
        mActiveVms.put(key, vm);
        Log.i(TAG, "Registered active VM in router: " + key);
    }

    public synchronized void unregisterVm(String packageName, int userId) {
        String key = makeKey(packageName, userId);
        mActiveVms.remove(key);
        Log.i(TAG, "Unregistered VM from router: " + key);
    }

    public synchronized VirtualMachine route(String packageName, int userId) {
        String key = makeKey(packageName, userId);
        return mActiveVms.get(key);
    }

    public synchronized VirtualMachine findActiveVmForProcess(String processName) {
        for (VirtualMachine vm : mActiveVms.values()) {
            if (vm.processName.equals(processName) && vm.isRunning()) {
                return vm;
            }
        }
        return null;
    }

    private String makeKey(String packageName, int userId) {
        return packageName + "_u" + userId;
    }
}
