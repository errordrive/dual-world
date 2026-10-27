package com.example.dualworld.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.dualworld.engine.cache.AppMetadataCache
import com.example.dualworld.engine.core.VirtualEngine
import java.io.File

/**
 * Repository mediating between the UI layer and the VirtualEngine core.
 */
class CloneRepository(private val context: Context) {
    private val engine = VirtualEngine.get()

    fun getAllClones(): List<AppMetadataCache.AppEntry> {
        return engine.allClones
    }

    fun installClone(packageName: String, apkPath: String, userId: Int = 0): Boolean {
        return engine.installClone(packageName, apkPath, userId)
    }

    fun launchClone(packageName: String, userId: Int = 0): Boolean {
        return engine.launchClone(context, packageName, userId)
    }

    fun uninstallClone(packageName: String, userId: Int = 0): Boolean {
        return engine.uninstallClone(packageName, userId)
    }

    fun renameClone(packageName: String, userId: Int = 0, newName: String): Boolean {
        return engine.renameClone(packageName, userId, newName)
    }

    fun getInstalledApps(): List<InstalledAppItem> {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        val list = mutableListOf<InstalledAppItem>()

        for (pkg in packages) {
            // Exclude self and persistent system packages without launch intents
            if (pkg.packageName == context.packageName) continue
            val launchIntent = pm.getLaunchIntentForPackage(pkg.packageName) ?: continue
            val appInfo = pkg.applicationInfo ?: continue
            val label = pm.getApplicationLabel(appInfo).toString()
            val sizeBytes = File(appInfo.sourceDir).length()

            list.add(
                InstalledAppItem(
                    packageName = pkg.packageName,
                    appName = label,
                    versionName = pkg.versionName ?: "1.0",
                    versionCode = pkg.versionCode,
                    apkPath = appInfo.sourceDir,
                    sizeBytes = sizeBytes,
                    isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            )
        }
        list.sortBy { it.appName.lowercase() }
        return list
    }
}

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Int,
    val apkPath: String,
    val sizeBytes: Long,
    val isSystem: Boolean,
    var isSelected: Boolean = false
)
