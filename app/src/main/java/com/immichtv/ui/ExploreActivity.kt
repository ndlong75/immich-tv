package com.immichtv.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.immichtv.api.*
import com.immichtv.ui.presenters.CardPresenter
import com.immichtv.ui.presenters.TextCardPresenter
import kotlinx.coroutines.launch

class ExploreActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.immichtv.R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(com.immichtv.R.id.main_frame, ExploreFragment())
                .commit()
        }
    }
}

class ExploreFragment : BrowseSupportFragment() {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Explore"
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = Color.parseColor("#0f3460")
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        adapter = rowsAdapter
        setupClickListener()
        loadExploreData()
    }

    private fun setupClickListener() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is ExploreEntry) {
                val intent = Intent(requireContext(), BrowseGridActivity::class.java).apply {
                    putExtra(BrowseGridActivity.EXTRA_MODE, BrowseGridActivity.MODE_SEARCH)
                    putExtra(BrowseGridActivity.EXTRA_ID, item.value)
                    putExtra(BrowseGridActivity.EXTRA_TITLE, item.value)
                    putExtra(BrowseGridActivity.EXTRA_SEARCH_FIELD, item.fieldName)
                }
                startActivity(intent)
            }
        }
    }

    private fun loadExploreData() {
        lifecycleScope.launch {
            try {
                val groups = ImmichClient.getApi().getExploreData()
                groups.forEach { group ->
                    val presenter = TextCardPresenter()
                    val listAdapter = ArrayObjectAdapter(presenter)
                    group.items.forEach { item ->
                        listAdapter.add(ExploreEntry(
                            fieldName = group.fieldName,
                            value = item.value,
                            thumbnailAssetId = item.data.id
                        ))
                    }
                    val label = when (group.fieldName) {
                        "exifInfo.city" -> "\uD83C\uDFD9 Places"
                        "smartInfo.objects" -> "\uD83D\uDCE6 Things"
                        "smartInfo.tags" -> "\uD83C\uDFF7 Tags"
                        else -> group.fieldName
                    }
                    val header = HeaderItem(label)
                    rowsAdapter.add(ListRow(header, listAdapter))
                }

                if (groups.isEmpty()) {
                    Toast.makeText(requireContext(), "No explore data available", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Explore error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

data class ExploreEntry(
    val fieldName: String,
    val value: String,
    val thumbnailAssetId: String?
)
