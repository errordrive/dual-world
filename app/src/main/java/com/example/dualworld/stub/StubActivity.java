package com.example.dualworld.stub;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.example.dualworld.client.VirtualClient;

/**
 * Pre-declared stub activity multiplexer for Dual World.
 */
public class StubActivity extends Activity {
    private static final String TAG = "StubActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        Intent targetIntent = intent.getParcelableExtra("_v_target_intent_");
        String targetPkg = intent.getStringExtra("_v_pkg_");
        int userId = intent.getIntExtra("_v_user_id_", 0);

        Log.i(TAG, "StubActivity created: " + getClass().getSimpleName() + " target: " + targetPkg + " user: " + userId);

        if (targetIntent != null && targetPkg != null) {
            VirtualClient.get().setCurrentApp(targetPkg, userId);
            try {
                targetIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                startActivity(targetIntent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch target activity", e);
                Toast.makeText(this, "Unable to launch cloned app: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        finish();
    }

    public static class P0 extends StubActivity {}
    public static class P1 extends StubActivity {}
    public static class P2 extends StubActivity {}
    public static class P3 extends StubActivity {}
}
