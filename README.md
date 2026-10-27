# CloneApp — Android Dual Space & Virtual Container

A modular, high-performance Android App Virtualization & Cloning Container engineered from the reverse engineering analysis of **Dual Space** (`Dual Space_1.205-248.0.10.apk`).

---

## 🏗️ Architecture & Mechanism Breakdown

This project models the core virtualization mechanisms of Dual Space:

```
                          ┌───────────────────────────┐
                          │   Main UI Process         │
                          │   (com.apklab.cloneapp)   │
                          └─────────────┬─────────────┘
                                        │
                         ContentResolver / Binder IPC
                                        │
                          ┌─────────────▼─────────────┐
                          │  Daemon Server Process    │
                          │  (:service)               │
                          │  - VActivityManagerService│
                          │  - VPackageManagerService │
                          │  - VirtualServerProvider  │
                          └─────────────┬─────────────┘
                                        │
                         Allocated Stub Routing Intent
                                        │
          ┌─────────────────────────────┼─────────────────────────────┐
          │                             │                             │
┌─────────▼─────────┐         ┌─────────▼─────────┐         ┌─────────▼─────────┐
│  Agent 0 Process  │         │  Agent 1 Process  │         │  Agent 2 Process  │
│  (:agent0)        │         │  (:agent1)        │         │  (:agent2)        │
│  - StubActivity.P0│         │  - StubActivity.P1│         │  - StubActivity.P2│
│  - StubService.P0 │         │  - StubService.P1 │         │  - StubService.P2 │
│  - Binder Hook    │         │  - Binder Hook    │         │  - Binder Hook    │
│  - Cloned App #1  │         │  - Cloned App #2  │         │  - Cloned App #3  │
└───────────────────┘         └───────────────────┘         └───────────────────┘
```

### 1. Multi-Process Topology & Component Stubbing
- **Host Container (`com.apklab.cloneapp`)**: Serves the user dashboard, settings, and installed app scanner.
- **Daemon Server (`:service`)**: Runs `VirtualServerProvider`, `VActivityManagerService`, and `VPackageManagerService`. Keeps track of virtual package manifests, permissions, and active process allocations.
- **Sandbox Agents (`:agent0` .. `:agent3`)**: Sandboxed multi-tenant processes hosting cloned apps. Each process declares a suite of proxy components (`StubActivity$P0`, `StubService$P0`, `StubContentProvider$P0`).

### 2. Binder Transaction Interception (`BinderHookManager`)
- System service proxies (`android.app.IActivityManager`, `android.app.IActivityTaskManager`, `android.content.pm.IPackageManager`) are intercepted using Dynamic Proxies and reflection over `ActivityManager.IActivityManagerSingleton` and `ActivityThread.sPackageManager`.
- System calls like `startActivity` are intercepted to rewrite destination intents to point to declared stub activities in the host manifest.

### 3. Activity Lifecycle Interception (`ActivityThreadHook`)
- Hooks into `ActivityThread.mH` callback chain.
- Intercepts `LAUNCH_ACTIVITY` (code 100) and `EXECUTE_TRANSACTION` (code 159).
- Replaces stub activity tokens with the real target activity class and restores the target cloned application environment.

### 4. Sandboxed Filesystem Redirection (`VirtualStorage`)
- Cloned apps receive isolated storage directories:
  - Internal: `/data/data/com.apklab.cloneapp/virtual/user/<userId>/<packageName>/`
  - External: `/sdcard/Android/data/com.apklab.cloneapp/files/virtual/user/<userId>/<packageName>/`
- Prevents cross-contamination between different accounts of the same cloned app (e.g., dual WhatsApp / Telegram accounts).

---

## 🛠️ Project Structure

```
CloneApp/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml          # Declares :service and :agent0..:agent3 stubs
│       ├── java/com/apklab/cloneapp/
│       │   ├── CloneApplication.java   # Base application lifecycle entry point
│       │   ├── core/
│       │   │   ├── VirtualCore.java     # Engine orchestrator & process type resolver
│       │   │   ├── VirtualStorage.java  # Filesystem path redirection engine
│       │   │   ├── VirtualAppModel.java # Cloned app metadata
│       │   │   └── VirtualUser.java     # Virtual user definition
│       │   ├── server/
│       │   │   ├── VirtualServerProvider.java # IPC gateway for :service
│       │   │   ├── VActivityManagerService.java
│       │   │   └── VPackageManagerService.java
│       │   ├── client/
│       │   │   ├── VirtualClient.java   # Agent process controller
│       │   │   ├── BinderHookManager.java # System service Binder hook installer
│       │   │   └── ActivityThreadHook.java # ActivityThread handler instrumentation
│       │   ├── hook/
│       │   │   ├── BaseHook.java
│       │   │   ├── InvocationHandlerProxy.java
│       │   │   ├── ActivityManagerHook.java
│       │   │   └── PackageManagerHook.java
│       │   ├── stub/
│       │   │   ├── StubActivity.java    # P0..P3 activity multiplexers
│       │   │   ├── StubService.java     # P0..P3 service multiplexers
│       │   │   └── StubContentProvider.java
│       │   └── ui/
│       │       ├── MainActivity.java    # Cloned app dashboard
│       │       ├── AppListActivity.java # Installed app selector
│       │       ├── ClonedAppAdapter.java
│       │       └── AvailableAppAdapter.java
│       └── res/                         # UI layouts, colors, strings, themes
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🚀 Building & Running

1. Open this directory in **Android Studio** (Hedgehog, Iguana, Jellyfish, or newer).
2. Sync Gradle dependencies.
3. Build and install to any Android device or emulator running **Android 7.0 (API 24) to Android 14 (API 34)**:
   ```bash
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
4. Open **CloneApp**, tap the `+` button to browse installed apps, select any app to clone, and launch independent multiple accounts!
