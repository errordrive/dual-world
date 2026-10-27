package com.example.dualworld.engine.ipc;

import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * Locates and replaces cached system service Binder proxies (AMS, PMS, ATM)
 * inside ServiceManager.sCache and framework Singletons.
 */
public class BinderProxyManager {
    private static final String TAG = "BinderProxyManager";

    public interface MethodInterceptor {
        Object onIntercept(Object proxy, Method method, Object[] args, Object original) throws Throwable;
    }

    public static void injectAll(Context context, MethodInterceptor amsInterceptor, MethodInterceptor pmsInterceptor) {
        try {
            injectActivityManager(context, amsInterceptor);
            injectPackageManager(context, pmsInterceptor);
            injectServiceManagerCache(context, "package", pmsInterceptor);
            injectServiceManagerCache(context, "activity", amsInterceptor);
            Log.i(TAG, "Successfully injected Binder proxies into runtime");
        } catch (Throwable t) {
            Log.w(TAG, "Binder proxy injection encountered non-fatal reflection warning: " + t.getMessage());
        }
    }

    public static void injectActivityManager(Context context, MethodInterceptor interceptor) {
        try {
            Class<?> amClass = Class.forName("android.app.ActivityManager");
            Field singletonField = null;
            try {
                singletonField = amClass.getDeclaredField("IActivityManagerSingleton");
            } catch (NoSuchFieldException e) {
                try {
                    Class<?> atmClass = Class.forName("android.app.ActivityTaskManager");
                    singletonField = atmClass.getDeclaredField("IActivityTaskManagerSingleton");
                } catch (Exception ignored) {}
            }

            if (singletonField != null) {
                singletonField.setAccessible(true);
                Object singleton = singletonField.get(null);
                if (singleton != null) {
                    Class<?> singletonClass = Class.forName("android.util.Singleton");
                    Field mInstanceField = singletonClass.getDeclaredField("mInstance");
                    mInstanceField.setAccessible(true);
                    Method getMethod = singletonClass.getMethod("get");
                    Object originalAm = getMethod.invoke(singleton);

                    if (originalAm != null) {
                        Class<?> iAmInterface = Class.forName("android.app.IActivityManager");
                        Object proxy = createProxy(context.getClassLoader(), iAmInterface, originalAm, interceptor);
                        mInstanceField.set(singleton, proxy);
                        Log.d(TAG, "Injected IActivityManager proxy into Singleton");
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not inject IActivityManager Singleton: " + t.getMessage());
        }
    }

    public static void injectPackageManager(Context context, MethodInterceptor interceptor) {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method currentAtMethod = atClass.getDeclaredMethod("currentActivityThread");
            currentAtMethod.setAccessible(true);
            Object activityThread = currentAtMethod.invoke(null);

            Field sPmField = atClass.getDeclaredField("sPackageManager");
            sPmField.setAccessible(true);
            Object originalPm = sPmField.get(null);

            if (originalPm == null && activityThread != null) {
                Method getPm = atClass.getDeclaredMethod("getPackageManager");
                getPm.setAccessible(true);
                originalPm = getPm.invoke(activityThread);
            }

            if (originalPm != null) {
                Class<?> iPmInterface = Class.forName("android.content.pm.IPackageManager");
                Object proxy = createProxy(context.getClassLoader(), iPmInterface, originalPm, interceptor);
                sPmField.set(null, proxy);
                Log.d(TAG, "Injected IPackageManager proxy into ActivityThread");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not inject IPackageManager: " + t.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public static void injectServiceManagerCache(Context context, String serviceName, MethodInterceptor interceptor) {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Field sCacheField = smClass.getDeclaredField("sCache");
            sCacheField.setAccessible(true);
            Map<String, IBinder> sCache = (Map<String, IBinder>) sCacheField.get(null);

            if (sCache != null && sCache.containsKey(serviceName)) {
                IBinder originalBinder = sCache.get(serviceName);
                if (originalBinder != null) {
                    IBinder proxyBinder = (IBinder) Proxy.newProxyInstance(
                            context.getClassLoader(),
                            new Class<?>[]{IBinder.class},
                            (proxy, method, args) -> {
                                if ("queryLocalInterface".equals(method.getName())) {
                                    IInterface originalIface = originalBinder.queryLocalInterface((String) args[0]);
                                    if (originalIface != null) {
                                        return createProxy(context.getClassLoader(), originalIface.getClass().getInterfaces()[0], originalIface, interceptor);
                                    }
                                }
                                return method.invoke(originalBinder, args);
                            }
                    );
                    sCache.put(serviceName, proxyBinder);
                    Log.d(TAG, "Injected ServiceManager.sCache proxy for " + serviceName);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not hook ServiceManager.sCache for " + serviceName + ": " + t.getMessage());
        }
    }

    private static Object createProxy(ClassLoader cl, Class<?> iface, Object original, MethodInterceptor interceptor) {
        return Proxy.newProxyInstance(
                cl,
                new Class<?>[]{iface},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (interceptor != null) {
                            return interceptor.onIntercept(proxy, method, args, original);
                        }
                        return method.invoke(original, args);
                    }
                }
        );
    }
}
