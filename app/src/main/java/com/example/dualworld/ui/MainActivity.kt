package com.example.dualworld.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dualworld.R
import com.example.dualworld.data.CloneRepository
import com.example.dualworld.engine.cache.AppMetadataCache
import com.example.dualworld.engine.cache.IconCache
import com.example.dualworld.ui.components.CacheStatusBanner
import com.example.dualworld.ui.components.CloneCard
import com.example.dualworld.ui.components.GradientButton
import com.example.dualworld.ui.components.SectionHeader
import com.example.dualworld.ui.components.StatusDot

/**
 * Premium dashboard activity featuring live clone metrics, glassy active carousels,
 * and comprehensive sandbox control.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var repository: CloneRepository
    private val clonesList = mutableListOf<AppMetadataCache.AppEntry>()

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

        // All Clones Vertical List
        recyclerAll.layoutManager = LinearLayoutManager(this)
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
    }

    private fun openAppList() {
        startActivity(Intent(this, AppListActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun refreshDashboard() {
        clonesList.clear()
        clonesList.addAll(repository.getAllClones())

        val count = clonesList.size
        badgeCloneCount.text = getString(R.string.badge_clones_count, count)

        if (count == 0) {
            contentContainer.visibility = View.GONE
            emptyClonesState.visibility = View.VISIBLE
        } else {
            contentContainer.visibility = View.VISIBLE
            emptyClonesState.visibility = View.GONE

            activeAdapter.notifyDataSetChanged()
            allAdapter.notifyDataSetChanged()
        }
    }

    private fun showCloneDetail(entry: AppMetadataCache.AppEntry) {
        val sheet = CloneDetailBottomSheet.newInstance(entry.packageName, entry.userId) {
            refreshDashboard()
        }
        sheet.show(supportFragmentManager, "CloneDetailSheet")
    }

    // Horizontal Carousel Adapter (Glassy CloneCard)
    inner class ActiveClonesAdapter : RecyclerView.Adapter<ActiveClonesAdapter.CardViewHolder>() {

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
            val entry = clonesList[position]
            val isRunning = (position == 0) // active simulator

            holder.card.bind(entry.appName ?: entry.packageName, null, isRunning, entry.userId)

            IconCache.get(this@MainActivity).loadAsync(entry.packageName, entry.userId) { bitmap ->
                if (bitmap != null) {
                    holder.card.iconView.setImageBitmap(bitmap)
                } else {
                    holder.card.iconView.setImageResource(R.drawable.ic_launcher)
                }
            }

            holder.card.setOnClickListener {
                showCloneDetail(entry)
            }
        }

        override fun getItemCount(): Int = clonesList.size
    }

    // Vertical List Adapter
    inner class AllClonesAdapter : RecyclerView.Adapter<AllClonesAdapter.RowViewHolder>() {

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
            val entry = clonesList[position]
            holder.txtAppName.text = entry.appName ?: entry.packageName
            holder.txtUserTag.text = getString(R.string.user_tag, entry.userId)
            holder.txtPackageName.text = entry.packageName
            holder.btnLaunch.setText(getString(R.string.action_launch))
            holder.statusDot.setRunning(position == 0)

            IconCache.get(this@MainActivity).loadAsync(entry.packageName, entry.userId) { bitmap ->
                if (bitmap != null) {
                    holder.imgIcon.setImageBitmap(bitmap)
                } else {
                    holder.imgIcon.setImageResource(R.drawable.ic_launcher)
                }
            }

            // Spring motion animation
            holder.itemView.alpha = 0f
            holder.itemView.translationY = 24f
            holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240)
                .setStartDelay((position * 40L).coerceAtMost(320L))
                .start()

            holder.btnLaunch.setOnClickListener {
                val launched = repository.launchClone(entry.packageName, entry.userId)
                if (!launched) {
                    Toast.makeText(this@MainActivity, "Failed to launch clone sandbox", Toast.LENGTH_SHORT).show()
                }
            }

            holder.itemView.setOnClickListener {
                showCloneDetail(entry)
            }
        }

        override fun getItemCount(): Int = clonesList.size
    }
}
