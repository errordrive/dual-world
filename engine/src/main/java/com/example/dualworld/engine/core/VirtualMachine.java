package com.example.dualworld.engine.core;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;

/**
 * Per-clone Virtual Machine instance managing runtime lifecycle within an agent process.
 */
public class VirtualMachine {
    private static final String TAG = "VirtualMachine";

    public enum State {
        STOPPED,
        STARTING,
        RUNNING,
        DESTROYED
    }

    public final String packageName;
    public final int userId;
    public final int agentIndex;
    public final String processName;

    private State mState = State.STOPPED;
    private ClassLoader mClassLoader;
    private Application mGuestApplication;
    private File mSandboxDataDir;
    private File mGuestApkFile;
    private long mStartedAt = 0;

    public VirtualMachine(String packageName, int userId, int agentIndex, String processName) {
        this.packageName = packageName;
        this.userId = userId;
        this.agentIndex = agentIndex;
        this.processName = processName;
    }

    public synchronized void create(File apkFile, File sandboxDataDir, ClassLoader classLoader) {
        this.mGuestApkFile = apkFile;
        this.mSandboxDataDir = sandboxDataDir;
        this.mClassLoader = classLoader;
        this.mState = State.STARTING;
        Log.i(TAG, "Created VM for " + packageName + " (user " + userId + ") in agent " + agentIndex);
    }

    public synchronized void start(Application guestApplication) {
        this.mGuestApplication = guestApplication;
        this.mState = State.RUNNING;
        this.mStartedAt = System.currentTimeMillis();
        Log.i(TAG, "VM started running: " + packageName + " (user " + userId + ")");
    }

    public synchronized void stop() {
        if (mState == State.RUNNING || mState == State.STARTING) {
            this.mState = State.STOPPED;
            Log.i(TAG, "VM stopped: " + packageName);
        }
    }

    public synchronized void destroy() {
        stop();
        this.mState = State.DESTROYED;
        this.mGuestApplication = null;
        this.mClassLoader = null;
        Log.i(TAG, "VM destroyed: " + packageName);
    }

    public synchronized State getState() {
        return mState;
    }

    public boolean isRunning() {
        return mState == State.RUNNING;
    }

    public ClassLoader getClassLoader() {
        return mClassLoader;
    }

    public Application getGuestApplication() {
        return mGuestApplication;
    }

    public File getSandboxDataDir() {
        return mSandboxDataDir;
    }

    public File getGuestApkFile() {
        return mGuestApkFile;
    }

    public long getStartedAt() {
        return mStartedAt;
    }
}
