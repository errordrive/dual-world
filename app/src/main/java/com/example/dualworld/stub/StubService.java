package com.example.dualworld.stub;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class StubService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static class P0 extends StubService {}
    public static class P1 extends StubService {}
    public static class P2 extends StubService {}
    public static class P3 extends StubService {}
}
