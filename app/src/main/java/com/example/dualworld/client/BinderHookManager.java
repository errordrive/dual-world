package com.example.dualworld.client;

import android.content.Context;
import android.util.Log;

import com.example.dualworld.hook.ActivityManagerHook;
import com.example.dualworld.hook.InvocationHandlerProxy;
import com.example.dualworld.hook.PackageManagerHook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class BinderHookManager {
    private static final String TAG = "BinderHookManager";

    public static void installHooks(Context context) {
        try {
            hookActivityManager(context);
            hookPackageManager(context);
            Log.i(TAG, "All core system service hooks successfully installed in agent process");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install Binder hooks", t);
        }
    }

    private static void hookActivityManager(Context context) throws Exception {
        Class<?> amClass = Class.forName("android.app.ActivityManager");
        Field amSingletonField;
        try {
            amSingletonField = amClass.getDeclaredField("IActivityManagerSingleton");
        } catch (NoSuchFieldException e) {
            Class<?> atmClass = Class.forName("android.app.ActivityTaskManager");
            amSingletonField = atmClass.getDeclaredField("IActivityTaskManagerSingleton");
        }
        amSingletonField.setAccessible(true);
        Object singleton = amSingletonField.get(null);

        Class<?> singletonClass = Class.forName("android.util.Singleton");
        Field mInstanceField = singletonClass.getDeclaredField("mInstance");
        mInstanceField.setAccessible(true);

        Method getMethod = singletonClass.getMethod("get");
        Object originalAm = getMethod.invoke(singleton);

        if (originalAm != null) {
            Class<?> iAmInterface = Class.forName("android.app.IActivityManager");
            InvocationHandlerProxy handler = new InvocationHandlerProxy(originalAm);
            handler.addHook(new ActivityManagerHook.StartActivityHook(context));
            handler.addHook(new ActivityManagerHook.GetPackageNameHook());

            Object proxy = Proxy.newProxyInstance(
                    context.getClassLoader(),
                    new Class<?>[]{iAmInterface},
                    handler
            );

            mInstanceField.set(singleton, proxy);
            Log.d(TAG, "Successfully hooked IActivityManager");
        }
    }

    private static void hookPackageManager(Context context) throws Exception {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
        currentActivityThreadMethod.setAccessible(true);
        Object currentActivityThread = currentActivityThreadMethod.invoke(null);

        Field sPackageManagerField = activityThreadClass.getDeclaredField("sPackageManager");
        sPackageManagerField.setAccessible(true);
        Object originalPm = sPackageManagerField.get(null);

        if (originalPm == null) {
            Method getPackageManagerMethod = activityThreadClass.getDeclaredMethod("getPackageManager");
            getPackageManagerMethod.setAccessible(true);
            originalPm = getPackageManagerMethod.invoke(currentActivityThread);
        }

        if (originalPm != null) {
            Class<?> iPmInterface = Class.forName("android.content.pm.IPackageManager");
            InvocationHandlerProxy handler = new InvocationHandlerProxy(originalPm);
            handler.addHook(new PackageManagerHook.GetPackageInfoHook(context));
            handler.addHook(new PackageManagerHook.GetApplicationInfoHook());

            Object proxy = Proxy.newProxyInstance(
                    context.getClassLoader(),
                    new Class<?>[]{iPmInterface},
                    handler
            );

            sPackageManagerField.set(null, proxy);
            Log.d(TAG, "Successfully hooked IPackageManager");
        }
    }
}
