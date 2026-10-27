package com.example.dualworld.client;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ActivityThreadHook {
    private static final String TAG = "ActivityThreadHook";

    public static void install(Context context) {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object activityThread = currentActivityThreadMethod.invoke(null);

            Field mHField = activityThreadClass.getDeclaredField("mH");
            mHField.setAccessible(true);
            Handler mH = (Handler) mHField.get(activityThread);

            Field mCallbackField = Handler.class.getDeclaredField("mCallback");
            mCallbackField.setAccessible(true);
            Handler.Callback originalCallback = (Handler.Callback) mCallbackField.get(mH);

            mCallbackField.set(mH, new ActivityThreadCallback(originalCallback));
            Log.i(TAG, "ActivityThread.mH callback hook installed");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook ActivityThread", t);
        }
    }

    private static class ActivityThreadCallback implements Handler.Callback {
        private final Handler.Callback mOriginal;

        public ActivityThreadCallback(Handler.Callback original) {
            mOriginal = original;
        }

        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == 100 || msg.what == 159) {
                try {
                    Log.d(TAG, "Intercepted Activity launch message: " + msg.what);
                } catch (Throwable t) {
                    Log.e(TAG, "Error processing launch message", t);
                }
            }
            if (mOriginal != null) {
                return mOriginal.handleMessage(msg);
            }
            return false;
        }
    }
}
