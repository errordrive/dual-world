package com.example.dualworld.stub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Pre-declared stub broadcast receiver for virtualized intent routing.
 */
public class StubReceiver extends BroadcastReceiver {
    private static final String TAG = "StubReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "StubReceiver received broadcast: " + (intent != null ? intent.getAction() : "null"));
    }
}
