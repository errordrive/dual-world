# REA Feature Tracing: Dual World

This document traces three key features from UI interaction through the internal virtualization subsystems to filesystem and IPC effects, citing exact files and method signatures.

---

## Feature 1: Clone Installed Application ("Clone App")

### 1. UI Trigger Event
- **Location:** [`app/src/main/java/com/example/dualworld/ui/AppListActivity.java`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/ui/AppListActivity.java)
- **User Action:** User taps the **"Clone"** button (`btnClone`) on a discovered app item in `AvailableAppAdapter`.
- **Callback Trigger:**
  ```java
  mAdapter = new AvailableAppAdapter(app -> {
      int nextUserId = 0;
      for (VirtualAppModel model : VirtualCore.get().getClonedApps()) {
          if (model.packageName.equals(app.packageName)) {
              nextUserId = Math.max(nextUserId, model.userId + 1);
          }
      }
      boolean success = VirtualCore.get().installPackage(app.packageName, nextUserId);
      ...
  });
  ```

### 2. Core Code Execution Path
1. **Method:** `VirtualCore.installPackage(String packageName, int userId)`  
   *File:* [`app/src/main/java/com/example/dualworld/core/VirtualCore.java`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/core/VirtualCore.java)
   - Queries host `PackageManager` to retrieve the original `ApplicationInfo` and app label.
   - Instantiates a `VirtualAppModel` recording `packageName`, `appName`, `userId`, and `apkPath`.
   - Adds the model to the synchronized `mClonedApps` in-memory collection.
2. **Method:** `VirtualCore.saveClonedAppsToStorage()`  
   *File:* [`app/src/main/java/com/example/dualworld/core/VirtualCore.java`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/core/VirtualCore.java)
   - Serializes the updated clone list into a JSON array.
   - Commits to `SharedPreferences` ("`virtual_apps`", key "`installed_list`").

### 3. Storage & IPC Effects
- **Sandbox Directory Allocation:**  
  `VirtualStorage.getDataDir(packageName, userId)` creates:
  ```
  /data/data/com.example.dualworld/virtual/user/<userId>/<packageName>/
  ├── files/
  ├── databases/
  ├── shared_prefs/
  └── cache/
  ```
- **External Sandbox Allocation:**  
  `VirtualStorage.getExternalDataDir(packageName, userId)` creates:
  ```
  /sdcard/Android/data/com.example.dualworld/files/virtual/user/<userId>/<packageName>/
  ```

---

## Feature 2: Launch Cloned Instance ("Launch Clone")

### 1. UI Trigger Event
- **Location:** [`app/src/main/java/com/example/dualworld/ui/MainActivity.java`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/ui/MainActivity.java)
- **User Action:** User taps the **"Launch"** button (`btnLaunch`) on a cloned app card in `ClonedAppAdapter`.
- **Callback Trigger:**
  ```java
  @Override
  public void onLaunchClick(VirtualAppModel app) {
      boolean launched = VirtualCore.get().launchApp(MainActivity.this, app.packageName, app.userId);
  }
  ```

### 2. Core Code Execution Path
1. **Method:** `VirtualCore.launchApp(Context context, String packageName, int userId)`  
   *File:* [`app/src/main/java/com/example/dualworld/core/VirtualCore.java`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/core/VirtualCore.java)
   - Retrieves the target package's launch intent: `pm.getLaunchIntentForPackage(packageName)`.
   - Computes target process allocation slot:
     ```java
     int agentIndex = Math.abs(packageName.hashCode() + userId) % 4;
     String stubClassName = "com.example.dualworld.stub.StubActivity$P" + agentIndex;
     ```
   - Wraps the real intent into `stubIntent` with extras:
     - `_v_target_intent_`: Original guest `Intent`
     - `_v_pkg_`: Target package name
     - `_v_user_id_`: Allocated virtual user ID
   - Invokes `context.startActivity(stubIntent)`.
2. **Method:** `StubActivity.onCreate(Bundle savedInstanceState)`  
   *File:* [`app/src/main/java/com/example/dualworld/stub/StubActivity.java`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/stub/StubActivity.java)
   - Lifecycle runs inside the assigned isolated process (`:agent0`, `:agent1`, `:agent2`, or `:agent3`).
   - Reads `_v_target_intent_`, sets active app via `VirtualClient.setCurrentApp(targetPkg, userId)`.
   - Forwards execution: `startActivity(targetIntent)`.

### 3. IPC & System Hook Effects
- **ActivityManager Hook:**  
  [`app/src/main/java/com/example/dualworld/hook/ActivityManagerHook.java`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/hook/ActivityManagerHook.java)
  Intercepts nested `startActivity` calls inside the agent process, rewriting them to keep them contained within the stub process domain.
- **PackageManager Hook:**  
  [`app/src/main/java/com/example/dualworld/hook/PackageManagerHook.java`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/hook/PackageManagerHook.java)
  Intercepts `getPackageInfo` and `getApplicationInfo`, pointing `ApplicationInfo.dataDir` to the per-user sandboxed storage directory.

---

## Feature 3: Remove Cloned Application ("Remove Clone")

### 1. UI Trigger Event
- **Location:** [`app/src/main/java/com/example/dualworld/ui/MainActivity.java`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/ui/MainActivity.java)
- **User Action:** User taps the **"Delete"** image button (`btnDelete`) on a cloned app card.
- **Callback Trigger:**
  ```java
  @Override
  public void onDeleteClick(VirtualAppModel app) {
      VirtualCore.get().uninstallPackage(app.packageName, app.userId);
      Toast.makeText(MainActivity.this, R.string.clone_removed, Toast.LENGTH_SHORT).show();
      refreshClonedList();
  }
  ```

### 2. Core Code Execution Path
1. **Method:** `VirtualCore.uninstallPackage(String packageName, int userId)`  
   *File:* [`app/src/main/java/com/example/dualworld/core/VirtualCore.java`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/core/VirtualCore.java)
   - Locates matching `VirtualAppModel` matching `(packageName, userId)`.
   - Removes the model from `mClonedApps`.
   - Invokes `saveClonedAppsToStorage()` to write the updated JSON to `SharedPreferences`.
2. **Method:** `VirtualCore.deleteRecursive(File fileOrDirectory)`  
   *File:* [`app/src/main/java/com/example/dualworld/core/VirtualCore.java`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/core/VirtualCore.java)
   - Recursively traverses and purges all child files, databases, shared_prefs, and sub-folders within the isolated directory.

### 3. Storage & State Effects
- The physical directory `/data/data/com.example.dualworld/virtual/user/<userId>/<packageName>/` is completely removed from disk.
- Other accounts of the same app (e.g. `user/0/com.whatsapp` vs `user/1/com.whatsapp`) remain intact and unaffected.
- The UI list automatically refreshes via `refreshClonedList()`.
