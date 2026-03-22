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

    companion object {
        private const val ROW_PHOTOS = 0L
        private const val ROW_MEMORIES = 1L
        private const val ROW_ALBUMS = 2L
        private const val ROW_PEOPLE = 3L
        private const val ROW_RANDOM = 4L
        private const val ROW_SETTINGS = 5L
    }

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

        // Brand colors
        brandColor = Color.parseColor("#1E40AF")    // Deep blue
        searchAffordanceColor = Color.parseColor("#3B82F6")
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, row ->
            when (item) {
                is AlbumSimple -> openAlbum(item)
                is Person -> openPerson(item)
                is Asset -> openAsset(item)
                is SettingsItem -> openSettings()
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
            ImmichClient.configure(PrefsManager.serverUrl, PrefsManager.apiKey)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to connect: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        loadLatestPhotos()
        loadMemories()
        loadAlbums()
        loadPeople()
        loadRandomPhotos()
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
                    val header = HeaderItem(ROW_PHOTOS, "\uD83D\uDCF8 Photos")
                    rowsAdapter.add(0, ListRow(header, listAdapter))
                }
            } catch (e: Exception) {
                // Fallback silently
            }
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
                            "${memory.data.year} — ${memory.assets.size} photos"
                        } else {
                            "Memory — ${memory.assets.size} photos"
                        }
                        val thumbAsset = memory.assets.firstOrNull()
                        listAdapter.add(MemoryCard(
                            id = memory.id,
                            title = title,
                            thumbnailAssetId = thumbAsset?.id,
                            assets = memory.assets
                        ))
                    }
                    val header = HeaderItem(ROW_MEMORIES, "\uD83D\uDCAD On This Day")
                    rowsAdapter.add(ListRow(header, listAdapter))
                }
            } catch (e: Exception) {
                // Memories endpoint may not be available on older Immich versions
            }
        }
    }

    private fun loadAlbums() {
        lifecycleScope.launch {
            try {
                val albums = ImmichClient.getApi().getAlbums()
                if (albums.isNotEmpty()) {
                    val cardPresenter = CardPresenter(ImmichClient.baseUrl)
                    val listAdapter = ArrayObjectAdapter(cardPresenter)
                    albums.sortedByDescending { it.updatedAt }.forEach { album ->
                        listAdapter.add(album)
                    }
                    val header = HeaderItem(ROW_ALBUMS, "\uD83D\uDCF7 Albums")
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
                    people.sortedBy { it.name }.forEach { person ->
                        listAdapter.add(person)
                    }
                    val header = HeaderItem(ROW_PEOPLE, "\uD83D\uDC64 People")
                    rowsAdapter.add(ListRow(header, listAdapter))
                }
            } catch (e: Exception) {
                // People may not be available
            }
        }
    }

    private fun loadRandomPhotos() {
        lifecycleScope.launch {
            try {
                val assets = ImmichClient.getApi().getRandomAssets(30)
                if (assets.isNotEmpty()) {
                    val cardPresenter = CardPresenter(ImmichClient.baseUrl)
                    val listAdapter = ArrayObjectAdapter(cardPresenter)
                    assets.forEach { asset ->
                        listAdapter.add(asset)
                    }
                    val header = HeaderItem(ROW_RANDOM, "\uD83C\uDFB2 Random Photos")
                    rowsAdapter.add(ListRow(header, listAdapter))
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load photos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addSettingsRow() {
        val presenter = IconCardPresenter()
        val listAdapter = ArrayObjectAdapter(presenter)
        listAdapter.add(SettingsItem("⚙️ Server Settings", "Configure Immich server connection"))
        val header = HeaderItem(ROW_SETTINGS, "⚙️ Settings")
        rowsAdapter.add(ListRow(header, listAdapter))
    }

    // ── Navigation ──────────────────────────────────────────────────────────

    private fun openAlbum(album: AlbumSimple) {
        val intent = Intent(requireContext(), BrowseGridActivity::class.java).apply {
            putExtra(BrowseGridActivity.EXTRA_MODE, BrowseGridActivity.MODE_ALBUM)
            putExtra(BrowseGridActivity.EXTRA_ID, album.id)
            putExtra(BrowseGridActivity.EXTRA_TITLE, album.albumName)
        }
        startActivity(intent)
    }

    private fun openPerson(person: Person) {
        val intent = Intent(requireContext(), BrowseGridActivity::class.java).apply {
            putExtra(BrowseGridActivity.EXTRA_MODE, BrowseGridActivity.MODE_PERSON)
            putExtra(BrowseGridActivity.EXTRA_ID, person.id)
            putExtra(BrowseGridActivity.EXTRA_TITLE, person.name)
        }
        startActivity(intent)
    }

    private fun openMemory(memory: MemoryCard) {
        val intent = Intent(requireContext(), BrowseGridActivity::class.java).apply {
            putExtra(BrowseGridActivity.EXTRA_MODE, BrowseGridActivity.MODE_MEMORY)
            putExtra(BrowseGridActivity.EXTRA_ID, memory.id)
            putExtra(BrowseGridActivity.EXTRA_TITLE, memory.title)
        }
        startActivity(intent)
    }

    private fun openAsset(asset: Asset) {
        if (asset.type == AssetType.VIDEO) {
            val intent = Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_ASSET_ID, asset.id)
                putExtra(VideoPlayerActivity.EXTRA_TITLE, asset.originalFileName)
            }
            startActivity(intent)
        } else {
            val intent = Intent(requireContext(), PhotoViewerActivity::class.java).apply {
                putExtra(PhotoViewerActivity.EXTRA_ASSET_ID, asset.id)
            }
            startActivity(intent)
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
