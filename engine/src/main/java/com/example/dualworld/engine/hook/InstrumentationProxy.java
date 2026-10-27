package com.example.dualworld.engine.hook;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.example.dualworld.engine.core.VirtualMachine;
import com.example.dualworld.engine.ipc.IpcRouter;
import com.example.dualworld.engine.ipc.ParcelRewriter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Custom Instrumentation proxy intercepting activity lifecycle creation, class resolution,
 * and context injection for virtualized guest apps.
 */
public class InstrumentationProxy extends Instrumentation {
    private static final String TAG = "InstrumentationProxy";

    private final Instrumentation mBase;
    private final Context mHostContext;

    public static InstrumentationProxy install(Context hostContext) {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method currentAtMethod = atClass.getDeclaredMethod("currentActivityThread");
            currentAtMethod.setAccessible(true);
            Object activityThread = currentAtMethod.invoke(null);

            Field mInstrumentationField = atClass.getDeclaredField("mInstrumentation");
            mInstrumentationField.setAccessible(true);
            Instrumentation original = (Instrumentation) mInstrumentationField.get(activityThread);

            if (!(original instanceof InstrumentationProxy)) {
                InstrumentationProxy proxy = new InstrumentationProxy(original, hostContext);
                mInstrumentationField.set(activityThread, proxy);
                Log.i(TAG, "InstrumentationProxy installed on ActivityThread");
                return proxy;
            } else {
                return (InstrumentationProxy) original;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install InstrumentationProxy", t);
            return null;
        }
    }

    public InstrumentationProxy(Instrumentation base, Context hostContext) {
        this.mBase = base;
        this.mHostContext = hostContext;
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        // Check if intent contains wrapped guest intent
        if (intent != null && intent.hasExtra(ParcelRewriter.EXTRA_TARGET_INTENT)) {
            Intent guestIntent = intent.getParcelableExtra(ParcelRewriter.EXTRA_TARGET_INTENT);
            String guestPkg = intent.getStringExtra(ParcelRewriter.EXTRA_GUEST_PKG);
            int userId = intent.getIntExtra(ParcelRewriter.EXTRA_USER_ID, 0);

            if (guestIntent != null && guestIntent.getComponent() != null) {
                String guestClassName = guestIntent.getComponent().getClassName();
                VirtualMachine vm = IpcRouter.get().route(guestPkg, userId);
                ClassLoader targetLoader = (vm != null && vm.getClassLoader() != null) ? vm.getClassLoader() : cl;

                Log.i(TAG, "newActivity swapping stub " + className + " -> guest " + guestClassName);
                try {
                    return mBase.newActivity(targetLoader, guestClassName, guestIntent);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to load with guestLoader; falling back to base: " + e.getMessage());
                }
            }
        }

        return mBase.newActivity(cl, className, intent);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        Intent intent = activity.getIntent();
        if (intent != null && intent.hasExtra(ParcelRewriter.EXTRA_TARGET_INTENT)) {
            String guestPkg = intent.getStringExtra(ParcelRewriter.EXTRA_GUEST_PKG);
            int userId = intent.getIntExtra(ParcelRewriter.EXTRA_USER_ID, 0);
            VirtualMachine vm = IpcRouter.get().route(guestPkg, userId);

            if (vm != null) {
                Log.d(TAG, "callActivityOnCreate: Bound activity " + activity.getClass().getName() + " to VM " + guestPkg);
            }
        }

        mBase.callActivityOnCreate(activity, icicle);
    }

    @Override
    public void callApplicationOnCreate(Application app) {
        mBase.callApplicationOnCreate(app);
    }

    public Instrumentation getBase() {
        return mBase;
    }
}
