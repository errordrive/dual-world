package com.example.dualworld.engine.process;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages allocation and lifecycle leases for the pre-declared worker process pool (:agent0..:agent3).
 */
public class AgentProcessManager {
    private static final String TAG = "AgentProcessManager";
    public static final int MAX_AGENTS = 4;

    private static volatile AgentProcessManager sInstance;

    // agentIndex -> "packageName_u<userId>"
    private final Map<Integer, String> mAgentAllocations = new HashMap<>();

    public static AgentProcessManager get() {
        if (sInstance == null) {
            synchronized (AgentProcessManager.class) {
                if (sInstance == null) {
                    sInstance = new AgentProcessManager();
                }
            }
        }
        return sInstance;
    }

    private AgentProcessManager() {}

    public synchronized int acquireAgent(String packageName, int userId) {
        String key = makeKey(packageName, userId);

        // Check if already assigned
        for (Map.Entry<Integer, String> entry : mAgentAllocations.entrySet()) {
            if (key.equals(entry.getValue())) {
                Log.d(TAG, "Reusing agent " + entry.getKey() + " for " + key);
                return entry.getKey();
            }
        }

        // Find free agent
        for (int i = 0; i < MAX_AGENTS; i++) {
            if (!mAgentAllocations.containsKey(i)) {
                mAgentAllocations.put(i, key);
                Log.i(TAG, "Assigned agent " + i + " to " + key);
                return i;
            }
        }

        // Fallback: hash modulo distribution
        int assigned = Math.abs(key.hashCode()) % MAX_AGENTS;
        mAgentAllocations.put(assigned, key);
        Log.w(TAG, "All agents busy; multiplexing agent " + assigned + " for " + key);
        return assigned;
    }

    public synchronized void releaseAgent(String packageName, int userId) {
        String key = makeKey(packageName, userId);
        Integer toRemove = null;
        for (Map.Entry<Integer, String> entry : mAgentAllocations.entrySet()) {
            if (key.equals(entry.getValue())) {
                toRemove = entry.getKey();
                break;
            }
        }
        if (toRemove != null) {
            mAgentAllocations.remove(toRemove);
            Log.i(TAG, "Released agent " + toRemove + " from " + key);
        }
    }

    public static String getProcessNameForAgent(String hostPackage, int agentIndex) {
        return hostPackage + ":agent" + agentIndex;
    }

    private String makeKey(String packageName, int userId) {
        return packageName + "_u" + userId;
    }
}
