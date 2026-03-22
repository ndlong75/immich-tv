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

class MapActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_frame, MapFragment())
                .commit()
        }
    }
}

class MapFragment : BrowseSupportFragment() {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Locations"
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = Color.parseColor("#0f3460")
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        adapter = rowsAdapter
        setupClickListener()
        loadLocations()
    }

    private fun setupClickListener() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is LocationEntry) {
                val intent = Intent(requireContext(), BrowseGridActivity::class.java).apply {
                    putExtra(BrowseGridActivity.EXTRA_MODE, BrowseGridActivity.MODE_SEARCH)
                    putExtra(BrowseGridActivity.EXTRA_ID, item.city ?: item.country ?: "")
                    putExtra(BrowseGridActivity.EXTRA_TITLE, item.displayName)
                    putExtra(BrowseGridActivity.EXTRA_SEARCH_FIELD, "exifInfo.city")
                }
                startActivity(intent)
            }
        }
    }

    private fun loadLocations() {
        lifecycleScope.launch {
            try {
                val markers = ImmichClient.getApi().getMapMarkers()

                // Group by country -> city
                val byCountry = markers
                    .filter { !it.country.isNullOrBlank() }
                    .groupBy { it.country ?: "Unknown" }
                    .toSortedMap()

                var rowId = 0L
                byCountry.forEach { (country, countryMarkers) ->
                    val byCityMap = countryMarkers
                        .groupBy { it.city ?: country }
                        .toSortedMap()

                    val presenter = TextCardPresenter()
                    val listAdapter = ArrayObjectAdapter(presenter)

                    byCityMap.forEach { (city, cityMarkers) ->
                        listAdapter.add(LocationEntry(
                            city = city,
                            country = country,
                            displayName = city,
                            photoCount = cityMarkers.size,
                            sampleAssetId = cityMarkers.firstOrNull()?.id
                        ))
                    }

                    val header = HeaderItem(rowId++, "\uD83C\uDF0D $country (${countryMarkers.size} photos)")
                    rowsAdapter.add(ListRow(header, listAdapter))
                }

                if (byCountry.isEmpty()) {
                    Toast.makeText(requireContext(), "No location data found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Map error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

data class LocationEntry(
    val city: String?,
    val country: String?,
    val displayName: String,
    val photoCount: Int,
    val sampleAssetId: String?
)
