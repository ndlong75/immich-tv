package com.immichtv.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.immichtv.R
import com.immichtv.api.*
import com.immichtv.ui.presenters.CardPresenter
import com.immichtv.ui.presenters.IconCardPresenter
import com.immichtv.util.PrefsManager
import kotlinx.coroutines.launch

class HomeFragment : BrowseSupportFragment() {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        setupEventListeners()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        adapter = rowsAdapter
        loadData()
    }

    private fun setupUI() {
        title = "Immich Photos"
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = Color.parseColor("#1E40AF")
        searchAffordanceColor = Color.parseColor("#3B82F6")

        setOnSearchClickedListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
    }

    private fun setupEventListeners() {
        // Clicking ANY item in a row opens the GRID view for that category
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, row ->
            val headerTitle = (row as? ListRow)?.headerItem?.name ?: ""

            when {
                // Photos row → open full photos grid
                headerTitle.contains("Photos") && item is Asset ->
                    openGrid(BrowseGridActivity.MODE_PHOTOS, "", "Photos")

                // Memories row → open memory grid
                item is MemoryCard ->
                    openGrid(BrowseGridActivity.MODE_MEMORY, item.id, item.title)

                // Albums row → open album grid
                item is AlbumSimple ->
                    openGrid(BrowseGridActivity.MODE_ALBUM, item.id, item.albumName)

                // People row → open person grid
                item is Person ->
                    openGrid(BrowseGridActivity.MODE_PERSON, item.id, item.name)

                // Random row → open random grid
                headerTitle.contains("Random") && item is Asset ->
                    openGrid(BrowseGridActivity.MODE_RANDOM, "", "Random Photos")

                // Navigate row items
                item is NavItem -> handleNavItem(item)

                // Settings
                item is SettingsItem -> openSettings()
            }
        }
    }

    fun refreshData() {
        if (PrefsManager.isConfigured()) {
            rowsAdapter.clear()
            loadData()
        }
    }

    private fun loadData() {
        if (!PrefsManager.isConfigured()) return

        try {
            ImmichClient.configure(PrefsManager.serverUrl, PrefsManager.accessToken)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to connect: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        loadLatestPhotos()
        loadMemories()
        loadAlbums()
        loadPeople()
        loadRandomPhotos()
        addNavigationRow()
        addSettingsRow()
    }

    // ── Data Loading ────────────────────────────────────────────────────

    private fun loadLatestPhotos() {
        lifecycleScope.launch {
            try {
                val response = ImmichClient.getApi().searchAssets(
                    SearchRequest(size = 50, order = "desc")
                )
                val assets = response.assets.items
                if (assets.isNotEmpty()) {
                    val listAdapter = ArrayObjectAdapter(CardPresenter(ImmichClient.baseUrl))
                    assets.forEach { listAdapter.add(it) }
                    rowsAdapter.add(0, ListRow(HeaderItem("\uD83D\uDCF8 Photos"), listAdapter))
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadMemories() {
        lifecycleScope.launch {
            try {
                val memories = ImmichClient.getApi().getMemories()
                if (memories.isNotEmpty()) {
                    val listAdapter = ArrayObjectAdapter(CardPresenter(ImmichClient.baseUrl))
                    memories.forEach { memory ->
                        val title = if (memory.data.year != null)
                            "${memory.data.year} \u2014 ${memory.assets.size} photos"
                        else "Memory \u2014 ${memory.assets.size} photos"
                        listAdapter.add(MemoryCard(
                            id = memory.id, title = title,
                            thumbnailAssetId = memory.assets.firstOrNull()?.id,
                            assets = memory.assets
                        ))
                    }
                    rowsAdapter.add(ListRow(HeaderItem("\uD83D\uDCAD On This Day"), listAdapter))
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadAlbums() {
        lifecycleScope.launch {
            try {
                val albums = ImmichClient.getApi().getAlbums()
                if (albums.isNotEmpty()) {
                    val listAdapter = ArrayObjectAdapter(CardPresenter(ImmichClient.baseUrl))
                    albums.sortedByDescending { it.updatedAt }.forEach { listAdapter.add(it) }
                    rowsAdapter.add(ListRow(HeaderItem("\uD83D\uDCF7 Albums"), listAdapter))
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load albums", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadPeople() {
        lifecycleScope.launch {
            try {
                val response = ImmichClient.getApi().getPeople()
                val people = response.people.filter { it.name.isNotBlank() }
                if (people.isNotEmpty()) {
                    val listAdapter = ArrayObjectAdapter(CardPresenter(ImmichClient.baseUrl))
                    people.sortedByDescending { it.assetCount }.forEach { listAdapter.add(it) }
                    rowsAdapter.add(ListRow(HeaderItem("\uD83D\uDC64 People"), listAdapter))
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadRandomPhotos() {
        lifecycleScope.launch {
            try {
                val assets = ImmichClient.getApi().getRandomAssets(30)
                if (assets.isNotEmpty()) {
                    val listAdapter = ArrayObjectAdapter(CardPresenter(ImmichClient.baseUrl))
                    assets.forEach { listAdapter.add(it) }
                    rowsAdapter.add(ListRow(HeaderItem("\uD83C\uDFB2 Random Photos"), listAdapter))
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load photos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addNavigationRow() {
        val listAdapter = ArrayObjectAdapter(IconCardPresenter())
        listAdapter.add(NavItem("search", "\uD83D\uDD0D Search", "Smart AI search"))
        listAdapter.add(NavItem("explore", "\uD83C\uDF0D Explore", "Places and things"))
        listAdapter.add(NavItem("locations", "\uD83D\uDCCD Locations", "Photos by location"))
        listAdapter.add(NavItem("timeline", "\uD83D\uDCC5 Timeline", "All photos by date"))
        rowsAdapter.add(ListRow(HeaderItem("\uD83D\uDCCB Browse"), listAdapter))
    }

    private fun addSettingsRow() {
        val listAdapter = ArrayObjectAdapter(IconCardPresenter())
        listAdapter.add(SettingsItem("\u2699\uFE0F Server Settings", "Configure connection"))
        rowsAdapter.add(ListRow(HeaderItem("\u2699\uFE0F Settings"), listAdapter))
    }

    // ── Navigation ──────────────────────────────────────────────────────

    private fun openGrid(mode: String, id: String, title: String) {
        startActivity(Intent(requireContext(), BrowseGridActivity::class.java).apply {
            putExtra(BrowseGridActivity.EXTRA_MODE, mode)
            putExtra(BrowseGridActivity.EXTRA_ID, id)
            putExtra(BrowseGridActivity.EXTRA_TITLE, title)
        })
    }

    private fun handleNavItem(item: NavItem) {
        when (item.action) {
            "search" -> startActivity(Intent(requireContext(), SearchActivity::class.java))
            "explore" -> startActivity(Intent(requireContext(), ExploreActivity::class.java))
            "locations" -> startActivity(Intent(requireContext(), MapActivity::class.java))
            "timeline" -> openGrid(BrowseGridActivity.MODE_TIMELINE, "", "Timeline")
        }
    }

    private fun openSettings() {
        startActivity(Intent(requireContext(), SettingsActivity::class.java))
    }
}

// ── Data classes ────────────────────────────────────────────────────────────

data class MemoryCard(
    val id: String,
    val title: String,
    val thumbnailAssetId: String?,
    val assets: List<Asset>
)

data class SettingsItem(
    val title: String,
    val description: String
)

data class NavItem(
    val action: String,
    val title: String,
    val description: String
)
