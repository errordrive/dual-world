package com.example.dualworld.hook;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class InvocationHandlerProxy implements InvocationHandler {
    private final Object mBase;
    private final Map<String, BaseHook> mHookMap = new HashMap<>();

    public InvocationHandlerProxy(Object base) {
        mBase = base;
    }

    public void addHook(BaseHook hook) {
        mHookMap.put(hook.getMethodName(), hook);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        BaseHook hook = mHookMap.get(method.getName());
        if (hook != null) {
            return hook.call(mBase, method, args);
        }
        return method.invoke(mBase, args);
    }
}
