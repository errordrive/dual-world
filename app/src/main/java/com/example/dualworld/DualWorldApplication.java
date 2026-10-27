package com.example.dualworld;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.example.dualworld.core.VirtualCore;
import com.example.dualworld.engine.core.VirtualEngine;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Main Application class for Dual World.
 * Hardened with LSPosed HiddenApiBypass, global crash logging, and graceful degradation.
 */
public class DualWorldApplication extends Application {
    private static final String TAG = "DualWorldApplication";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        // 1. Bypass Android 9+ hidden API restrictions immediately
        installHiddenApiBypass();

        // 2. Install persistent uncaught exception handler
        installCrashHandler(base);

        Log.i(TAG, "attachBaseContext() process=" + getProcessName());

        // 3. Early bootstrap with try/catch guard
        try {
            VirtualEngine.get().init(this);
        } catch (Throwable t) {
            Log.e(TAG, "VirtualEngine early init warning", t);
        }

        try {
            VirtualCore.get().attachBaseContext(this);
        } catch (Throwable t) {
            Log.e(TAG, "VirtualCore early init warning", t);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate() process=" + getProcessName());

        try {
            VirtualEngine.get().init(this);
        } catch (Throwable t) {
            Log.e(TAG, "VirtualEngine onCreate init warning", t);
        }

        try {
            VirtualCore.get().initialize(this);
        } catch (Throwable t) {
            Log.e(TAG, "VirtualCore onCreate init warning", t);
        }
    }

    private void installHiddenApiBypass() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("");
                Log.i(TAG, "HiddenApiBypass successfully exempted all hidden APIs");
            } catch (Throwable t) {
                Log.w(TAG, "HiddenApiBypass installation failed: " + t.getMessage());
            }
        }
    }

    private void installCrashHandler(Context base) {
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                writeCrashToFile(base, thread, throwable);
            } catch (Throwable ignored) {}

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    private void writeCrashToFile(Context context, Thread thread, Throwable throwable) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            String stackTrace = sw.toString();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
            String timestamp = sdf.format(new Date());

            String crashReport = "========================================================\n" +
                    "FATAL CRASH: " + timestamp + "\n" +
                    "Process: " + getProcessName() + " (PID: " + android.os.Process.myPid() + ")\n" +
                    "Thread: " + thread.getName() + " (" + thread.getId() + ")\n" +
                    "Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ", SDK " + Build.VERSION.SDK_INT + ")\n" +
                    "========================================================\n" +
                    stackTrace + "\n\n";

            Log.e(TAG, "UNCAUGHT FATAL EXCEPTION:\n" + crashReport);

            // 1. Write to app private directory
            try {
                File privateDir = context.getFilesDir();
                if (privateDir != null) {
                    File crashFile = new File(privateDir, "dualworld_crash.txt");
                    try (FileWriter fw = new FileWriter(crashFile, true)) {
                        fw.write(crashReport);
                    }
                }
            } catch (Throwable ignored) {}

            // 2. Write to public storage (/sdcard/dualworld_crash.txt)
            try {
                File sdcardFile = new File(Environment.getExternalStorageDirectory(), "dualworld_crash.txt");
                try (FileWriter fw = new FileWriter(sdcardFile, true)) {
                    fw.write(crashReport);
                }
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            Log.e(TAG, "Failed to write crash report", t);
        }
    }
}
