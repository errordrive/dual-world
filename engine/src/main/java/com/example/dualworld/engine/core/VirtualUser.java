package com.example.dualworld.engine.core;

import java.io.File;

/**
 * Multi-user identity model for virtualized guest applications.
 */
public class VirtualUser {
    public final int userId;
    public final String userName;
    public final long createdAt;
    private File mStorageDir;

    public VirtualUser(int userId, String userName) {
        this.userId = userId;
        this.userName = userName;
        this.createdAt = System.currentTimeMillis();
    }

    public void setStorageDir(File storageDir) {
        this.mStorageDir = storageDir;
    }

    public File getStorageDir() {
        return mStorageDir;
    }

    @Override
    public String toString() {
        return "VirtualUser{id=" + userId + ", name='" + userName + "'}";
    }
}
