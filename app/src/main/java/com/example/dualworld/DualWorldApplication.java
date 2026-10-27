package com.example.dualworld;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.example.dualworld.core.VirtualCore;

/**
 * Main Application class for Dual World.
 * Coordinates early-stage sandbox bootstrapping and process differentiation.
 */
public class DualWorldApplication extends Application {
    private static final String TAG = "DualWorldApplication";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Log.i(TAG, "attachBaseContext() process=" + getProcessName());
        VirtualCore.get().attachBaseContext(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate() process=" + getProcessName());
        VirtualCore.get().initialize(this);
    }
}
