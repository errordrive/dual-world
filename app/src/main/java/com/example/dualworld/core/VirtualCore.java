package com.example.dualworld.core;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.example.dualworld.client.VirtualClient;
import com.example.dualworld.server.VActivityManagerService;
import com.example.dualworld.server.VPackageManagerService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central engine orchestrator for Dual World.
 */
public class VirtualCore {
    private static final String TAG = "VirtualCore";
    private static final VirtualCore sInstance = new VirtualCore();

    public enum ProcessType {
        MAIN,
        SERVER,
        AGENT
    }

    private Context mContext;
    private String mProcessName;
    private ProcessType mProcessType = ProcessType.MAIN;
    private VirtualStorage mStorage;
    private final List<VirtualAppModel> mClonedApps = new ArrayList<>();

    public static VirtualCore get() {
        return sInstance;
    }

    private VirtualCore() {}

    public void attachBaseContext(Application app) {
        mContext = app;
        mProcessName = resolveProcessName(app);
        mProcessType = determineProcessType(mProcessName);
        mStorage = new VirtualStorage(app);

        Log.i(TAG, "Initialized process: " + mProcessName + " (" + mProcessType + ")");

        if (mProcessType == ProcessType.AGENT) {
            VirtualClient.get().attachBaseContext(app);
        }
    }

    public void initialize(Application app) {
        if (mProcessType == ProcessType.MAIN) {
            loadClonedAppsFromStorage();
        } else if (mProcessType == ProcessType.SERVER) {
            VPackageManagerService.get().init(app);
            VActivityManagerService.get().init(app);
        } else if (mProcessType == ProcessType.AGENT) {
            VirtualClient.get().init(app);
        }
    }

    private ProcessType determineProcessType(String processName) {
        if (processName == null || processName.equals(mContext.getPackageName())) {
            return ProcessType.MAIN;
        }
        if (processName.endsWith(":service")) {
            return ProcessType.SERVER;
        }
        if (processName.contains(":agent")) {
            return ProcessType.AGENT;
        }
        return ProcessType.MAIN;
    }

    private String resolveProcessName(Context context) {
        if (Build.VERSION.SDK_INT >= 28) {
            return Application.getProcessName();
        }
        return context.getPackageName();
    }

    public Context getContext() {
        return mContext;
    }

    public VirtualStorage getStorage() {
        return mStorage;
    }

    public ProcessType getProcessType() {
        return mProcessType;
    }

    public String getProcessName() {
        return mProcessName;
    }

    public synchronized List<VirtualAppModel> getClonedApps() {
        return Collections.unmodifiableList(new ArrayList<>(mClonedApps));
    }

    public synchronized boolean installPackage(String packageName, int userId) {
        try {
            PackageManager pm = mContext.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(appInfo);

            VirtualAppModel model = new VirtualAppModel(packageName, label.toString(), userId, appInfo.sourceDir);
            model.icon = pm.getApplicationIcon(appInfo);

            for (VirtualAppModel existing : mClonedApps) {
                if (existing.packageName.equals(packageName) && existing.userId == userId) {
                    return true;
                }
            }

            mClonedApps.add(model);
            saveClonedAppsToStorage();

            mStorage.getDataDir(packageName, userId);
            mStorage.getExternalDataDir(packageName, userId);

            // Forward to new VirtualEngine
            com.example.dualworld.engine.core.VirtualEngine.get().installClone(packageName, appInfo.sourceDir, userId);

            Log.i(TAG, "Cloned " + packageName + " for User #" + userId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to clone app: " + packageName, e);
            return false;
        }
    }

    public synchronized boolean uninstallPackage(String packageName, int userId) {
        VirtualAppModel target = null;
        for (VirtualAppModel model : mClonedApps) {
            if (model.packageName.equals(packageName) && model.userId == userId) {
                target = model;
                break;
            }
        }
        if (target != null) {
            mClonedApps.remove(target);
            saveClonedAppsToStorage();
            File dataDir = mStorage.getDataDir(packageName, userId);
            deleteRecursive(dataDir);
            com.example.dualworld.engine.core.VirtualEngine.get().uninstallClone(packageName, userId);
            return true;
        }
        return false;
    }

    public synchronized boolean renameClone(String packageName, int userId, String newName) {
        for (VirtualAppModel model : mClonedApps) {
            if (model.packageName.equals(packageName) && model.userId == userId) {
                model.appName = newName;
                saveClonedAppsToStorage();
                return true;
            }
        }
        return false;
    }

    public boolean launchApp(Context context, String packageName, int userId) {
        boolean launched = com.example.dualworld.engine.core.VirtualEngine.get().launchClone(context, packageName, userId) == com.example.dualworld.engine.core.VirtualEngine.LaunchResult.SUCCESS;
        if (launched) {
            return true;
        }
        try {
            PackageManager pm = context.getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
            if (launchIntent == null) {
                Log.e(TAG, "No launch intent found for " + packageName);
                return false;
            }

            int agentIndex = Math.abs(packageName.hashCode() + userId) % 4;
            String stubClassName = "com.example.dualworld.stub.StubActivity$P" + agentIndex;

            Intent stubIntent = new Intent();
            stubIntent.setComponent(new ComponentName(context, stubClassName));
            stubIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            stubIntent.putExtra("_v_target_intent_", launchIntent);
            stubIntent.putExtra("_v_pkg_", packageName);
            stubIntent.putExtra("_v_user_id_", userId);

            context.startActivity(stubIntent);
            Log.i(TAG, "Launched " + packageName + " (user " + userId + ") via " + stubClassName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Launch error", e);
            return false;
        }
    }

    private void saveClonedAppsToStorage() {
        try {
            JSONArray array = new JSONArray();
            for (VirtualAppModel model : mClonedApps) {
                JSONObject obj = new JSONObject();
                obj.put("pkg", model.packageName);
                obj.put("name", model.appName);
                obj.put("userId", model.userId);
                obj.put("apk", model.apkPath);
                array.put(obj);
            }
            SharedPreferences sp = mContext.getSharedPreferences("virtual_apps", Context.MODE_PRIVATE);
            sp.edit().putString("installed_list", array.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save apps", e);
        }
    }

    private void loadClonedAppsFromStorage() {
        mClonedApps.clear();
        try {
            SharedPreferences sp = mContext.getSharedPreferences("virtual_apps", Context.MODE_PRIVATE);
            String json = sp.getString("installed_list", null);
            if (json != null) {
                PackageManager pm = mContext.getPackageManager();
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String pkg = obj.getString("pkg");
                    String name = obj.getString("name");
                    int userId = obj.getInt("userId");
                    String apk = obj.getString("apk");
                    VirtualAppModel model = new VirtualAppModel(pkg, name, userId, apk);
                    try {
                        model.icon = pm.getApplicationIcon(pkg);
                    } catch (Exception ignored) {}
                    mClonedApps.add(model);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load apps", e);
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }
}
