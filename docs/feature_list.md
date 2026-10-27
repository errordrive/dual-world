# Dual Space Reverse Engineering — Extracted Feature List

**Target Studied:** `Dual Space_1.205-248.0.10.apk` (Package: `com.xunijun.app.gp`)  
**Analysis Engine:** REA (Reverse Engineer Anything) + JADX decompilation  
**Clean-Room Target Implementation:** `Dual World` (`com.example.dualworld`)

---

## 1. Core Feature Inventory

From our static and structural reverse engineering of Dual Space, the following core features constitute the app cloning & virtualization experience:

| Feature ID | Feature Name | Description Observed in Dual Space | Clean-Room Implementation in Dual World |
| :--- | :--- | :--- | :--- |
| **FEAT-01** | **Scan & Discover Installed Apps** | Queries `PackageManager` for apps with `CATEGORY_LAUNCHER`, filtering out system apps and host packages. | `AppListActivity` scans installed apps via `getInstalledApplications` and displays them in a clean Material 3 list. |
| **FEAT-02** | **App Cloning / Creation** | Allocates an isolated sandbox profile for the selected package and saves metadata to persistent storage. | `VirtualCore.installPackage(packageName, userId)` registers the cloned package and allocates its profile. |
| **FEAT-03** | **Multi-User / Multi-Instance Clones** | Supports multiple simultaneous instances of the same application (e.g. Account #1, Account #2, Account #3). | `VirtualUser` tracks distinct `userId`s (0, 1, 2, ...), allowing multiple clones of WhatsApp, Telegram, etc. |
| **FEAT-04** | **Per-User Isolated Sandbox Storage** | Transparently redirects file operations so each account gets its own private data and cache directory. | `VirtualStorage` allocates `<host_data>/virtual/user/<userId>/<pkg>` and isolates shared preferences, databases, and files. |
| **FEAT-05** | **Component Stub Multiplexing** | Uses pre-declared manifest components in isolated process slots (`:agent0` .. `:agent3`) to run guest apps. | `StubActivity.P0`..`P3`, `StubService.P0`..`P3`, `StubContentProvider.P0`..`P3` multiplex execution into dedicated agent processes. |
| **FEAT-06** | **Launch Cloned App** | Resolves the guest app's launch intent, wraps it into a stub routing intent, and launches it in the target agent process. | `VirtualCore.launchApp(context, pkg, userId)` wraps the launch intent and starts the allocated `StubActivity`. |
| **FEAT-07** | **Cloned App Dashboard / Management** | Displays all currently cloned apps with their app icon, clone instance badge, and package identifier. | `MainActivity` shows a modern Material 3 grid/list with quick launch and delete actions. |
| **FEAT-08** | **Clone Removal & Data Cleanup** | Uninstalls the virtual clone and recursively purges its private sandboxed directory without touching host or other accounts. | `VirtualCore.uninstallPackage(pkg, userId)` unregisters the clone and deletes its sandboxed filesystem hierarchy. |
| **FEAT-09** | **Clone Renaming (v1.1 Target)** | Allows users to assign custom alias labels to cloned accounts (e.g., "Personal WhatsApp", "Work WhatsApp"). | Added in v1.1 via `VirtualCore.renameClone(pkg, userId, newName)` and UI dialog. |
