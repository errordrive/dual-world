package com.example.dualworld.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dualworld.R
import com.example.dualworld.data.CloneRepository
import com.example.dualworld.engine.cache.AppMetadataCache
import com.example.dualworld.engine.cache.IconCache
import com.example.dualworld.engine.core.VirtualEngine
import com.example.dualworld.ui.components.CacheStatusBanner
import com.example.dualworld.ui.components.CloneCard
import com.example.dualworld.ui.components.GradientButton
import com.example.dualworld.ui.components.SectionHeader
import com.example.dualworld.ui.components.StatusDot

/**
 * Premium dashboard activity featuring live clone metrics, glassy active carousels,
 * and comprehensive sandbox control.
 */
class MainActivity : AppCompatActivity(), CloneDetailBottomSheet.OnCloneChangedListener {

    private lateinit var repository: CloneRepository
    private val activeClonesData = mutableListOf<AppMetadataCache.AppEntry>()
    private val allClonesData = mutableListOf<AppMetadataCache.AppEntry>()
    private val runningPackages = mutableSetOf<String>()
    private val animatedPositions = mutableSetOf<Int>()

    private lateinit var badgeCloneCount: TextView
    private lateinit var bannerCacheStatus: CacheStatusBanner
    private lateinit var contentContainer: View
    private lateinit var emptyClonesState: View
    private lateinit var headerActiveClones: SectionHeader
    private lateinit var headerAllClones: SectionHeader
    private lateinit var recyclerActive: RecyclerView
    private lateinit var recyclerAll: RecyclerView
    private lateinit var fabAdd: ImageButton

    private lateinit var activeAdapter: ActiveClonesAdapter
    private lateinit var allAdapter: AllClonesAdapter

    object DIFF_CALLBACK : DiffUtil.ItemCallback<AppMetadataCache.AppEntry>() {
        override fun areItemsTheSame(a: AppMetadataCache.AppEntry, b: AppMetadataCache.AppEntry): Boolean =
            a.packageName == b.packageName && a.userId == b.userId

        override fun areContentsTheSame(a: AppMetadataCache.AppEntry, b: AppMetadataCache.AppEntry): Boolean =
            a.appName == b.appName
                && a.launchCount == b.launchCount
                && a.lastLaunchAt == b.lastLaunchAt
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = CloneRepository(this)

        badgeCloneCount = findViewById(R.id.badge_clone_count)
        bannerCacheStatus = findViewById(R.id.banner_cache_status)
        contentContainer = findViewById(R.id.content_container)
        emptyClonesState = findViewById(R.id.empty_clones_state)
        headerActiveClones = findViewById(R.id.header_active_clones)
        headerAllClones = findViewById(R.id.header_all_clones)
        recyclerActive = findViewById(R.id.recycler_active_clones)
        recyclerAll = findViewById(R.id.recycler_all_clones)
        fabAdd = findViewById(R.id.fab_add_clone)

        // Settings Button
        findViewById<ImageView>(R.id.btn_open_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Section Headers
        headerActiveClones.setTitle(getString(R.string.section_active_clones))
        headerAllClones.setTitle(getString(R.string.section_all_clones))

        // Active Clones Horizontal Carousel
        recyclerActive.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        activeAdapter = ActiveClonesAdapter()
        recyclerActive.adapter = activeAdapter

        // All Clones Vertical List (Inside NestedScrollView)
        recyclerAll.layoutManager = LinearLayoutManager(this)
        recyclerAll.isNestedScrollingEnabled = false
        recyclerAll.setHasFixedSize(false)
        (recyclerAll.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
        allAdapter = AllClonesAdapter()
        recyclerAll.adapter = allAdapter

        // Empty state CTA
        findViewById<GradientButton>(R.id.btn_empty_add).apply {
            setText(getString(R.string.btn_add_app))
            setOnClickListener { openAppList() }
        }

        // Floating Action Button
        fabAdd.setOnClickListener { openAppList() }
        fabAdd.setOnLongClickListener {
            openAppList()
            true
        }

        checkExternalStoragePermission()
    }

    private fun checkExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                ))
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (ignored: Exception) {}
            }
        }
    }

    private fun openAppList() {
        startActivity(Intent(this, AppListActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    override fun onCloneChanged() {
        refreshDashboard()
    }

    private fun refreshDashboard() {
        try {
            animatedPositions.clear()
            val all = repository.getAllClones()

            activeClonesData.clear()
            activeClonesData.addAll(
                all.filter { it.launchCount > 0 }
                    .sortedByDescending { it.lastLaunchAt }
                    .take(10)
            )

            allClonesData.clear()
            allClonesData.addAll(all)

            val count = allClonesData.size
            badgeCloneCount.text = getString(R.string.badge_clones_count, count)

            if (count == 0) {
                contentContainer.visibility = View.GONE
                emptyClonesState.visibility = View.VISIBLE
            } else {
                contentContainer.visibility = View.VISIBLE
                emptyClonesState.visibility = View.GONE

                val hasActive = activeClonesData.isNotEmpty()
                headerActiveClones.visibility = if (hasActive) View.VISIBLE else View.GONE
                recyclerActive.visibility = if (hasActive) View.VISIBLE else View.GONE

                activeAdapter.submitList(activeClonesData.toList())
                allAdapter.submitList(allClonesData.toList())
            }
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Dashboard refresh error", t)
        }
    }

    private fun showCloneDetail(entry: AppMetadataCache.AppEntry) {
        val sheet = CloneDetailBottomSheet.newInstance(entry.packageName, entry.userId)
        sheet.show(supportFragmentManager, "CloneDetailSheet")
    }

    // Horizontal Carousel Adapter (Glassy CloneCard)
    inner class ActiveClonesAdapter :
        ListAdapter<AppMetadataCache.AppEntry, ActiveClonesAdapter.CardViewHolder>(DIFF_CALLBACK) {

        inner class CardViewHolder(val card: CloneCard) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
            val card = CloneCard(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    (110 * resources.displayMetrics.density).toInt(),
                    (130 * resources.displayMetrics.density).toInt()
                ).apply {
                    marginEnd = (12 * resources.displayMetrics.density).toInt()
                }
            }
            return CardViewHolder(card)
        }

        override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
            val entry = getItem(position)
            val isRunning = runningPackages.contains(entry.packageName)

            holder.card.bind(entry.appName ?: entry.packageName, null, isRunning, entry.userId)

            holder.card.iconView.tag = entry.packageName
            IconCache.get(this@MainActivity).loadAsync(entry.packageName, entry.userId) { bitmap ->
                if (holder.card.iconView.tag == entry.packageName) {
                    if (bitmap != null) {
                        holder.card.iconView.setImageBitmap(bitmap)
                    } else {
                        holder.card.iconView.setImageResource(R.drawable.ic_launcher)
                    }
                }
            }

            holder.card.setOnClickListener {
                showCloneDetail(entry)
            }
        }
    }

    // Vertical List Adapter
    inner class AllClonesAdapter :
        ListAdapter<AppMetadataCache.AppEntry, AllClonesAdapter.RowViewHolder>(DIFF_CALLBACK) {

        inner class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imgIcon: ImageView = itemView.findViewById(R.id.imgIcon)
            val statusDot: StatusDot = itemView.findViewById(R.id.statusDot)
            val txtAppName: TextView = itemView.findViewById(R.id.txtAppName)
            val txtUserTag: TextView = itemView.findViewById(R.id.txtUserTag)
            val txtPackageName: TextView = itemView.findViewById(R.id.txtPackageName)
            val btnLaunch: GradientButton = itemView.findViewById(R.id.btnLaunch)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cloned_app, parent, false)
            return RowViewHolder(view)
        }

        override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
            val entry = getItem(position)
            val isRunning = runningPackages.contains(entry.packageName)

            holder.txtAppName.text = entry.appName ?: entry.packageName
            holder.txtUserTag.text = getString(R.string.user_tag, entry.userId)
            holder.txtPackageName.text = entry.packageName
            holder.btnLaunch.setText(getString(R.string.action_launch))
            holder.statusDot.setRunning(isRunning)

            holder.imgIcon.tag = entry.packageName
            IconCache.get(this@MainActivity).loadAsync(entry.packageName, entry.userId) { bitmap ->
                if (holder.imgIcon.tag == entry.packageName) {
                    if (bitmap != null) {
                        holder.imgIcon.setImageBitmap(bitmap)
                    } else {
                        holder.imgIcon.setImageResource(R.drawable.ic_launcher)
                    }
                }
            }

            // Stagger motion animation only for unseen items
            if (!animatedPositions.contains(position)) {
                animatedPositions.add(position)
                holder.itemView.alpha = 0f
                holder.itemView.translationY = 24f
                holder.itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(240)
                    .setStartDelay((position * 40L).coerceAtMost(320L))
                    .start()
            } else {
                holder.itemView.alpha = 1f
                holder.itemView.translationY = 0f
            }

            holder.btnLaunch.setOnClickListener {
                holder.btnLaunch.isEnabled = false
                holder.btnLaunch.setText(getString(R.string.state_launching))
                VirtualEngine.get().launchCloneAsync(this@MainActivity, entry.packageName, entry.userId) { result ->
                    holder.btnLaunch.isEnabled = true
                    holder.btnLaunch.setText(getString(R.string.action_launch))
                    if (result == VirtualEngine.LaunchResult.SUCCESS) {
                        runningPackages.add(entry.packageName)
                        activeAdapter.notifyDataSetChanged()
                        allAdapter.notifyDataSetChanged()
                    } else {
                        Toast.makeText(this@MainActivity, result.toUserMessage(), Toast.LENGTH_LONG).show()
                    }
                }
            }

            holder.itemView.setOnClickListener {
                showCloneDetail(entry)
            }
        }
    }
}
