package com.example.dualworld.stub;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Pre-declared stub service placeholders for agent worker processes.
 */
public class StubService extends Service {
    private static final String TAG = "StubService";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "StubService invoked in process: " + getClass().getSimpleName());
        return START_NOT_STICKY;
    }

    public static class PS0 extends StubService {}
    public static class PS1 extends StubService {}
    public static class PS2 extends StubService {}
    public static class PS3 extends StubService {}

    // Compatibility aliases
    public static class P0 extends PS0 {}
    public static class P1 extends PS1 {}
    public static class P2 extends PS2 {}
    public static class P3 extends PS3 {}
}
