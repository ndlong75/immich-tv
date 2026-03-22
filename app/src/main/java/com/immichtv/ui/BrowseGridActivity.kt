package com.immichtv.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.immichtv.R
import com.immichtv.api.*
import com.immichtv.ui.presenters.CardPresenter
import kotlinx.coroutines.launch

class BrowseGridActivity : FragmentActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SEARCH_FIELD = "search_field"
        const val MODE_ALBUM = "album"
        const val MODE_PERSON = "person"
        const val MODE_MEMORY = "memory"
        const val MODE_TIMELINE = "timeline"
        const val MODE_SEARCH = "search"
        const val MODE_PHOTOS = "photos"
        const val MODE_RANDOM = "random"
        const val MODE_DATE = "date"       // Jump to specific date
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            val fragment = AssetGridFragment().apply {
                arguments = Bundle().apply {
                    putString(EXTRA_MODE, intent.getStringExtra(EXTRA_MODE))
                    putString(EXTRA_ID, intent.getStringExtra(EXTRA_ID))
                    putString(EXTRA_TITLE, intent.getStringExtra(EXTRA_TITLE))
                    putString(EXTRA_SEARCH_FIELD, intent.getStringExtra(EXTRA_SEARCH_FIELD))
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, fragment)
                .commit()
        }
    }
}

class AssetGridFragment : VerticalGridSupportFragment() {

    companion object {
        private const val NUM_COLUMNS = 5
        private const val BATCH_SIZE = 200
    }

    private val gridAdapter = ArrayObjectAdapter(CardPresenter(ImmichClient.baseUrl))
    private var allAssets: MutableList<Asset> = mutableListOf()
    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gridPresenter = VerticalGridPresenter().apply {
            numberOfColumns = NUM_COLUMNS
        }
        setGridPresenter(gridPresenter)
        adapter = gridAdapter

        title = arguments?.getString(BrowseGridActivity.EXTRA_TITLE) ?: "Photos"

        setupClickListener()
        setupScrollListener()
        loadAssets()
    }

    private fun setupClickListener() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is Asset) openAsset(item)
        }
    }

    private fun setupScrollListener() {
        setOnItemViewSelectedListener(OnItemViewSelectedListener { _, item, _, _ ->
            if (item is Asset && !isLoading && hasMore) {
                val position = allAssets.indexOfFirst { it.id == item.id }
                if (position >= allAssets.size - 20) {
                    loadNextBatch()
                }
            }
        })
    }

    private fun loadAssets() {
        val mode = arguments?.getString(BrowseGridActivity.EXTRA_MODE) ?: return
        val id = arguments?.getString(BrowseGridActivity.EXTRA_ID) ?: ""

        when (mode) {
            BrowseGridActivity.MODE_ALBUM -> loadAlbum(id)
            BrowseGridActivity.MODE_PERSON -> loadPaginated(personIds = listOf(id))
            BrowseGridActivity.MODE_MEMORY -> loadMemory(id)
            BrowseGridActivity.MODE_TIMELINE, BrowseGridActivity.MODE_PHOTOS -> loadPaginated()
            BrowseGridActivity.MODE_RANDOM -> loadRandom()
            BrowseGridActivity.MODE_SEARCH -> loadSmartSearch(id)
            BrowseGridActivity.MODE_DATE -> loadByDate(id)
        }
    }

    // ── Core paginated loader using search/metadata ─────────────────────

    private fun loadPaginated(
        personIds: List<String>? = null,
        takenAfter: String? = null,
        takenBefore: String? = null
    ) {
        if (isLoading) return
        isLoading = true
        lifecycleScope.launch {
            try {
                val response = ImmichClient.getApi().searchAssets(
                    SearchRequest(
                        size = BATCH_SIZE,
                        page = currentPage,
                        order = "desc",
                        personIds = personIds,
                        takenAfter = takenAfter,
                        takenBefore = takenBefore
                    )
                )
                val items = response.assets.items
                hasMore = items.size >= BATCH_SIZE
                addAssets(items)
            } catch (e: Exception) { showError(e) }
            finally { isLoading = false }
        }
    }

    private fun loadSmartSearch(query: String) {
        if (isLoading) return
        isLoading = true
        lifecycleScope.launch {
            try {
                val response = ImmichClient.getApi().smartSearch(
                    SmartSearchRequest(query = query, size = BATCH_SIZE, page = currentPage)
                )
                val items = response.assets.items
                hasMore = items.size >= BATCH_SIZE
                addAssets(items)
            } catch (e: Exception) { showError(e) }
            finally { isLoading = false }
        }
    }

    private fun loadRandom() {
        if (isLoading) return
        isLoading = true
        lifecycleScope.launch {
            try {
                val items = ImmichClient.getApi().getRandomAssets(BATCH_SIZE)
                // Random always has more
                hasMore = true
                addAssets(items)
            } catch (e: Exception) { showError(e) }
            finally { isLoading = false }
        }
    }

    private fun loadByDate(dateStr: String) {
        // dateStr = "2024-03-15" -> load around that date
        if (isLoading) return
        isLoading = true
        lifecycleScope.launch {
            try {
                // Load photos from that date's month
                val yearMonth = dateStr.take(7) // "2024-03"
                val takenAfter = "${yearMonth}-01T00:00:00.000Z"
                val nextMonth = nextMonthStr(yearMonth)
                val takenBefore = "${nextMonth}-01T00:00:00.000Z"

                val response = ImmichClient.getApi().searchAssets(
                    SearchRequest(
                        size = BATCH_SIZE,
                        page = currentPage,
                        order = "desc",
                        takenAfter = takenAfter,
                        takenBefore = takenBefore
                    )
                )
                val items = response.assets.items
                hasMore = items.size >= BATCH_SIZE
                addAssets(items)
            } catch (e: Exception) { showError(e) }
            finally { isLoading = false }
        }
    }

    private fun nextMonthStr(yearMonth: String): String {
        val parts = yearMonth.split("-")
        var year = parts[0].toInt()
        var month = parts[1].toInt() + 1
        if (month > 12) { month = 1; year++ }
        return "$year-${month.toString().padStart(2, '0')}"
    }

    // ── Non-paginated loaders ───────────────────────────────────────────

    private fun loadAlbum(albumId: String) {
        lifecycleScope.launch {
            try {
                val album = ImmichClient.getApi().getAlbumDetail(albumId)
                hasMore = false
                addAssets(album.assets)
            } catch (e: Exception) { showError(e) }
        }
    }

    private fun loadMemory(memoryId: String) {
        lifecycleScope.launch {
            try {
                val memories = ImmichClient.getApi().getMemories()
                hasMore = false
                addAssets(memories.find { it.id == memoryId }?.assets ?: emptyList())
            } catch (e: Exception) { showError(e) }
        }
    }

    // ── Next batch (triggered by scroll) ────────────────────────────────

    private fun loadNextBatch() {
        val mode = arguments?.getString(BrowseGridActivity.EXTRA_MODE) ?: return
        val id = arguments?.getString(BrowseGridActivity.EXTRA_ID) ?: ""
        currentPage++

        when (mode) {
            BrowseGridActivity.MODE_PHOTOS, BrowseGridActivity.MODE_TIMELINE -> loadPaginated()
            BrowseGridActivity.MODE_RANDOM -> loadRandom()
            BrowseGridActivity.MODE_PERSON -> loadPaginated(personIds = listOf(id))
            BrowseGridActivity.MODE_SEARCH -> loadSmartSearch(id)
            BrowseGridActivity.MODE_DATE -> loadByDate(id)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun addAssets(newAssets: List<Asset>) {
        val existingIds = allAssets.map { it.id }.toSet()
        val unique = newAssets.filter { it.id !in existingIds }
        allAssets.addAll(unique)
        unique.forEach { gridAdapter.add(it) }

        if (allAssets.isEmpty()) {
            Toast.makeText(requireContext(), "No photos found", Toast.LENGTH_SHORT).show()
        }
        updateTitle()
    }

    private fun updateTitle() {
        val baseTitle = arguments?.getString(BrowseGridActivity.EXTRA_TITLE) ?: "Photos"
        title = "$baseTitle (${allAssets.size}${if (hasMore) "+" else ""})"
    }

    private fun showError(e: Exception) {
        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
    }

    private fun openAsset(asset: Asset) {
        val imageAssets = allAssets.filter { it.type == AssetType.IMAGE }
        val imageIndex = imageAssets.indexOfFirst { it.id == asset.id }.coerceAtLeast(0)

        if (asset.type == AssetType.VIDEO) {
            startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_ASSET_ID, asset.id)
                putExtra(VideoPlayerActivity.EXTRA_TITLE, asset.originalFileName)
            })
        } else {
            // Limit to ~50 IDs around the selected photo to prevent
            // TransactionTooLargeException (Intent has ~1MB limit)
            val windowSize = 50
            val windowStart = (imageIndex - windowSize / 2).coerceAtLeast(0)
            val windowEnd = (windowStart + windowSize).coerceAtMost(imageAssets.size)
            val windowIds = imageAssets.subList(windowStart, windowEnd).map { it.id }
            val adjustedIndex = imageIndex - windowStart

            startActivity(Intent(requireContext(), PhotoViewerActivity::class.java).apply {
                putExtra(PhotoViewerActivity.EXTRA_ASSET_ID, asset.id)
                putStringArrayListExtra(
                    PhotoViewerActivity.EXTRA_ASSET_IDS,
                    ArrayList(windowIds)
                )
                putExtra(PhotoViewerActivity.EXTRA_START_INDEX, adjustedIndex)
            })
        }
    }
}
