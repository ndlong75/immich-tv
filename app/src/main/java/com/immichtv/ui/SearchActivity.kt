package com.immichtv.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.immichtv.R
import com.immichtv.api.*
import com.immichtv.ui.presenters.CardPresenter
import kotlinx.coroutines.launch

class SearchActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        val searchInput = findViewById<EditText>(R.id.search_input)
        val resultCount = findViewById<TextView>(R.id.result_count)

        searchInput.requestFocus()

        searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val query = searchInput.text.toString().trim()
                if (query.isNotBlank()) {
                    doSearch(query, resultCount)
                }
                true
            } else false
        }
    }

    private fun doSearch(query: String, resultCount: TextView) {
        resultCount.text = "Searching..."

        val fragment = SearchResultsFragment().apply {
            arguments = Bundle().apply {
                putString("query", query)
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.results_frame, fragment)
            .commit()
    }
}

class SearchResultsFragment : VerticalGridSupportFragment() {

    private val gridAdapter = ArrayObjectAdapter(CardPresenter(ImmichClient.baseUrl))
    private var assets: List<Asset> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gridPresenter = VerticalGridPresenter().apply {
            numberOfColumns = 5
        }
        setGridPresenter(gridPresenter)
        adapter = gridAdapter

        val query = arguments?.getString("query") ?: return
        title = "Results: $query"

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is Asset) openAsset(item)
        }

        loadResults(query)
    }

    private fun loadResults(query: String) {
        lifecycleScope.launch {
            try {
                val response = ImmichClient.getApi().smartSearch(
                    SmartSearchRequest(query = query, size = 100)
                )
                assets = response.assets.items
                assets.forEach { gridAdapter.add(it) }

                // Update count in parent
                activity?.findViewById<TextView>(R.id.result_count)?.text =
                    "${assets.size} results for \"$query\""

                if (assets.isEmpty()) {
                    Toast.makeText(requireContext(), "No results found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Search error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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
