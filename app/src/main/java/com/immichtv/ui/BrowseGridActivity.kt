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
    }

    private val gridAdapter = ArrayObjectAdapter(CardPresenter(ImmichClient.baseUrl))
    private var assets: List<Asset> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gridPresenter = VerticalGridPresenter().apply {
            numberOfColumns = NUM_COLUMNS
        }
        setGridPresenter(gridPresenter)
        adapter = gridAdapter

        title = arguments?.getString(BrowseGridActivity.EXTRA_TITLE) ?: "Photos"

        setupClickListener()
        loadAssets()
    }

    private fun setupClickListener() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is Asset) openAsset(item)
        }
    }

    private fun loadAssets() {
        val mode = arguments?.getString(BrowseGridActivity.EXTRA_MODE) ?: return
        val id = arguments?.getString(BrowseGridActivity.EXTRA_ID) ?: ""
        val searchField = arguments?.getString(BrowseGridActivity.EXTRA_SEARCH_FIELD)

        lifecycleScope.launch {
            try {
                assets = when (mode) {
                    BrowseGridActivity.MODE_ALBUM -> {
                        val album = ImmichClient.getApi().getAlbumDetail(id)
                        album.assets
                    }
                    BrowseGridActivity.MODE_PERSON -> {
                        val response = ImmichClient.getApi().searchAssets(
                            SearchRequest(personIds = listOf(id), size = 200, order = "desc")
                        )
                        response.assets.items
                    }
                    BrowseGridActivity.MODE_MEMORY -> {
                        val memories = ImmichClient.getApi().getMemories()
                        memories.find { it.id == id }?.assets ?: emptyList()
                    }
                    BrowseGridActivity.MODE_TIMELINE -> {
                        // Load by time buckets for date-grouped display
                        loadTimelineBuckets()
                    }
                    BrowseGridActivity.MODE_SEARCH -> {
                        // Smart search by explore category value
                        val response = ImmichClient.getApi().smartSearch(
                            SmartSearchRequest(query = id, size = 200)
                        )
                        response.assets.items
                    }
                    BrowseGridActivity.MODE_FOLDER -> {
                        // Search by original path containing the folder
                        val response = ImmichClient.getApi().searchAssets(
                            SearchRequest(
                                query = id,
                                size = 200,
                                order = "desc"
                            )
                        )
                        response.assets.items
                    }
                    else -> emptyList()
                }

                assets.forEach { asset -> gridAdapter.add(asset) }

                if (assets.isEmpty()) {
                    Toast.makeText(requireContext(), "No photos found", Toast.LENGTH_SHORT).show()
                }

                // Update title with count
                title = "${arguments?.getString(BrowseGridActivity.EXTRA_TITLE) ?: "Photos"} (${assets.size})"

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun loadTimelineBuckets(): List<Asset> {
        val buckets = ImmichClient.getApi().getTimeBuckets(size = "MONTH")
        val allAssets = mutableListOf<Asset>()
        // Load first 6 months for performance
        for (bucket in buckets.take(6)) {
            try {
                val bucketAssets = ImmichClient.getApi().getTimeBucket(
                    size = "MONTH",
                    timeBucket = bucket.timeBucket
                )
                allAssets.addAll(bucketAssets)
            } catch (_: Exception) {}
        }
        return allAssets
    }

    private fun openAsset(asset: Asset) {
        val imageAssets = assets.filter { it.type == AssetType.IMAGE }
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
