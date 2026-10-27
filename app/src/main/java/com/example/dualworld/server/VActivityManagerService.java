package com.example.dualworld.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Virtual Activity Manager Service for Dual World.
 */
public class VActivityManagerService {
    private static final String TAG = "VActivityManagerService";
    private static final VActivityManagerService sInstance = new VActivityManagerService();

    private Context mContext;

    public static VActivityManagerService get() {
        return sInstance;
    }

    private VActivityManagerService() {}

    public void init(Context context) {
        mContext = context;
        Log.i(TAG, "Virtual Activity Manager Service started");
    }

    public ComponentName resolveStubActivity(Intent targetIntent, int userId) {
        ComponentName originalComponent = targetIntent.getComponent();
        String pkg = originalComponent != null ? originalComponent.getPackageName() : targetIntent.getPackage();

        int agentIndex = 0;
        if (pkg != null) {
            agentIndex = Math.abs(pkg.hashCode() + userId) % 4;
        }

        String stubClass = "com.example.dualworld.stub.StubActivity$P" + agentIndex;
        return new ComponentName(mContext.getPackageName(), stubClass);
    }
}
