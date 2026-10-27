package com.example.dualworld.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dualworld.R
import com.example.dualworld.engine.cache.IconCache
import com.example.dualworld.engine.core.VirtualEngine
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Premium settings activity allowing cache purges, sandbox developer inspections,
 * and engine configuration.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var textCacheSize: TextView
    private lateinit var cacheProgress: ProgressBar
    private lateinit var btnClearCache: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageView>(R.id.btn_settings_back).setOnClickListener {
            finish()
        }

        textCacheSize = findViewById(R.id.text_cache_size)
        cacheProgress = findViewById(R.id.cache_progress)
        btnClearCache = findViewById(R.id.btn_clear_cache)

        updateCacheSize()

        btnClearCache.setOnClickListener {
            cacheProgress.visibility = View.VISIBLE
            btnClearCache.isEnabled = false

            executor.execute {
                IconCache.get(this).clearAll()
                // Purge internal cache dir
                deleteDirContents(cacheDir)
                Thread.sleep(600) // smooth feedback
                mainHandler.post {
                    cacheProgress.visibility = View.GONE
                    btnClearCache.isEnabled = true
                    Toast.makeText(this, R.string.cache_cleared_toast, Toast.LENGTH_SHORT).show()
                    updateCacheSize()
                }
            }
        }
    }

    private fun updateCacheSize() {
        executor.execute {
            val iconBytes = IconCache.get(this).diskUsageBytes
            val cacheBytes = computeDirSize(cacheDir)
            val totalMb = (iconBytes + cacheBytes) / (1024.0 * 1024.0)
            val formatted = String.format(Locale.US, "%.2f MB used", totalMb)
            mainHandler.post {
                textCacheSize.text = formatted
            }
        }
    }

    private fun computeDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        var total: Long = 0
        val files = dir.listFiles()
        if (files != null) {
            for (f in files) {
                total += if (f.isDirectory) computeDirSize(f) else f.length()
            }
        }
        return total
    }

    private fun deleteDirContents(dir: File?) {
        if (dir == null || !dir.exists()) return
        val files = dir.listFiles()
        if (files != null) {
            for (f in files) {
                if (f.isDirectory) {
                    deleteDirContents(f)
                }
                f.delete()
            }
        }
    }
}
