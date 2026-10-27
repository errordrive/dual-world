# Dual World Virtualization Engine: Comprehensive Root-Cause Audit

This audit evaluates every component and class in the v1.x virtualization engine to identify why cloned applications do not execute inside an isolated virtual container.

---

## 1. Component Audit Matrix

| Class / Component | Primary Responsibilities | Methods Audited | Current Mechanism | Verdict |
| :--- | :--- | :--- | :--- | :--- |
| [`VirtualCore`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/core/VirtualCore.java) | Process role resolution, package registry, storage creation, launch dispatcher | `attachBaseContext`, `installPackage`, `uninstallPackage`, `launchApp` | - SharedPreferences metadata persistence<br>- Directory creation under `/virtual/users/`<br>- `launchApp` wraps target Intent in a Stub intent and invokes `context.startActivity()` | **Partial** |
| [`VirtualClient`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/client/VirtualClient.java) | Per-agent process coordinator, tracking active guest package & user ID | `attachBaseContext`, `setCurrentApp`, `resolveAgentIndex` | - Holds in-memory static variables for current package and user ID<br>- Calls `BinderHookManager` and `ActivityThreadHook` during `attachBaseContext` | **Partial** |
| [`BinderHookManager`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/client/BinderHookManager.java) | System service proxy injection (AMS, PMS) | `installHooks`, `hookActivityManager`, `hookPackageManager` | - Reflects into `ActivityManager.IActivityManagerSingleton` / `ActivityTaskManager`<br>- Reflects into `ActivityThread.sPackageManager`<br>- Wraps originals with `InvocationHandlerProxy` | **Partial** |
| [`ActivityThreadHook`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/client/ActivityThreadHook.java) | `ActivityThread.mH` handler message interception | `install`, `ActivityThreadCallback.handleMessage` | - Intercepts `mH` via `Handler.mCallback`<br>- Checks `msg.what == 100` (`LAUNCH_ACTIVITY`) or `msg.what == 159` (`EXECUTE_TRANSACTION`)<br>- **Fatal Flaw:** Only logs a debug line and forwards directly to `mOriginal.handleMessage(msg)` without mutating the transaction or swapping target tokens | **Stub** |
| [`ActivityManagerHook`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/hook/ActivityManagerHook.java) | Intercepts `startActivity` and `getCallingPackage` | `StartActivityHook.call`, `GetPackageNameHook.call` | - Replaces target Intent with a `StubActivity` intent when `startActivity` is invoked<br>- Returns guest package name for `getCallingPackage` | **Partial** |
| [`PackageManagerHook`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/hook/PackageManagerHook.java) | Spoofs `PackageInfo` and `ApplicationInfo` data directories | `GetPackageInfoHook.call`, `GetApplicationInfoHook.call` | - Overwrites `ApplicationInfo.dataDir` with sandboxed virtual storage path if queried<br>- Does not handle component resolution, intent filters, or APK signature queries | **Partial** |
| [`BaseHook`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/hook/BaseHook.java) & [`InvocationHandlerProxy`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/hook/InvocationHandlerProxy.java) | Dynamic Proxy dispatch foundation | `invoke`, `addHook` | - Standard Java reflection dynamic proxy delegating method calls to registered `BaseHook` instances | **Working** |
| [`StubActivity`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/stub/StubActivity.java) | Pre-declared manifest activity placeholder (`P0`..`P3`) | `onCreate` | - **Fatal Flaw:** In `onCreate`, extracts `_v_target_intent_` and calls `startActivity(targetIntent)` followed immediately by `finish()`. This launches the external host-installed application or fails with `SecurityException`, completely escaping the agent sandbox! | **Broken** |
| [`StubService`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/stub/StubService.java) | Pre-declared manifest service placeholder (`P0`..`P3`) | `onBind`, `onStartCommand` | - Empty skeleton returning `null` / `START_NOT_STICKY`. Does not route or instantiate guest services | **Stub** |
| [`StubContentProvider`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/stub/StubContentProvider.java) | Pre-declared manifest provider placeholder (`P0`..`P3`) | `query`, `insert`, etc. | - Empty skeleton returning `null`. Does not proxy guest provider URIs or authorities | **Stub** |
| [`VActivityManagerService`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/server/VActivityManagerService.java) | Server daemon for process & activity lifecycle | `resolveStubActivity` | - Returns a stub component name based on package hash; does not track active tasks, process allocations, or tokens | **Stub** |
| [`VPackageManagerService`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/server/VPackageManagerService.java) | Server daemon for package parser & component registry | `init`, `getInstalledPackages` | - Simple in-memory list returning host installed packages; does not parse guest APKs or component manifests | **Stub** |
| [`VirtualServerProvider`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/server/VirtualServerProvider.java) | Central IPC bridge between agent processes and `:service` daemon | `call` | - Returns an empty `Bundle`. No actual IPC routing implemented | **Stub** |
| [`VirtualStorage`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/core/VirtualStorage.java) | Filesystem directory hierarchy creation | `getDataDir`, `getExternalDataDir` | - Correctly generates directory paths `/data/data/.../virtual/users/<userId>/<pkg>` and creates them on disk | **Working** |

---

## 2. Root Cause Analysis

Why does the cloned app never truly run isolated?

1. **Stub Activity Launch Escape:**  
   In [`StubActivity.java#L35-L42`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/stub/StubActivity.java#L35-L42), the code executed:
   ```java
   targetIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
   startActivity(targetIntent);
   finish();
   ```
   Instead of intercepting the launch to instantiate the guest activity inside the stub's host process, it invoked Android's system `startActivity()` with the target's external intent. This caused the operating system to launch the original installed package in its own external UID, or crash if the target was unexported.

2. **No ClassLoader Injection:**  
   The guest APK's classes and DEX files were never added to the host's ClassLoader hierarchy. In an Android app sandbox, a custom `DelegateClassLoader` or `DexClassLoader` must be spliced into `LoadedApk.mClassLoader` or `ActivityThread` so that when `ActivityThread` attempts to instantiate guest classes, the Dalvik/ART runtime resolves them from the cloned APK container.

3. **Incomplete `ActivityThread.mH` Interception:**  
   [`ActivityThreadHook.java#L45-L51`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/client/ActivityThreadHook.java#L45-L51) only logged messages `what == 100` (`LAUNCH_ACTIVITY`) and `what == 159` (`EXECUTE_TRANSACTION`). It did not modify the `ActivityClientRecord` or unpack the `ClientTransaction` `LaunchActivityItem` to replace the stub intent with the real guest intent before the framework's `handleLaunchActivity` proceeded.

4. **Absence of Guest `Application` & Context Binding (`BIND_APPLICATION`):**  
   Guest apps require their own `Application` instance initialized with an isolated `ContextImpl`, merged `AssetManager` (resources), and redirected directories. In the v1.x engine, message `110` (`BIND_APPLICATION`) was completely unhandled, leaving the agent process running under the host's application context without guest assets or theme.

5. **No Instrumentation Proxying:**  
   There was no `InstrumentationProxy` wrapping `ActivityThread.mInstrumentation`. Without hooking `newActivity` and `callActivityOnCreate`, the framework instantiated whatever component Android OS knew about, without custom class resolution, resource injection, or lifecycle tracking.

6. **Missing IPC Service Broker:**  
   The `:service` daemon and `:agent*` processes communicated through an empty `VirtualServerProvider.call()` method. No shared Binder interfaces or token ledgers existed to coordinate agent assignment, stub multiplexing, or process recycling.

---

## 3. Rebuild Mandate

To attain production-grade virtualization, the engine must be decoupled into an independent `engine/` module with:
- **`engine.core`**: `VirtualEngine`, `VirtualMachine`, `VirtualUser`
- **`engine.ipc`**: `BinderProxyManager`, `IpcRouter`, `ParcelRewriter`
- **`engine.hook`**: `ActivityThreadInterceptor`, `ClassLoaderInjector`, `InstrumentationProxy`
- **`engine.pm`**: `GuestPackageManager`, `ComponentResolver`, `PermissionMirror`
- **`engine.fs`**: `SandboxFileSystem`, `ApkInstaller`
- **`engine.process`**: `AgentProcessManager`, `StubCoordinator`
- **`engine.cache`**: `AppMetadataCache`, `IconCache`, `DexOptCache`
- **Component Stub Matrix**: 16 Stub Activities (4 per agent), 4 Stub Services, 2 Stub Providers, 1 Stub Receiver.
