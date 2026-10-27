package com.example.dualworld.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dualworld.R
import com.example.dualworld.data.CloneRepository
import com.example.dualworld.data.InstalledAppItem
import com.example.dualworld.ui.components.GradientButton
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Premium catalog activity with real-time fuzzy search, multi-selection,
 * and bulk clone container initialization.
 */
class AppListActivity : AppCompatActivity() {

    private lateinit var repository: CloneRepository
    private val allApps = mutableListOf<InstalledAppItem>()
    private val filteredApps = mutableListOf<InstalledAppItem>()
    private val selectedPackages = mutableSetOf<String>()

    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptySearchState: View
    private lateinit var bottomActionBar: View
    private lateinit var btnCloneSelected: GradientButton
    private lateinit var editSearch: EditText
    private lateinit var adapter: AppAdapter

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)

        repository = CloneRepository(this)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.recyclerView)
        emptySearchState = findViewById(R.id.empty_search_state)
        bottomActionBar = findViewById(R.id.bottom_action_bar)
        btnCloneSelected = findViewById(R.id.btn_clone_selected)
        editSearch = findViewById(R.id.edit_search)

        adapter = AppAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnCloneSelected.setOnClickListener {
            performBulkClone()
        }

        loadApps()
    }

    private fun loadApps() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        executor.execute {
            val list = repository.getInstalledApps()
            mainHandler.post {
                allApps.clear()
                allApps.addAll(list)
                filterList(editSearch.text.toString())
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun filterList(query: String) {
        val trimmed = query.trim().lowercase(Locale.getDefault())
        filteredApps.clear()

        if (trimmed.isEmpty()) {
            filteredApps.addAll(allApps)
        } else {
            for (app in allApps) {
                if (app.appName.lowercase(Locale.getDefault()).contains(trimmed) ||
                    app.packageName.lowercase(Locale.getDefault()).contains(trimmed)) {
                    filteredApps.add(app)
                }
            }
        }

        adapter.notifyDataSetChanged()
        emptySearchState.visibility = if (filteredApps.isEmpty() && allApps.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateBottomBar() {
        val count = selectedPackages.size
        if (count > 0) {
            bottomActionBar.visibility = View.VISIBLE
            btnCloneSelected.setText(getString(R.string.btn_clone_count, count))
        } else {
            bottomActionBar.visibility = View.GONE
        }
    }

    private fun performBulkClone() {
        val targets = allApps.filter { selectedPackages.contains(it.packageName) }
        if (targets.isEmpty()) return

        progressBar.visibility = View.VISIBLE
        bottomActionBar.visibility = View.GONE

        executor.execute {
            for (target in targets) {
                repository.installClone(target.packageName, target.apkPath, 0)
            }
            mainHandler.post {
                progressBar.visibility = View.GONE
                Toast.makeText(this, R.string.clone_success, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    inner class AppAdapter : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val iconView: ImageView = itemView.findViewById(R.id.imgIcon)
            val titleView: TextView = itemView.findViewById(R.id.txtAppName)
            val subtitleView: TextView = itemView.findViewById(R.id.txtPackageName)
            val checkBox: CheckBox = itemView.findViewById(R.id.check_select)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_available_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = filteredApps[position]
            val isSelected = selectedPackages.contains(app.packageName)

            holder.titleView.text = app.appName
            val sizeMb = app.sizeBytes / (1024.0 * 1024.0)
            holder.subtitleView.text = String.format(Locale.US, "v%s • %.1f MB", app.versionName, sizeMb)
            holder.checkBox.isChecked = isSelected

            // Async / deferred icon retrieval
            try {
                val icon = packageManager.getApplicationIcon(app.packageName)
                holder.iconView.setImageDrawable(icon)
            } catch (e: Exception) {
                holder.iconView.setImageResource(R.drawable.ic_launcher)
            }

            // Staggered motion fade-in
            holder.itemView.alpha = 0f
            holder.itemView.translationY = 24f
            holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240)
                .setStartDelay((position * 30L).coerceAtMost(300L))
                .start()

            holder.itemView.setOnClickListener {
                if (selectedPackages.contains(app.packageName)) {
                    selectedPackages.remove(app.packageName)
                    holder.checkBox.isChecked = false
                } else {
                    selectedPackages.add(app.packageName)
                    holder.checkBox.isChecked = true
                }
                updateBottomBar()
            }
        }

        override fun getItemCount(): Int = filteredApps.size
    }
}
