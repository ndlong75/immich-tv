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
        // Click: navigate into content
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, row ->
            when (item) {
                is NavItem -> handleNavItem(item)
                is AlbumSimple -> openAlbum(item)
                is Person -> openPerson(item)
                is Asset -> openAsset(item)
                is MemoryCard -> openMemory(item)
            }
        }

        // Long-press: edit person (set name, birthday, photo)
        setOnItemViewSelectedListener(OnItemViewSelectedListener { _, item, _, _ ->
            // We'll use the MENU key instead for edit — see onKeyDown below
        })
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

        // Each sidebar row shows a PREVIEW strip; clicking any item opens full grid
        addPhotosPreview()
        addMemoriesPreview()
        addAlbumsPreview()
        addPeoplePreview()
        addRandomPreview()
        addBrowseRow()
        addSettingsRow()
    }

    private fun addPhotosPreview() {
        lifecycleScope.launch {
            try {
                val response = ImmichClient.getApi().searchAssets(
                    SearchRequest(size = 30, order = "desc")
                )
                val assets = response.assets.items
                if (assets.isNotEmpty()) {
                    val listAdapter = ArrayObjectAdapter(CardPresenter(ImmichClient.baseUrl))
                    // First item is a "View All" nav card
                    listAdapter.add(NavItem("photos", "\u25A6 View All Photos", "Open full grid"))
                    assets.forEach { listAdapter.add(it) }
                    rowsAdapter.add(ListRow(HeaderItem("\uD83D\uDCF8 Photos"), listAdapter))
                }
            } catch (_: Exception) {}
        }
    }

    private fun addMemoriesPreview() {
        lifecycleScope.launch {
            try {
                val memories = ImmichClient.getApi().getMemories()
                if (memories.isNotEmpty()) {
                    val listAdapter = ArrayObjectAdapter(CardPresenter(ImmichClient.baseUrl))
                    memories.forEach { memory ->
                        val title = if (memory.data.year != null) {
                            "${memory.data.year} \u2014 ${memory.assets.size} photos"
                        } else "Memory \u2014 ${memory.assets.size} photos"
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

    private fun addAlbumsPreview() {
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

    private fun addPeoplePreview() {
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

    private fun addRandomPreview() {
        lifecycleScope.launch {
            try {
                val assets = ImmichClient.getApi().getRandomAssets(30)
                if (assets.isNotEmpty()) {
                    val listAdapter = ArrayObjectAdapter(CardPresenter(ImmichClient.baseUrl))
                    listAdapter.add(NavItem("random", "\u25A6 View All Random", "Open full grid"))
                    assets.forEach { listAdapter.add(it) }
                    rowsAdapter.add(ListRow(HeaderItem("\uD83C\uDFB2 Random"), listAdapter))
                }
            } catch (_: Exception) {}
        }
    }

    private fun addBrowseRow() {
        val listAdapter = ArrayObjectAdapter(IconCardPresenter())
        listAdapter.add(NavItem("search", "\uD83D\uDD0D Search", "Smart AI search"))
        listAdapter.add(NavItem("explore", "\uD83C\uDF0D Explore", "Places and things"))
        listAdapter.add(NavItem("locations", "\uD83D\uDCCD Locations", "Photos by location"))
        listAdapter.add(NavItem("timeline", "\uD83D\uDCC5 Timeline", "All photos by date"))
        rowsAdapter.add(ListRow(HeaderItem("\uD83D\uDCCB Browse"), listAdapter))
    }

    private fun addSettingsRow() {
        val listAdapter = ArrayObjectAdapter(IconCardPresenter())
        listAdapter.add(NavItem("settings", "\u2699\uFE0F Settings", "Configure connection"))
        rowsAdapter.add(ListRow(HeaderItem("\u2699\uFE0F Settings"), listAdapter))
    }

    // ── Navigation ──────────────────────────────────────────────────────

    private fun handleNavItem(item: NavItem) {
        when (item.id) {
            "photos" -> openGrid(BrowseGridActivity.MODE_PHOTOS, "Photos")
            "random" -> openGrid(BrowseGridActivity.MODE_RANDOM, "Random Photos")
            "timeline" -> openGrid(BrowseGridActivity.MODE_TIMELINE, "Timeline")
            "search" -> startActivity(Intent(requireContext(), SearchActivity::class.java))
            "explore" -> startActivity(Intent(requireContext(), ExploreActivity::class.java))
            "locations" -> startActivity(Intent(requireContext(), MapActivity::class.java))
            "settings" -> startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
    }

    private fun openGrid(mode: String, title: String, id: String = "") {
        startActivity(Intent(requireContext(), BrowseGridActivity::class.java).apply {
            putExtra(BrowseGridActivity.EXTRA_MODE, mode)
            putExtra(BrowseGridActivity.EXTRA_ID, id)
            putExtra(BrowseGridActivity.EXTRA_TITLE, title)
        })
    }

    private fun openAlbum(album: AlbumSimple) {
        openGrid(BrowseGridActivity.MODE_ALBUM, album.albumName, album.id)
    }

    private fun openPerson(person: Person) {
        openGrid(BrowseGridActivity.MODE_PERSON, person.name, person.id)
    }

    private fun openMemory(memory: MemoryCard) {
        openGrid(BrowseGridActivity.MODE_MEMORY, memory.title, memory.id)
    }

    private fun openAsset(asset: Asset) {
        if (asset.type == AssetType.VIDEO) {
            startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_ASSET_ID, asset.id)
                putExtra(VideoPlayerActivity.EXTRA_TITLE, asset.originalFileName)
            })
        } else {
            startActivity(Intent(requireContext(), PhotoViewerActivity::class.java).apply {
                putExtra(PhotoViewerActivity.EXTRA_ASSET_ID, asset.id)
            })
        }
    }
}

// ── Data classes ────────────────────────────────────────────────────────

data class NavItem(
    val id: String,
    val title: String,
    val description: String
)

data class MemoryCard(
    val id: String,
    val title: String,
    val thumbnailAssetId: String?,
    val assets: List<Asset>
)

// Kept for IconCardPresenter compatibility
data class SettingsItem(
    val title: String,
    val description: String
)
