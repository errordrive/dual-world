package com.example.dualworld.engine.cache;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Diagnostic logger that appends high-priority engine trace events directly to disk
 * (/sdcard/dualworld_launch_diag.txt) for real-device troubleshooting.
 */
public class LaunchDiagnostics {

    public static void log(String tag, String message) {
        Log.i(tag, message);
        try {
            File diagFile = new File("/sdcard/dualworld_launch_diag.txt");
            if (!diagFile.exists()) {
                File extDir = Environment.getExternalStorageDirectory();
                if (extDir != null && extDir.exists()) {
                    diagFile = new File(extDir, "dualworld_launch_diag.txt");
                }
            }

            try (FileWriter fw = new FileWriter(diagFile, true)) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
                fw.write("[" + sdf.format(new Date()) + "] [" + android.os.Process.myPid() + "] [" + tag + "] " + message + "\n");
            }
        } catch (Throwable ignored) {}
    }
}
