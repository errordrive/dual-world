package com.example.dualworld;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.example.dualworld.core.VirtualCore;
import com.example.dualworld.engine.core.VirtualEngine;

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
        VirtualEngine.get().init(this);
        VirtualCore.get().attachBaseContext(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate() process=" + getProcessName());
        VirtualEngine.get().init(this);
        VirtualCore.get().initialize(this);
    }
}
