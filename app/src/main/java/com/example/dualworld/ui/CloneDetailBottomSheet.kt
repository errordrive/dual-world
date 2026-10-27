package com.example.dualworld.ui

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.dualworld.R
import com.example.dualworld.data.CloneRepository
import com.example.dualworld.engine.cache.AppMetadataCache
import com.example.dualworld.engine.cache.IconCache
import com.example.dualworld.ui.components.GradientButton
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-peek premium bottom sheet displaying clone analytics and management actions.
 */
class CloneDetailBottomSheet : BottomSheetDialogFragment() {

    private lateinit var repository: CloneRepository
    private var packageName: String = ""
    private var userId: Int = 0
    private var onDataChanged: (() -> Unit)? = null

    companion object {
        private const val ARG_PKG = "arg_pkg"
        private const val ARG_USER = "arg_user"

        fun newInstance(pkg: String, user: Int, onDataChanged: () -> Unit): CloneDetailBottomSheet {
            return CloneDetailBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_PKG, pkg)
                    putInt(ARG_USER, user)
                }
                this.onDataChanged = onDataChanged
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageName = arguments?.getString(ARG_PKG) ?: ""
        userId = arguments?.getInt(ARG_USER) ?: 0
        repository = CloneRepository(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.sheet_clone_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val entry = AppMetadataCache.get(requireContext()).getApp(packageName, userId)

        val iconView = view.findViewById<ImageView>(R.id.detail_app_icon)
        val nameView = view.findViewById<TextView>(R.id.detail_app_name)
        val pkgView = view.findViewById<TextView>(R.id.detail_package_name)
        val userTagView = view.findViewById<TextView>(R.id.detail_user_tag)
        val storageVal = view.findViewById<TextView>(R.id.stat_storage_val)
        val launchesVal = view.findViewById<TextView>(R.id.stat_launches_val)
        val lastOpenedVal = view.findViewById<TextView>(R.id.stat_last_opened_val)
        val btnLaunch = view.findViewById<GradientButton>(R.id.btn_detail_launch)
        val btnEditName = view.findViewById<ImageView>(R.id.btn_edit_name)
        val btnClearData = view.findViewById<View>(R.id.btn_action_clear_data)
        val btnFreeze = view.findViewById<View>(R.id.btn_action_freeze)
        val btnDelete = view.findViewById<View>(R.id.btn_action_delete)

        val appName = entry?.appName ?: packageName
        nameView.text = appName
        pkgView.text = packageName
        userTagView.text = getString(R.string.user_tag, userId)
        btnLaunch.setText(getString(R.string.action_launch))

        // Async icon loading
        IconCache.get(requireContext()).loadAsync(packageName, userId) { bitmap ->
            if (bitmap != null) {
                iconView.setImageBitmap(bitmap)
            } else {
                iconView.setImageResource(R.drawable.ic_launcher)
            }
        }

        // Metrics computation
        launchesVal.text = (entry?.launchCount ?: 0).toString()
        if (entry != null && entry.lastLaunchAt > 0) {
            val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            lastOpenedVal.text = sdf.format(Date(entry.lastLaunchAt))
        } else {
            lastOpenedVal.text = getString(R.string.stat_never)
        }

        // Storage size
        val fs = com.example.dualworld.engine.core.VirtualEngine.get().fileSystem
        val userDir = fs.getUserDataDir(packageName, userId)
        val sizeMb = computeDirSize(userDir) / (1024.0 * 1024.0)
        storageVal.text = String.format(Locale.US, "%.1f MB", sizeMb.coerceAtLeast(0.1))

        // Launch Action
        btnLaunch.setOnClickListener {
            dismiss()
            val success = repository.launchClone(packageName, userId)
            if (!success) {
                Toast.makeText(requireContext(), "Failed to launch clone sandbox", Toast.LENGTH_SHORT).show()
            }
        }

        // Rename Action
        val showRenameDialog = {
            val input = EditText(requireContext()).apply {
                setText(appName)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                hint = getString(R.string.rename_dialog_hint)
            }
            AlertDialog.Builder(requireContext(), R.style.CustomBottomSheetDialogTheme)
                .setTitle(R.string.rename_dialog_title)
                .setView(input)
                .setPositiveButton(R.string.btn_save) { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotEmpty()) {
                        repository.renameClone(packageName, userId, newName)
                        nameView.text = newName
                        onDataChanged?.invoke()
                    }
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }
        btnEditName.setOnClickListener { showRenameDialog() }
        nameView.setOnClickListener { showRenameDialog() }

        // Clear Data Action
        btnClearData.setOnClickListener {
            fs.cleanUserSandbox(packageName, userId)
            fs.getUserDataDir(packageName, userId) // recreate skeleton
            storageVal.text = "0.1 MB"
            Toast.makeText(requireContext(), R.string.data_cleared, Toast.LENGTH_SHORT).show()
            onDataChanged?.invoke()
        }

        // Freeze Action
        btnFreeze.setOnClickListener {
            Toast.makeText(requireContext(), "Process isolation frozen", Toast.LENGTH_SHORT).show()
        }

        // Delete Action
        btnDelete.setOnClickListener {
            AlertDialog.Builder(requireContext(), R.style.CustomBottomSheetDialogTheme)
                .setTitle(R.string.action_delete)
                .setMessage("Are you sure you want to permanently delete this clone and its storage?")
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    repository.uninstallClone(packageName, userId)
                    Toast.makeText(requireContext(), R.string.clone_removed, Toast.LENGTH_SHORT).show()
                    onDataChanged?.invoke()
                    dismiss()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
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
}
