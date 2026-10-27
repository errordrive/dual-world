package com.example.dualworld.core;

import android.graphics.drawable.Drawable;

/**
 * Metadata model for an application cloned inside the virtual container.
 */
public class VirtualAppModel {
    public final String packageName;
    public String appName;
    public final int userId;
    public final String apkPath;
    public Drawable icon;

    public VirtualAppModel(String packageName, String appName, int userId, String apkPath) {
        this.packageName = packageName;
        this.appName = appName;
        this.userId = userId;
        this.apkPath = apkPath;
    }

    public String getKey() {
        return packageName + "@" + userId;
    }
}
