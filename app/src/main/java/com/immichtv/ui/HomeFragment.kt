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
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, row ->
            val headerItem = (row as? ListRow)?.headerItem
            val headerTitle = headerItem?.name ?: ""

            when (item) {
                is AlbumSimple -> openAlbum(item)
                is Person -> openPerson(item)
                is Asset -> {
                    // Photos, Random, Memories rows → open as grid
                    when {
                        headerTitle.contains("Photos") -> openGridMode(BrowseGridActivity.MODE_PHOTOS, "Photos")
                        headerTitle.contains("Random") -> openGridMode(BrowseGridActivity.MODE_RANDOM, "Random Photos")
                        else -> openAsset(item)
                    }
                }
                is SettingsItem -> handleSettingsItem(item)
                is MemoryCard -> openMemory(item)
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

    // ── Data Loading ────────────────────────────────────────────────────────

    private fun loadLatestPhotos() {
        lifecycleScope.launch {
            try {
                val response = ImmichClient.getApi().searchAssets(
                    SearchRequest(size = 50, order = "desc")
                )
                val assets = response.assets.items
                if (assets.isNotEmpty()) {
                    val cardPresenter = CardPresenter(ImmichClient.baseUrl)
                    val listAdapter = ArrayObjectAdapter(cardPresenter)
                    assets.forEach { asset -> listAdapter.add(asset) }
                    val header = HeaderItem("\uD83D\uDCF8 Photos")
                    rowsAdapter.add(0, ListRow(header, listAdapter))
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadMemories() {
        lifecycleScope.launch {
            try {
                val memories = ImmichClient.getApi().getMemories()
                if (memories.isNotEmpty()) {
                    val cardPresenter = CardPresenter(ImmichClient.baseUrl)
                    val listAdapter = ArrayObjectAdapter(cardPresenter)
                    memories.forEach { memory ->
                        val title = if (memory.data.year != null) {
                            "${memory.data.year} \u2014 ${memory.assets.size} photos"
                        } else {
                            "Memory \u2014 ${memory.assets.size} photos"
                        }
                        listAdapter.add(MemoryCard(
                            id = memory.id,
                            title = title,
                            thumbnailAssetId = memory.assets.firstOrNull()?.id,
                            assets = memory.assets
                        ))
                    }
                    val header = HeaderItem("\uD83D\uDCAD On This Day")
                    rowsAdapter.add(ListRow(header, listAdapter))
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadAlbums() {
        lifecycleScope.launch {
            try {
                val albums = ImmichClient.getApi().getAlbums()
                if (albums.isNotEmpty()) {
                    val cardPresenter = CardPresenter(ImmichClient.baseUrl)
                    val listAdapter = ArrayObjectAdapter(cardPresenter)
                    albums.sortedByDescending { it.updatedAt }.forEach { listAdapter.add(it) }
                    val header = HeaderItem("\uD83D\uDCF7 Albums")
                    rowsAdapter.add(ListRow(header, listAdapter))
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
                    val cardPresenter = CardPresenter(ImmichClient.baseUrl)
                    val listAdapter = ArrayObjectAdapter(cardPresenter)
                    people.sortedByDescending { it.assetCount }.forEach { listAdapter.add(it) }
                    val header = HeaderItem("\uD83D\uDC64 People")
                    rowsAdapter.add(ListRow(header, listAdapter))
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadRandomPhotos() {
        lifecycleScope.launch {
            try {
                val assets = ImmichClient.getApi().getRandomAssets(30)
                if (assets.isNotEmpty()) {
                    val cardPresenter = CardPresenter(ImmichClient.baseUrl)
                    val listAdapter = ArrayObjectAdapter(cardPresenter)
                    assets.forEach { listAdapter.add(it) }
                    val header = HeaderItem("\uD83C\uDFB2 Random Photos")
                    rowsAdapter.add(ListRow(header, listAdapter))
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load photos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addNavigationRow() {
        val presenter = IconCardPresenter()
        val listAdapter = ArrayObjectAdapter(presenter)
        listAdapter.add(SettingsItem("\uD83D\uDD0D Search", "Smart AI search"))
        listAdapter.add(SettingsItem("\uD83C\uDF0D Explore", "Places and things"))
        listAdapter.add(SettingsItem("\uD83D\uDCC2 Folders", "Browse by folder"))
        listAdapter.add(SettingsItem("\uD83D\uDCCD Locations", "Photos by location"))
        listAdapter.add(SettingsItem("\uD83D\uDCC5 Timeline", "All photos by date"))
        val header = HeaderItem("\uD83D\uDCCB Browse")
        rowsAdapter.add(ListRow(header, listAdapter))
    }

    private fun addSettingsRow() {
        val presenter = IconCardPresenter()
        val listAdapter = ArrayObjectAdapter(presenter)
        listAdapter.add(SettingsItem("\u2699\uFE0F Server Settings", "Configure connection"))
        val header = HeaderItem("\u2699\uFE0F Settings")
        rowsAdapter.add(ListRow(header, listAdapter))
    }

    // ── Navigation ──────────────────────────────────────────────────────────

    private fun openGridMode(mode: String, title: String) {
        startActivity(Intent(requireContext(), BrowseGridActivity::class.java).apply {
            putExtra(BrowseGridActivity.EXTRA_MODE, mode)
            putExtra(BrowseGridActivity.EXTRA_TITLE, title)
        })
    }

    private fun openAlbum(album: AlbumSimple) {
        startActivity(Intent(requireContext(), BrowseGridActivity::class.java).apply {
            putExtra(BrowseGridActivity.EXTRA_MODE, BrowseGridActivity.MODE_ALBUM)
            putExtra(BrowseGridActivity.EXTRA_ID, album.id)
            putExtra(BrowseGridActivity.EXTRA_TITLE, album.albumName)
        })
    }

    private fun openPerson(person: Person) {
        startActivity(Intent(requireContext(), BrowseGridActivity::class.java).apply {
            putExtra(BrowseGridActivity.EXTRA_MODE, BrowseGridActivity.MODE_PERSON)
            putExtra(BrowseGridActivity.EXTRA_ID, person.id)
            putExtra(BrowseGridActivity.EXTRA_TITLE, person.name)
        })
    }

    private fun openMemory(memory: MemoryCard) {
        startActivity(Intent(requireContext(), BrowseGridActivity::class.java).apply {
            putExtra(BrowseGridActivity.EXTRA_MODE, BrowseGridActivity.MODE_MEMORY)
            putExtra(BrowseGridActivity.EXTRA_ID, memory.id)
            putExtra(BrowseGridActivity.EXTRA_TITLE, memory.title)
        })
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

    private fun handleSettingsItem(item: SettingsItem) {
        when {
            item.title.contains("Search") -> startActivity(Intent(requireContext(), SearchActivity::class.java))
            item.title.contains("Explore") -> startActivity(Intent(requireContext(), ExploreActivity::class.java))
            item.title.contains("Folders") -> startActivity(Intent(requireContext(), FolderBrowserActivity::class.java))
            item.title.contains("Locations") -> startActivity(Intent(requireContext(), MapActivity::class.java))
            item.title.contains("Timeline") -> startActivity(Intent(requireContext(), BrowseGridActivity::class.java).apply {
                putExtra(BrowseGridActivity.EXTRA_MODE, BrowseGridActivity.MODE_TIMELINE)
                putExtra(BrowseGridActivity.EXTRA_TITLE, "Timeline")
            })
            item.title.contains("Settings") -> startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
    }

    private fun openSettings() {
        startActivity(Intent(requireContext(), SettingsActivity::class.java))
    }
}

// ── Data classes for special cards ──────────────────────────────────────────

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
