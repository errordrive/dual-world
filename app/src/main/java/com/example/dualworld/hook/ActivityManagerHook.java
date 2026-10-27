package com.example.dualworld.hook;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.dualworld.client.VirtualClient;

import java.lang.reflect.Method;

public class ActivityManagerHook {
    private static final String TAG = "ActivityManagerHook";

    public static class StartActivityHook extends BaseHook {
        private final Context mContext;

        public StartActivityHook(Context context) {
            super("startActivity");
            mContext = context;
        }

        @Override
        public Object call(Object base, Method method, Object[] args) throws Throwable {
            int intentIndex = -1;
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Intent) {
                    intentIndex = i;
                    break;
                }
            }

            if (intentIndex != -1) {
                Intent targetIntent = (Intent) args[intentIndex];
                String guestPkg = VirtualClient.get().getCurrentPackageName();
                int userId = VirtualClient.get().getCurrentUserId();

                Log.i(TAG, "Intercepted startActivity: " + targetIntent);

                int agentIndex = VirtualClient.get().getAgentIndex();
                String stubClass = "com.example.dualworld.stub.StubActivity$P" + agentIndex;

                Intent stubIntent = new Intent();
                stubIntent.setComponent(new ComponentName(mContext.getPackageName(), stubClass));
                stubIntent.putExtra("_v_target_intent_", targetIntent);
                stubIntent.putExtra("_v_pkg_", guestPkg);
                stubIntent.putExtra("_v_user_id_", userId);

                args[intentIndex] = stubIntent;
            }

            return method.invoke(base, args);
        }
    }

    public static class GetPackageNameHook extends BaseHook {
        public GetPackageNameHook() {
            super("getCallingPackage");
        }

        @Override
        public Object call(Object base, Method method, Object[] args) throws Throwable {
            String guestPkg = VirtualClient.get().getCurrentPackageName();
            if (guestPkg != null) {
                return guestPkg;
            }
            return method.invoke(base, args);
        }
    }
}
