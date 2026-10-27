package com.example.dualworld.engine.hook;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.dualworld.engine.ipc.ParcelRewriter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Intercepts ActivityThread.mH messages (BIND_APPLICATION, LAUNCH_ACTIVITY, EXECUTE_TRANSACTION)
 * to rewrite stub tokens into genuine guest component definitions before framework execution.
 */
public class ActivityThreadInterceptor {
    private static final String TAG = "ActivityThreadInterceptor";

    public static final int BIND_APPLICATION = 110;
    public static final int LAUNCH_ACTIVITY = 100;
    public static final int EXECUTE_TRANSACTION = 159;

    public static void install(Context context) {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method currentAtMethod = atClass.getDeclaredMethod("currentActivityThread");
            currentAtMethod.setAccessible(true);
            Object activityThread = currentAtMethod.invoke(null);

            Field mHField = atClass.getDeclaredField("mH");
            mHField.setAccessible(true);
            Handler mH = (Handler) mHField.get(activityThread);

            Field mCallbackField = Handler.class.getDeclaredField("mCallback");
            mCallbackField.setAccessible(true);
            Handler.Callback originalCallback = (Handler.Callback) mCallbackField.get(mH);

            mCallbackField.set(mH, new HandlerCallbackWrapper(originalCallback, context));
            Log.i(TAG, "ActivityThreadInterceptor successfully installed on mH");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install ActivityThreadInterceptor", t);
        }
    }

    private static class HandlerCallbackWrapper implements Handler.Callback {
        private final Handler.Callback mOriginal;
        private final Context mContext;

        public HandlerCallbackWrapper(Handler.Callback original, Context context) {
            this.mOriginal = original;
            this.mContext = context;
        }

        @Override
        public boolean handleMessage(Message msg) {
            try {
                if (msg.what == LAUNCH_ACTIVITY) {
                    handleLegacyLaunchActivity(msg.obj);
                } else if (msg.what == EXECUTE_TRANSACTION) {
                    handleExecuteTransaction(msg.obj);
                } else if (msg.what == BIND_APPLICATION) {
                    handleBindApplication(msg.obj);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error handling message " + msg.what, t);
            }

            if (mOriginal != null) {
                return mOriginal.handleMessage(msg);
            }
            return false;
        }

        private void handleLegacyLaunchActivity(Object record) {
            if (record == null) return;
            try {
                Class<?> recordClass = record.getClass();
                Field intentField = recordClass.getDeclaredField("intent");
                intentField.setAccessible(true);
                Intent stubIntent = (Intent) intentField.get(record);

                if (stubIntent != null && stubIntent.hasExtra(ParcelRewriter.EXTRA_TARGET_INTENT)) {
                    Intent guestIntent = stubIntent.getParcelableExtra(ParcelRewriter.EXTRA_TARGET_INTENT);
                    if (guestIntent != null) {
                        intentField.set(record, guestIntent);
                        Log.i(TAG, "Legacy LAUNCH_ACTIVITY unwrapped: " + guestIntent.getComponent());
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to rewrite legacy ActivityClientRecord", t);
            }
        }

        private void handleExecuteTransaction(Object transaction) {
            if (transaction == null) return;
            try {
                Class<?> transactionClass = transaction.getClass();
                Method getCallbacksMethod = transactionClass.getDeclaredMethod("getCallbacks");
                getCallbacksMethod.setAccessible(true);
                List<?> callbacks = (List<?>) getCallbacksMethod.invoke(transaction);

                if (callbacks != null) {
                    for (Object item : callbacks) {
                        if (item != null && item.getClass().getName().endsWith("LaunchActivityItem")) {
                            Field mIntentField = item.getClass().getDeclaredField("mIntent");
                            mIntentField.setAccessible(true);
                            Intent stubIntent = (Intent) mIntentField.get(item);

                            if (stubIntent != null && stubIntent.hasExtra(ParcelRewriter.EXTRA_TARGET_INTENT)) {
                                Intent guestIntent = stubIntent.getParcelableExtra(ParcelRewriter.EXTRA_TARGET_INTENT);
                                if (guestIntent != null) {
                                    mIntentField.set(item, guestIntent);
                                    Log.i(TAG, "EXECUTE_TRANSACTION LaunchActivityItem unwrapped: " + guestIntent.getComponent());
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to inspect ClientTransaction", t);
            }
        }

        private void handleBindApplication(Object appBindData) {
            Log.d(TAG, "Intercepted BIND_APPLICATION");
        }
    }
}
