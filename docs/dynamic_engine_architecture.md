# Dynamic Asset-Based Engine Architecture (v2.2.0)

## 1. Investigation of Target App Assets (`Dual Space`)

A deep forensic inspection of the reference target app `Dual Space_1.205-248.0.10.apk` revealed that the host APK's dex classes are primarily UI and stub containers. The true virtualization engine is decoupled and bundled within the assets directory:

- **Payload Path:** `assets/hwb.apk`
- **Compressed Size:** 1.57 MB (Uncompressed: 4.51 MB)
- **Container Structure:**
  - `AndroidManifest.xml`: Pure container manifest (`com.xunijun.app.gp.stub`, `versionCode=654`, `minSdkVersion=24`, `targetSdkVersion=33`).
  - `classes.dex`: 2,147,888 bytes containing the virtualization core services, framework hooks, and stub routing.
  - `lib/arm64-v8a/` and `lib/armeabi-v7a/`:
    - `liblgyezi.so`: Native I/O and filesystem virtualization redirection.
    - `libgkspacev.so`: Native Binder transaction and IPC proxying.
    - `libgkspacevm.so`: ART runtime method hooking and trampoline engine.
- **Runtime Bootstrap Flow:**
  1. `com.xunijun.app.gp.App.attachBaseContext()` triggers asset verification.
  2. The compressed asset `assets/hwb.apk` is extracted to private app storage: `<data_dir>/sipx/sdk/0/base.apk`.
  3. Native libraries matching the active device ABI (`Build.SUPPORTED_ABIS[0]`) are extracted to `<data_dir>/sipx/sdk/0/lib/<abi>/`.
  4. A `DexClassLoader` is instantiated pointing to the extracted `base.apk` and native library directory.
  5. The host delegates framework hooking and sandbox initialization dynamically via reflection.

---

## 2. Clean-Room Architectural Implementation in Dual World

In accordance with clean-room guidelines, Dual World reproduces this decoupled dynamic architecture completely from scratch with 100% original code:

### 2.1 Dynamic Engine Asset (`engine-core.apk`)
- **Asset Location:** `app/src/main/assets/engine-core.apk` (packaged directly into `assets/engine-core.apk` of the final APK).
- **Contents:**
  - `AndroidManifest.xml`: Lightweight manifest (`package="com.example.dualworld.engine.core"`).
  - `classes.dex` through `classes4.dex`: Precompiled clean-room engine subsystems, including `VirtualEngine`, `SandboxFileSystem`, `ApkInstaller`, `ActivityThreadInterceptor`, `InstrumentationProxy`, `BinderProxyManager`, `IpcRouter`, `ComponentResolver`, `PermissionMirror`, and diagnostic caches.

### 2.2 Dynamic Engine Loader (`DynamicEngineLoader.java`)
- Located at: `engine/src/main/java/com/example/dualworld/engine/core/DynamicEngineLoader.java`
- Key Responsibilities:
  1. **Asset Detection & Atomic Extraction:**
     - Inspects `context.getAssets()` for `engine-core.apk`.
     - Atomically extracts the payload to `<private_files>/app_engine_core/engine-core.apk` via temporary staging files (`.tmp -> renameTo`).
  2. **Native Library Linking:**
     - Inspects zip entries for `lib/<abi>/*.so` matching the device's hardware ABI and extracts them to `<private_files>/app_engine_core/lib/<abi>/`.
  3. **DexClassLoader Instantiation:**
     - Constructs an isolated `DexClassLoader` targeting the extracted APK, private optimized cache directory (`app_engine_core_opt`), and native library directory.
  4. **Dynamic Bootstrap & Fail-Safe In-Tree Fallback:**
     - Dynamically resolves and invokes `com.example.dualworld.engine.core.VirtualEngine.get().init(application)`.
     - If the asset is missing, corrupted, or incompatible, gracefully falls back to the compiled in-tree `VirtualEngine` without crashing.

### 2.3 Application & Core Wiring
- **`DualWorldApplication.java`**:
  - `attachBaseContext()`: calls `DynamicEngineLoader.install(base)` and `DynamicEngineLoader.init(this)`.
  - `onCreate()`: ensures `DynamicEngineLoader.init(this)`.
- **`VirtualCore.java`**:
  - `attachBaseContext()`: delegates engine initialization to `DynamicEngineLoader.init(app)`.

---

## 3. Versioning & Release Verification

- **Version Code:** `8`
- **Version Name:** `2.2.0`
- **Git Tag:** `v2.2.0`
- **GitHub Commit:** `c1e481f` (`feat(engine): implement dynamic core engine loading from assets/engine-core.apk (v2.2.0)`)
- **CI Build Status:** GitHub Actions Workflow Run `40747162038` - **SUCCESS**
- **GitHub Release:** Created with `app-debug.apk` asset for tag `v2.2.0`.
- **Local Artifact:** `/storage/emulated/0/ApkLab/CloneApp/DualWorld-v2.2.0-debug.apk`
- **SHA-256:** `f9f2ff8e8e261084e796a0c41e7a1e0af72ec5aacf778b5806406e4b813074c1`
