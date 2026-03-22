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
        const val MODE_DATE = "date"  // jump to specific date
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
                .replace(R.id.main_frame, fragment).commit()
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
    private var timelineBucketIndex = 0
    private var timelineBuckets: List<TimeBucket> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setGridPresenter(VerticalGridPresenter().apply { numberOfColumns = NUM_COLUMNS })
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
                val pos = allAssets.indexOfFirst { it.id == item.id }
                if (pos >= allAssets.size - 20) loadNextBatch()
            }
        })
    }

    private fun loadAssets() {
        val mode = arguments?.getString(BrowseGridActivity.EXTRA_MODE) ?: return
        val id = arguments?.getString(BrowseGridActivity.EXTRA_ID) ?: ""
        when (mode) {
            BrowseGridActivity.MODE_ALBUM -> loadAlbum(id)
            BrowseGridActivity.MODE_PERSON -> loadPaginated { loadPersonPage(id) }
            BrowseGridActivity.MODE_MEMORY -> loadMemory(id)
            BrowseGridActivity.MODE_TIMELINE -> loadTimeline()
            BrowseGridActivity.MODE_PHOTOS -> loadPaginated { loadPhotosPage() }
            BrowseGridActivity.MODE_RANDOM -> loadPaginated { loadRandomPage() }
            BrowseGridActivity.MODE_SEARCH -> loadPaginated { loadSearchPage(id) }
            BrowseGridActivity.MODE_DATE -> loadDatePhotos(id)
        }
    }

    // ── Paginated wrapper ───────────────────────────────────────────────
    private fun loadPaginated(loader: suspend () -> List<Asset>) {
        if (isLoading) return
        isLoading = true
        lifecycleScope.launch {
            try {
                val items = loader()
                hasMore = items.size >= BATCH_SIZE
                addAssets(items)
            } catch (e: Exception) { showError(e) }
            finally { isLoading = false }
        }
    }

    private suspend fun loadPhotosPage(): List<Asset> {
        return ImmichClient.getApi().searchAssets(
            SearchRequest(size = BATCH_SIZE, page = currentPage, order = "desc")
        ).assets.items
    }

    private suspend fun loadRandomPage(): List<Asset> {
        return ImmichClient.getApi().getRandomAssets(BATCH_SIZE)
    }

    private suspend fun loadPersonPage(personId: String): List<Asset> {
        return ImmichClient.getApi().searchAssets(
            SearchRequest(personIds = listOf(personId), size = BATCH_SIZE, page = currentPage, order = "desc")
        ).assets.items
    }

    private suspend fun loadSearchPage(query: String): List<Asset> {
        return ImmichClient.getApi().smartSearch(
            SmartSearchRequest(query = query, size = BATCH_SIZE, page = currentPage)
        ).assets.items
    }

    // ── Non-paginated ───────────────────────────────────────────────────
    private fun loadAlbum(albumId: String) {
        lifecycleScope.launch {
            try {
                val album = ImmichClient.getApi().getAlbumDetail(albumId)
                hasMore = false; addAssets(album.assets)
            } catch (e: Exception) { showError(e) }
        }
    }

    private fun loadMemory(memoryId: String) {
        lifecycleScope.launch {
            try {
                val memories = ImmichClient.getApi().getMemories()
                hasMore = false; addAssets(memories.find { it.id == memoryId }?.assets ?: emptyList())
            } catch (e: Exception) { showError(e) }
        }
    }

    private fun loadTimeline() {
        if (isLoading) return
        isLoading = true
        lifecycleScope.launch {
            try {
                if (timelineBuckets.isEmpty()) {
                    timelineBuckets = ImmichClient.getApi().getTimeBuckets(size = "MONTH")
                }
                val batch = timelineBuckets.drop(timelineBucketIndex).take(3)
                val results = mutableListOf<Asset>()
                for (bucket in batch) {
                    try {
                        results.addAll(ImmichClient.getApi().getTimeBucket(
                            size = "MONTH", timeBucket = bucket.timeBucket
                        ))
                    } catch (_: Exception) {}
                }
                timelineBucketIndex += batch.size
                hasMore = timelineBucketIndex < timelineBuckets.size
                addAssets(results)
            } catch (e: Exception) { showError(e) }
            finally { isLoading = false }
        }
    }

    private fun loadDatePhotos(dateStr: String) {
        // dateStr = "YYYY-MM-DD", load photos from that month
        lifecycleScope.launch {
            try {
                val yearMonth = dateStr.take(7) // "YYYY-MM"
                val bucketStr = "${yearMonth}-01T00:00:00.000Z"
                val assets = ImmichClient.getApi().getTimeBucket(
                    size = "MONTH", timeBucket = bucketStr
                )
                hasMore = false; addAssets(assets)
            } catch (e: Exception) {
                // Fallback: search by date range
                try {
                    val response = ImmichClient.getApi().searchAssets(
                        SearchRequest(
                            takenAfter = "${dateStr}T00:00:00.000Z",
                            takenBefore = "${dateStr}T23:59:59.999Z",
                            size = BATCH_SIZE, order = "desc"
                        )
                    )
                    hasMore = false; addAssets(response.assets.items)
                } catch (e2: Exception) { showError(e2) }
            }
        }
    }

    // ── Next batch ──────────────────────────────────────────────────────
    private fun loadNextBatch() {
        val mode = arguments?.getString(BrowseGridActivity.EXTRA_MODE) ?: return
        val id = arguments?.getString(BrowseGridActivity.EXTRA_ID) ?: ""
        currentPage++
        when (mode) {
            BrowseGridActivity.MODE_PHOTOS -> loadPaginated { loadPhotosPage() }
            BrowseGridActivity.MODE_RANDOM -> loadPaginated { loadRandomPage() }
            BrowseGridActivity.MODE_PERSON -> loadPaginated { loadPersonPage(id) }
            BrowseGridActivity.MODE_SEARCH -> loadPaginated { loadSearchPage(id) }
            BrowseGridActivity.MODE_TIMELINE -> loadTimeline()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────
    private fun addAssets(newAssets: List<Asset>) {
        val existingIds = allAssets.map { it.id }.toSet()
        val unique = newAssets.filter { it.id !in existingIds }
        allAssets.addAll(unique)
        unique.forEach { gridAdapter.add(it) }
        if (allAssets.isEmpty()) Toast.makeText(requireContext(), "No photos found", Toast.LENGTH_SHORT).show()
        val baseTitle = arguments?.getString(BrowseGridActivity.EXTRA_TITLE) ?: "Photos"
        title = "$baseTitle (${allAssets.size}${if (hasMore) "+" else ""})"
    }

    private fun showError(e: Exception) {
        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
    }

    private fun openAsset(asset: Asset) {
        val imageAssets = allAssets.filter { it.type == AssetType.IMAGE }
        val idx = imageAssets.indexOfFirst { it.id == asset.id }.coerceAtLeast(0)
        if (asset.type == AssetType.VIDEO) {
            startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_ASSET_ID, asset.id)
                putExtra(VideoPlayerActivity.EXTRA_TITLE, asset.originalFileName)
            })
        } else {
            startActivity(Intent(requireContext(), PhotoViewerActivity::class.java).apply {
                putExtra(PhotoViewerActivity.EXTRA_ASSET_ID, asset.id)
                putStringArrayListExtra(PhotoViewerActivity.EXTRA_ASSET_IDS, ArrayList(imageAssets.map { it.id }))
                putExtra(PhotoViewerActivity.EXTRA_START_INDEX, idx)
            })
        }
    }
}
