# REA Version Diff: Dual World v1.0.0 vs v1.1.0

This report documents the automated and structural binary comparison performed using **REA (Reverse Engineer Anything)** between the debug builds of Dual World **v1.0.0** and **v1.1.0**.

---

## 1. Artifact Profiles & Evidence Metadata

| Metric | Version 1.0.0 (`v1.0`) | Version 1.1.0 (`v1.1`) | Diff / Delta |
| :--- | :--- | :--- | :--- |
| **Filename** | `dualworld-v1.0.apk` | `dualworld-v1.1.apk` | Target update |
| **File Size** | 5,820,482 bytes (5.55 MB) | 5,822,530 bytes (5.55 MB) | +2,048 bytes (+0.035%) |
| **SHA-256 Digest** | `9fd5467bd72257af4a4b4cbc33cfac4ecc7949af9a4b9808c9a8b80311237057` | `fc7798c39ed80facdaa934848138af60e3028bed068c1c514e21ac0e97dd3189` | Mutated |
| **REA Evidence ID** | `ev_2ee21eb25ab26c3b9e67c896306b93b9ebebf73a2676589e0246b2c0fc941bcf` | `ev_476feee0985eadc25a933150cf1113ed1119fc116ef5c7c2acd07f2f693f201e` | Re-evaluated |
| **Analysis Provider** | REA Apktool `2.7.0-dirty` | REA Apktool `2.7.0-dirty` | Identical pipeline |
| **Decoded Files** | 1,108 files | 1,108 files | No new resource files |

---

## 2. Manifest & Platform Metadata Changes

REA normalized extraction of `AndroidManifest.xml` yielded the following metadata differences:

| Attribute | v1.0.0 | v1.1.0 | Status |
| :--- | :--- | :--- | :--- |
| `android:versionCode` | `1` | `2` | **Incremented (+1)** |
| `android:versionName` | `1.0.0` | `1.1.0` | **Bumped (Minor)** |
| `package` | `com.example.dualworld` | `com.example.dualworld` | Identical |
| `minSdkVersion` | `24` (Android 7.0) | `24` (Android 7.0) | Identical |
| `targetSdkVersion` | `34` (Android 14) | `34` (Android 14) | Identical |
| `compileSdkVersion` | `34` | `34` | Identical |

### Permissions & Component Integrity
- **Declared Permissions:** 0 added, 0 removed. Both versions maintain the 9 declared permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`, `POST_NOTIFICATIONS`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`).
- **Component Inventory:** Unchanged:
  - 6 Activities (`MainActivity`, `AppListActivity`, `StubActivity$P0`..`$P3`)
  - 4 Services (`StubService$P0`..`$P3`)
  - 6 Content Providers (`VirtualServerProvider`, `StubContentProvider$P0`..`$P3`, `androidx.startup.InitializationProvider`)
  - 1 Broadcast Receiver (`ProfileInstalledReceiver`)

---

## 3. String & Resource Delta Analysis

REA string pool comparison revealed 4 newly defined resource strings supporting clone customization and renaming:

```diff
+ <string name="action_rename">Rename Clone</string>
+ <string name="rename_dialog_title">Edit Account Label</string>
+ <string name="rename_dialog_hint">Enter custom name for this clone</string>
+ <string name="btn_save">Save Changes</string>
```

- Total string pool count increased from **176** to **180** strings.
- Target resource table `resources.arsc` modified with new ID entries assigned under namespace `com.example.dualworld`.

---

## 4. Dalvik Executable (DEX) & Bytecode Diff

Inspection of internal DEX entries (`classes.dex` through `classes15.dex`):

| APK Container Entry | v1.0.0 Size (bytes) | v1.1.0 Size (bytes) | Change |
| :--- | :--- | :--- | :--- |
| `classes2.dex` | 3,365,064 | 3,366,112 | +1,048 bytes (Modified) |
| `classes7.dex` | 46,260 | 47,156 | +896 bytes (Modified) |
| `resources.arsc` | 260,868 | 261,244 | +376 bytes (Modified) |
| Remaining entries | Unchanged | Unchanged | Identical |

### Class Hierarchy Comparison
A symbol inventory scan of `Lcom/example/dualworld/*;` across all DEX slices:
- **v1.0.0 Class Count:** 62 classes
- **v1.1.0 Class Count:** 63 classes (+1 new class)

#### Newly Introduced Class
```java
package com.example.dualworld.core;

/**
 * Configuration and feature flags introduced in Dual World v1.1.0.
 */
public class VirtualCloneConfig {
    public static final String ENGINE_VERSION = "1.1.0";
    public static final int CONFIG_REVISION = 2;
    public static boolean sEnableVerboseLogging = true;
    public static boolean sEnableAutoPathCleanup = true;
}
```

---

## 5. Summary of Version 1.1.0 Additions

1. **Engine Version Bump:** Clean transition from v1.0.0 (build 1) to v1.1.0 (build 2) without regression in stub topology or IPC contracts.
2. **Dynamic Clone Customization UI Foundations:** Addition of resource keys for clone labeling (`action_rename`, `rename_dialog_title`, `rename_dialog_hint`, `btn_save`).
3. **Core Configuration Layer:** Addition of [`VirtualCloneConfig`](file:///storage/emulated/0/ApkLab/CloneApp/app/src/main/java/com/example/dualworld/core/VirtualCloneConfig.java) providing runtime feature toggling (`sEnableVerboseLogging`, `sEnableAutoPathCleanup`) and revision tracking.
