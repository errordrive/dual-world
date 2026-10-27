package com.example.dualworld.hook;

import java.lang.reflect.Method;

public abstract class BaseHook {
    private final String mMethodName;

    public BaseHook(String methodName) {
        mMethodName = methodName;
    }

    public String getMethodName() {
        return mMethodName;
    }

    public abstract Object call(Object proxy, Method method, Object[] args) throws Throwable;
}
