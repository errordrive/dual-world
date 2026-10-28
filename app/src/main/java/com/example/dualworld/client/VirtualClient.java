package com.example.dualworld.client;

import android.app.Application;
import android.content.Context;
import android.util.Log;

public class VirtualClient {
    private static final String TAG = "VirtualClient";
    private static final VirtualClient sInstance = new VirtualClient();

    private Context mContext;
    private String mCurrentPackageName;
    private int mCurrentUserId = 0;
    private int mAgentIndex = 0;

    public static VirtualClient get() {
        return sInstance;
    }

    private VirtualClient() {}

    public void attachBaseContext(Application app) {
        mContext = app;
        mAgentIndex = resolveAgentIndex(app);
        Log.i(TAG, "VirtualClient attachBaseContext for agent: " + mAgentIndex);
        // Note: Do not overwrite engine's ActivityThreadInterceptor with dummy hook
    }

    public void init(Application app) {
        Log.i(TAG, "VirtualClient initialized in agent process");
    }

    private int resolveAgentIndex(Context context) {
        String processName = "";
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            processName = Application.getProcessName();
        }
        if (processName.contains(":agent")) {
            try {
                String suffix = processName.substring(processName.indexOf(":agent") + 6);
                return Integer.parseInt(suffix);
            } catch (Exception ignored) {}
        }
        return 0;
    }

    public void setCurrentApp(String packageName, int userId) {
        mCurrentPackageName = packageName;
        mCurrentUserId = userId;
    }

    public String getCurrentPackageName() {
        return mCurrentPackageName;
    }

    public int getCurrentUserId() {
        return mCurrentUserId;
    }

    public int getAgentIndex() {
        return mAgentIndex;
    }
}
