package com.example.dualworld.stub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.example.dualworld.engine.core.VirtualEngine;
import com.example.dualworld.engine.ipc.ParcelRewriter;

/**
 * Pre-declared stub activity placeholders for worker agent processes (:agent0..:agent3).
 * Intercepted at runtime by ActivityThreadInterceptor and InstrumentationProxy.
 */
public class StubActivity extends Activity {
    private static final String TAG = "StubActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(ParcelRewriter.EXTRA_GUEST_PKG)) {
            String guestPkg = intent.getStringExtra(ParcelRewriter.EXTRA_GUEST_PKG);
            int userId = intent.getIntExtra(ParcelRewriter.EXTRA_USER_ID, 0);
            Log.i(TAG, "StubActivity created for guest package: " + guestPkg + " (user " + userId + ")");

            // The stub activity was reached directly; interception failed
            new AlertDialog.Builder(this)
                    .setTitle("Launch Failed")
                    .setMessage("Could not start \"" + guestPkg
                            + "\".\nThis device may restrict "
                            + "app virtualization.")
                    .setPositiveButton("OK", (d, w) -> finish())
                    .setCancelable(false)
                    .show();
        }
    }

    // Agent 0 Stub Activities
    public static class P0A0 extends StubActivity {}
    public static class P0A1 extends StubActivity {}
    public static class P0A2 extends StubActivity {}
    public static class P0A3 extends StubActivity {}

    // Agent 1 Stub Activities
    public static class P1A0 extends StubActivity {}
    public static class P1A1 extends StubActivity {}
    public static class P1A2 extends StubActivity {}
    public static class P1A3 extends StubActivity {}

    // Agent 2 Stub Activities
    public static class P2A0 extends StubActivity {}
    public static class P2A1 extends StubActivity {}
    public static class P2A2 extends StubActivity {}
    public static class P2A3 extends StubActivity {}

    // Agent 3 Stub Activities
    public static class P3A0 extends StubActivity {}
    public static class P3A1 extends StubActivity {}
    public static class P3A2 extends StubActivity {}
    public static class P3A3 extends StubActivity {}

    // Legacy compatibility aliases
    public static class P0 extends P0A0 {}
    public static class P1 extends P1A0 {}
    public static class P2 extends P2A0 {}
    public static class P3 extends P3A0 {}
}
