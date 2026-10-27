# Dual World Engine Status & Platform Compatibility Matrix

This document details the operational capabilities, architectural guarantees, and platform boundaries of the **Dual World v2.0** virtualization engine (`com.example.dualworld.engine`).

---

## 1. Engine Subsystem Status

| Subsystem | Components | Operational Status | Verification Mechanism |
| :--- | :--- | :--- | :--- |
| **Process Topology** | `:service`, `:agent0`..`:agent3` | **Working** | Manifest process attributes, separate Linux process PIDs |
| **Filesystem Sandbox** | `SandboxFileSystem`, `ApkInstaller` | **Working** | Path redirection `/data/data/.../virtual/users/<uid>/<pkg>`, isolated private data trees |
| **DEX / Class Loading** | `ClassLoaderInjector`, `DelegateClassLoader` | **Working** | Custom `DexClassLoader` hierarchy prioritizing guest bytecode and private native libraries |
| **Binder IPC Interception** | `BinderProxyManager`, `IpcRouter` | **Working** | `ServiceManager.sCache` and `IActivityManager` / `IPackageManager` Dynamic Proxies |
| **Activity Interception** | `ActivityThreadInterceptor`, `InstrumentationProxy` | **Working** | `mH.mCallback` message unwrapping (`100`, `110`, `159`) and `newActivity` class instantiation |
| **Component Resolution** | `GuestPackageManager`, `ComponentResolver` | **Working** | Non-root APK archive inspection, launcher intent matching |
| **Metadata Caching** | `AppMetadataCache` | **Working** | SQLite-backed manifest, hash-based invalidation, and launch tracking |
| **Icon Decoding Cache** | `IconCache` | **Working** | 50 MB Disk LRU + Memory cache with asynchronous decoding |
| **Stub Matrix** | 16 Activities, 4 Services, 2 Providers, 1 Receiver | **Working** | Pre-declared in `AndroidManifest.xml` with zero exported exposure |

---

## 2. Platform & Security Boundaries

### 1. Hidden API Restrictions (Android 9.0+)
Beginning in Android 9 (API 28), Android introduced restrictions on accessing private framework fields (e.g. `ActivityThread.mH`, `mPackages`, `IActivityManagerSingleton`).
- **Engine Handling:** Dual World employs standard Java reflection with graceful fallbacks (`ActivityManager` $\rightarrow$ `ActivityTaskManager` $\rightarrow$ `ServiceManager.sCache`).
- **Status:** On debuggable builds and standard user-space Android 7.0–14, field access succeeds with non-fatal runtime warnings in Logcat.

### 2. SELinux Security Context
Every worker agent (`:agent0`..`:agent3`) runs within the host application's Linux UID and SELinux domain (`u:r:untrusted_app:s0:...`).
- **Isolation Guarantee:** Cloned applications have full read/write isolation inside their designated virtual storage path (`/virtual/users/<userId>/<pkg>`), but cannot access other third-party application directories on the device.
- **SELinux Boundary:** A guest application attempting to run low-level root commands or modify `/system` or `/dev` nodes will be blocked by standard Android SELinux kernel policies.

### 3. Google Play Services & Push Notifications
Commercial Google Mobile Services (GMS) rely on signature verification with `com.android.vending`.
- **Limitation:** Apps strictly requiring Google SafetyNet / Play Integrity or hardware-backed keystore tokens require microG emulation or host-forwarding proxies.

---

## 3. Android Version Compatibility Matrix

| Android Version | API Level | Activity Interception Path | Storage Redirection | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Android 7.0 – 7.1** (Nougat) | 24 – 25 | `mH` message `100` (`LAUNCH_ACTIVITY`) | `/data/data/.../virtual/users/` | **Fully Supported** |
| **Android 8.0 – 8.1** (Oreo) | 26 – 27 | `IActivityManagerSingleton` + message `100` | Sandboxed virtual storage | **Fully Supported** |
| **Android 9.0** (Pie) | 28 | `ClientTransaction` / `LaunchActivityItem` | Scoped storage compatible | **Fully Supported** |
| **Android 10** (Q) | 29 | `ActivityTaskManager` proxy | Legacy external storage opt-in | **Fully Supported** |
| **Android 11 – 12** (R / S) | 30 – 31 | `Instrumentation.newActivity` proxy | Scoped internal storage | **Fully Supported** |
| **Android 13 – 14** (T / U) | 33 – 34 | ClientTransaction + `DelegateClassLoader` | App-specific internal sandbox | **Fully Supported** |
