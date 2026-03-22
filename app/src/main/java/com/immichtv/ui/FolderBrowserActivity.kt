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
import com.immichtv.ui.presenters.TextCardPresenter
import kotlinx.coroutines.launch

class FolderBrowserActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, FolderFragment())
                .commit()
        }
    }
}

class FolderFragment : BrowseSupportFragment() {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Folders"
        headersState = HEADERS_DISABLED
        brandColor = Color.parseColor("#0f3460")
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        adapter = rowsAdapter
        setupClickListener()
        loadFolders()
    }

    private fun setupClickListener() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is FolderEntry) {
                if (item.isFolder) {
                    val intent = Intent(requireContext(), BrowseGridActivity::class.java).apply {
                        putExtra(BrowseGridActivity.EXTRA_MODE, BrowseGridActivity.MODE_FOLDER)
                        putExtra(BrowseGridActivity.EXTRA_ID, item.path)
                        putExtra(BrowseGridActivity.EXTRA_TITLE, item.displayName)
                    }
                    startActivity(intent)
                }
            }
        }
    }

    private fun loadFolders() {
        lifecycleScope.launch {
            try {
                val folders = ImmichClient.getApi().getFolderTree()
                val presenter = TextCardPresenter()
                val listAdapter = ArrayObjectAdapter(presenter)
                flattenFolders(folders, "", listAdapter)
                val header = HeaderItem("All Folders")
                rowsAdapter.add(ListRow(header, listAdapter))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Folders error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun flattenFolders(nodes: List<FolderNode>, prefix: String, adapter: ArrayObjectAdapter) {
        nodes.forEach { node ->
            val displayName = if (prefix.isBlank()) node.path else node.path.removePrefix(prefix).trimStart('/')
            adapter.add(FolderEntry(
                path = node.path,
                displayName = displayName.ifBlank { node.path },
                isFolder = true
            ))
            node.children?.let { flattenFolders(it, node.path, adapter) }
        }
    }
}

data class FolderEntry(
    val path: String,
    val displayName: String,
    val isFolder: Boolean
)
