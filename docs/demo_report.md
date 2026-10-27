# REA (Reverse Engineer Anything) Live Demonstration Report

This report summarizes the end-to-end evaluation and live demonstration of the **Reverse Engineer Anything (REA)** tool suite in an Android application virtualization study, clean-room software recreation, binary verification, and automated differential analysis.

---

## 1. Executive Summary

- **Subject Under Study:** Commercial multi-account virtualization container (`Dual Space_1.205-248.0.10.apk`).
- **Clean-Room Implementation:** An original, independent Android virtualization container — **Dual World** (`com.example.dualworld`), engineered completely from scratch in Java and Material 3.
- **Repository:** [`https://github.com/errordrive/dual-world`](https://github.com/errordrive/dual-world)
- **Cloud Build Pipeline:** GitHub Actions continuous integration running Gradle assemble on Ubuntu 24.04 runners with Temurin JDK 17.
- **Artifacts Produced & Analyzed:**
  - `dualworld-v1.0.apk` (5,820,482 bytes, SHA256 `9fd5467bd72257af4a4b4cbc33cfac4ecc7949af9a4b9808c9a8b80311237057`)
  - `dualworld-v1.1.apk` (5,822,530 bytes, SHA256 `fc7798c39ed80facdaa934848138af60e3028bed068c1c514e21ac0e97dd3189`)

---

## 2. Step-by-Step REA Actions & Workflow

```mermaid
flowchart TD
    A["Dual Space APK (Commercial)"] -->|"REA decode-android-resources & JADX"| B["Architectural Extraction\n(Topology, Hooks, IPC)"]
    B --> C["Clean-Room Spec\ndocs/feature_list.md"]
    C --> D["Original Implementation\napp/ (Java, Material 3)"]
    D --> E["GitHub Actions Cloud CI\n(Dual World v1.0.0)"]
    E --> F["REA Static Analysis\ndualworld-v1.0.apk"]
    F --> G["docs/static_analysis.md"]
    F --> H["docs/feature_tracing.md"]
    D -->|"Feature Addition & Version Bump"| I["Dual World v1.1.0"]
    I --> J["GitHub Actions Cloud CI\n(Dual World v1.1.0)"]
    J --> K["REA Resource & Binary Diff"]
    K --> L["docs/version_diff.md"]
    L --> M["docs/demo_report.md"]
```

### Step 1: Study & Architectural Extraction
- **REA Operation:** `decode_android_resources` (Apktool provider) on `Dual Space_1.205-248.0.10.apk`.
- **Target Analysis:** Deconstructed resource layout, manifest entries, internal payload paths (`assets/hwb.apk`), and process topology.
- **Findings:**
  1. *Process Architecture:* Daemon process `:service` handles centralized package/activity registries; worker processes (`:agent0`..`:agent13`) host sandboxed guest apps.
  2. *Hooking Mechanism:* Replaces `ActivityThread.mH` handler callback to intercept system-bound launch intents and substitute stub activities.
  3. *Storage Sandboxing:* Virtual user directory redirection mapping standard app paths to `/data/data/<host>/virtual/users/<userId>/<pkg>`.
- **Clean-room Separation:** No decompiled code, native libraries (`libgkspacev.so`), encrypted assets (`hwb.apk`), or obfuscation XOR keys were copied. Dual World was authored independently using modern Android SDK and clean Java dynamic proxies.

### Step 2: REA Static Analysis on Self-Built APK
- **REA Operation:** `rea decode-android-resources dualworld-v1.0.apk`
- **Generated Evidence ID:** `ev_2ee21eb25ab26c3b9e67c896306b93b9ebebf73a2676589e0246b2c0fc941bcf`
- **Output:** Manifest parsing, component inventory (17 components: 6 activities, 4 services, 6 providers, 1 receiver across 6 distinct processes), 9 permissions, entry points, and call-graph structure.
- **Artifact:** Saved to [`docs/static_analysis.md`](file:///storage/emulated/0/ApkLab/CloneApp/docs/static_analysis.md).

### Step 3: REA Feature Tracing
- **Traced Features:**
  1. *Clone Installed App:* `AppListActivity` -> `VirtualCore.installApp()` -> `VPackageManagerService.installPackage()` -> Filesystem provisioning.
  2. *Launch Cloned App:* `MainActivity` -> `VirtualCore.launchApp()` -> `VActivityManagerService.startActivity()` -> Stub Resolution (`StubActivity$P0`) -> `ActivityThreadHook` -> Guest `Activity.onCreate()`.
  3. *Remove Clone:* `MainActivity` long-press -> `VirtualCore.uninstallApp()` -> Recursive deletion of `/virtual/users/<userId>/<pkg>`.
- **Artifact:** Saved to [`docs/feature_tracing.md`](file:///storage/emulated/0/ApkLab/CloneApp/docs/feature_tracing.md).

### Step 4: REA Version Comparison & Differential Analysis
- **REA Operation:** `rea decode-android-resources dualworld-v1.1.apk`
- **Generated Evidence ID:** `ev_476feee0985eadc25a933150cf1113ed1119fc116ef5c7c2acd07f2f693f201e`
- **Comparison Engine:** Normalized JSON evidence extraction comparing string pools, DEX classes, and manifest attributes between v1.0.0 and v1.1.0.
- **Key Deltas Detected:**
  - `versionCode`: `1` -> `2`, `versionName`: `1.0.0` -> `1.1.0`
  - 4 added resource strings: `action_rename`, `rename_dialog_title`, `rename_dialog_hint`, `btn_save`
  - Added class: `Lcom/example/dualworld/core/VirtualCloneConfig;` in `classes7.dex`
  - Modified bytecode containers: `classes2.dex`, `classes7.dex`, `resources.arsc`
- **Artifact:** Saved to [`docs/version_diff.md`](file:///storage/emulated/0/ApkLab/CloneApp/docs/version_diff.md).

---

## 3. Tool Performance & Timings

| Phase / Operation | Tool / Provider | Execution Time | Output Status |
| :--- | :--- | :--- | :--- |
| Resource Decoding (Dual Space) | REA Apktool `2.7.0-dirty` | ~18s | Evidence record generated |
| Cloud Build v1.0.0 | GitHub Actions (`ubuntu-latest`) | 1m 48s | Green (Run `40549589863`) |
| REA Static Analysis (v1.0.0 APK) | REA `decode-android-resources` | ~12s | Evidence ID `ev_2ee21eb2...` |
| Cloud Build v1.1.0 | GitHub Actions (`ubuntu-latest`) | 1m 23s | Green (Run `40550423012`) |
| REA Static Analysis (v1.1.0 APK) | REA `decode-android-resources` | ~13s | Evidence ID `ev_476feee0...` |
| REA Differential String Extraction | Python + REA JSON normalized record | ~1s | 4 strings identified |
| DEX Class Diffing | Symbol scanner across DEX slices | ~2s | `VirtualCloneConfig` identified |

---

## 4. Observed REA Strengths & Limitations

### Strengths
1. **Normalized Evidence Format:** Standardized JSON outputs allow programmatic querying of manifests, strings, architectures, and certificates across heterogeneous targets.
2. **Deterministic Hashing:** Automatically records target file size, SHA-256 digest, and canonical evidence hashes, ensuring traceability.
3. **Decoupled Architecture:** Clean separation between decoding engines (Apktool, JADX) and analytical consumers.

### Limitations & Workarounds
1. **JADX MCP Jar Requirement:** `inspect-android-package` requires `REA_JADX_MCP_JAR` environment variable to decompile DEX directly within REA.  
   *Workaround:* Leveraged REA's `decode-android-resources` for manifest and resource analysis alongside system JADX / DEX byte-scanners for class enumeration.
2. **Apktool Version Advisory:** REA reported that embedded Apktool `2.7.0-dirty` is older than upstream `2.9.2` (subject to CVE-2024-21633 path sanitization advisory). While safe on our clean self-built target, future deployments should update the underlying Apktool binary.
3. **Single-Record vs Session Bundle:** `rea compare` expects a multi-record session bundle from `export_evidence_bundle` rather than raw individual evidence files.  
   *Workaround:* Used REA JSON records with automated script diffing for exact string and structural matching.

---

## 5. Clean-Room Compliance Verification

Throughout all phases of this demonstration:
1. **No Proprietary Code Reuse:** Dual World was authored using genuine Android SDK APIs (`android.os.Process`, `java.lang.reflect.Proxy`, `android.os.Handler.Callback`, `android.content.pm.PackageInfo`).
2. **Isolation of Research from Implementation:** All reverse-engineering findings remain exclusively in `docs/` and internal memory; the `app/` codebase contains only original code and Material 3 design elements.
3. **Token Privacy:** The GitHub personal access token was injected dynamically via environment variables in command pipelines and was never written to any file, repository commit, or log file.
