package com.example.dualworld.engine.process;

import android.content.ComponentName;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Coordinates assignment and recycling of pre-declared manifest stub components
 * (Activities, Services, Providers) across the agent worker processes.
 */
public class StubCoordinator {
    private static final String TAG = "StubCoordinator";

    public static final int STUB_ACTIVITIES_PER_AGENT = 4;
    private static volatile StubCoordinator sInstance;

    // "packageName_u<userId>_activityClassName" -> stubClassName
    private final Map<String, String> mActiveActivityStubs = new HashMap<>();

    public static StubCoordinator get() {
        if (sInstance == null) {
            synchronized (StubCoordinator.class) {
                if (sInstance == null) {
                    sInstance = new StubCoordinator();
                }
            }
        }
        return sInstance;
    }

    private StubCoordinator() {}

    public synchronized ComponentName allocateActivityStub(String hostPackage, int agentIndex, String guestPackage, int userId, String guestActivityClass) {
        String key = makeKey(guestPackage, userId, guestActivityClass);
        if (mActiveActivityStubs.containsKey(key)) {
            String stubClass = mActiveActivityStubs.get(key);
            return new ComponentName(hostPackage, stubClass);
        }

        // Allocate stub index 0..3 within this agent
        int stubIndex = Math.abs(guestActivityClass.hashCode()) % STUB_ACTIVITIES_PER_AGENT;
        String stubClass = "com.example.dualworld.stub.StubActivity$P" + agentIndex + "A" + stubIndex;

        mActiveActivityStubs.put(key, stubClass);
        Log.i(TAG, "Allocated stub " + stubClass + " for guest " + guestActivityClass);
        return new ComponentName(hostPackage, stubClass);
    }

    public synchronized void releaseActivityStub(String guestPackage, int userId, String guestActivityClass) {
        String key = makeKey(guestPackage, userId, guestActivityClass);
        String released = mActiveActivityStubs.remove(key);
        if (released != null) {
            Log.d(TAG, "Released stub " + released + " for " + key);
        }
    }

    public ComponentName getServiceStub(String hostPackage, int agentIndex) {
        String stubClass = "com.example.dualworld.stub.StubService$PS" + agentIndex;
        return new ComponentName(hostPackage, stubClass);
    }

    public ComponentName getProviderStub(String hostPackage, int providerIndex) {
        String stubClass = "com.example.dualworld.stub.StubContentProvider$CP" + providerIndex;
        return new ComponentName(hostPackage, stubClass);
    }

    public ComponentName getReceiverStub(String hostPackage) {
        return new ComponentName(hostPackage, "com.example.dualworld.stub.StubReceiver");
    }

    private String makeKey(String guestPackage, int userId, String guestActivityClass) {
        return guestPackage + "_u" + userId + "_" + guestActivityClass;
    }
}
