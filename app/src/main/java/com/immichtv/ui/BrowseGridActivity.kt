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
        const val MODE_FOLDER = "folder"
        const val MODE_PHOTOS = "photos"
        const val MODE_RANDOM = "random"
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
    private var timelineBucketIndex = 0
    private var timelineBuckets: List<TimeBucket> = emptyList()

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
            // Auto-load next batch when near the end
            if (item is Asset && !isLoading && hasMore) {
                val position = allAssets.indexOfFirst { it.id == (item as? Asset)?.id }
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
            BrowseGridActivity.MODE_PERSON -> loadPerson(id)
            BrowseGridActivity.MODE_MEMORY -> loadMemory(id)
            BrowseGridActivity.MODE_TIMELINE -> loadTimeline()
            BrowseGridActivity.MODE_PHOTOS -> loadPhotos()
            BrowseGridActivity.MODE_RANDOM -> loadRandom()
            BrowseGridActivity.MODE_SEARCH -> loadSearch(id)
            BrowseGridActivity.MODE_FOLDER -> loadFolder(id)
        }
    }

    // ── Paginated loaders ───────────────────────────────────────────────

    private fun loadPhotos() {
        isLoading = true
        lifecycleScope.launch {
            try {
                val response = ImmichClient.getApi().searchAssets(
                    SearchRequest(size = BATCH_SIZE, page = currentPage, order = "desc")
                )
                val items = response.assets.items
                hasMore = items.size >= BATCH_SIZE
                addAssets(items)
                updateTitle()
            } catch (e: Exception) {
                showError(e)
            } finally {
                isLoading = false
            }
        }
    }

    private fun loadRandom() {
        isLoading = true
        lifecycleScope.launch {
            try {
                val items = ImmichClient.getApi().getRandomAssets(BATCH_SIZE)
                hasMore = items.size >= BATCH_SIZE
                addAssets(items)
                updateTitle()
            } catch (e: Exception) {
                showError(e)
            } finally {
                isLoading = false
            }
        }
    }

    private fun loadPerson(personId: String) {
        isLoading = true
        lifecycleScope.launch {
            try {
                val response = ImmichClient.getApi().searchAssets(
                    SearchRequest(
                        personIds = listOf(personId),
                        size = BATCH_SIZE,
                        page = currentPage,
                        order = "desc"
                    )
                )
                val items = response.assets.items
                hasMore = items.size >= BATCH_SIZE
                addAssets(items)
                updateTitle()
            } catch (e: Exception) {
                showError(e)
            } finally {
                isLoading = false
            }
        }
    }

    private fun loadSearch(query: String) {
        isLoading = true
        lifecycleScope.launch {
            try {
                val response = ImmichClient.getApi().smartSearch(
                    SmartSearchRequest(query = query, size = BATCH_SIZE, page = currentPage)
                )
                val items = response.assets.items
                hasMore = items.size >= BATCH_SIZE
                addAssets(items)
                updateTitle()
            } catch (e: Exception) {
                showError(e)
            } finally {
                isLoading = false
            }
        }
    }

    private fun loadFolder(path: String) {
        isLoading = true
        lifecycleScope.launch {
            try {
                val response = ImmichClient.getApi().searchAssets(
                    SearchRequest(query = path, size = BATCH_SIZE, page = currentPage, order = "desc")
                )
                val items = response.assets.items
                hasMore = items.size >= BATCH_SIZE
                addAssets(items)
                updateTitle()
            } catch (e: Exception) {
                showError(e)
            } finally {
                isLoading = false
            }
        }
    }

    // ── Non-paginated loaders ───────────────────────────────────────────

    private fun loadAlbum(albumId: String) {
        lifecycleScope.launch {
            try {
                val album = ImmichClient.getApi().getAlbumDetail(albumId)
                hasMore = false
                addAssets(album.assets)
                updateTitle()
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    private fun loadMemory(memoryId: String) {
        lifecycleScope.launch {
            try {
                val memories = ImmichClient.getApi().getMemories()
                val memory = memories.find { it.id == memoryId }
                hasMore = false
                addAssets(memory?.assets ?: emptyList())
                updateTitle()
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    private fun loadTimeline() {
        isLoading = true
        lifecycleScope.launch {
            try {
                if (timelineBuckets.isEmpty()) {
                    timelineBuckets = ImmichClient.getApi().getTimeBuckets(size = "MONTH")
                }
                // Load 3 month-buckets per batch
                val bucketsToLoad = timelineBuckets.drop(timelineBucketIndex).take(3)
                val batchAssets = mutableListOf<Asset>()
                for (bucket in bucketsToLoad) {
                    try {
                        val assets = ImmichClient.getApi().getTimeBucket(
                            size = "MONTH", timeBucket = bucket.timeBucket
                        )
                        batchAssets.addAll(assets)
                    } catch (_: Exception) {}
                }
                timelineBucketIndex += bucketsToLoad.size
                hasMore = timelineBucketIndex < timelineBuckets.size
                addAssets(batchAssets)
                updateTitle()
            } catch (e: Exception) {
                showError(e)
            } finally {
                isLoading = false
            }
        }
    }

    // ── Next batch (triggered by scroll) ────────────────────────────────

    private fun loadNextBatch() {
        val mode = arguments?.getString(BrowseGridActivity.EXTRA_MODE) ?: return
        val id = arguments?.getString(BrowseGridActivity.EXTRA_ID) ?: ""
        currentPage++

        when (mode) {
            BrowseGridActivity.MODE_PHOTOS -> loadPhotos()
            BrowseGridActivity.MODE_RANDOM -> loadRandom()
            BrowseGridActivity.MODE_PERSON -> loadPerson(id)
            BrowseGridActivity.MODE_SEARCH -> loadSearch(id)
            BrowseGridActivity.MODE_FOLDER -> loadFolder(id)
            BrowseGridActivity.MODE_TIMELINE -> loadTimeline()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun addAssets(newAssets: List<Asset>) {
        // Deduplicate by ID
        val existingIds = allAssets.map { it.id }.toSet()
        val unique = newAssets.filter { it.id !in existingIds }
        allAssets.addAll(unique)
        unique.forEach { gridAdapter.add(it) }

        if (allAssets.isEmpty()) {
            Toast.makeText(requireContext(), "No photos found", Toast.LENGTH_SHORT).show()
        }
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
            startActivity(Intent(requireContext(), PhotoViewerActivity::class.java).apply {
                putExtra(PhotoViewerActivity.EXTRA_ASSET_ID, asset.id)
                putStringArrayListExtra(
                    PhotoViewerActivity.EXTRA_ASSET_IDS,
                    ArrayList(imageAssets.map { it.id })
                )
                putExtra(PhotoViewerActivity.EXTRA_START_INDEX, imageIndex)
            })
        }
    }
}
