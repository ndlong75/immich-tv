package nl.giejay.android.tv.immich.assets

import android.app.AlertDialog
import android.os.Bundle
import java.time.ZoneId
import java.time.YearMonth
import java.time.LocalTime
import java.time.LocalDateTime
import nl.giejay.android.tv.immich.timeline.TimelineScrubberView
import nl.giejay.android.tv.immich.timeline.TimelineDatePicker
import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.R
import android.widget.FrameLayout
import android.view.ViewGroup
import android.view.Gravity
import android.app.Dialog
import android.widget.Toast
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import arrow.core.Either
import arrow.core.getOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.album.AlbumDetailsFragmentDirections
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.AssetResponse
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment
import nl.giejay.android.tv.immich.shared.prefs.ALL_ASSETS_SORTING
import nl.giejay.android.tv.immich.shared.prefs.ContentType
import nl.giejay.android.tv.immich.shared.prefs.EXCLUDE_ASSETS_IN_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.EnumByTitlePref
import nl.giejay.android.tv.immich.shared.prefs.FILTER_CONTENT_TYPE
import nl.giejay.android.tv.immich.shared.prefs.MetaDataScreen
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ANIMATE_ASSET_SLIDE
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ANIMATION_SPEED
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_FORCE_ORIGINAL_VIDEO
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_GLIDE_TRANSFORMATION
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_INTERVAL
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_DPAD_SEEK_IN_VIDEO
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_HEIGHT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_WIDTH
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MERGE_PORTRAIT_PHOTOS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ONLY_USE_THUMBNAILS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_PAN_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_SHOW_DATE_TOP_LEFT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_SCROLL_PANORAMAS
import nl.giejay.android.tv.immich.shared.util.Utils.pmap
import nl.giejay.android.tv.immich.shared.util.toCard
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.util.LoadMore
import nl.giejay.mediaslider.util.LoadMoreResult
import nl.giejay.mediaslider.viewmodel.MediaSliderViewModel

abstract class GenericAssetFragment : VerticalCardGridFragment<Asset>() {
    protected lateinit var currentFilter: ContentType
    protected lateinit var currentSort: PhotosOrder
    private var excludedAssetsLoaded = false
    protected var excludedAssetIds: Set<String> = emptySet()

    private val sliderViewModel: MediaSliderViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val sortingKey = getSortingKey()
        val filterKey = getFilterKey()
        currentSort = PreferenceManager.get(sortingKey)
        currentFilter = PreferenceManager.get(filterKey)
        super.onCreate(savedInstanceState)
        PreferenceManager.subscribeMultiple(listOf(sortingKey, filterKey)) { state ->
            if(state[sortingKey.key()] != currentSort || state[filterKey.key()] != currentFilter){
                jumpMonth = null
                clearState()
                currentSort = state[sortingKey.key()] as PhotosOrder
                currentFilter = state[filterKey.key()] as ContentType
                fetchInitialItems()
            }
        }
    }

    override fun filterItems(items: List<Asset>): List<Asset> {
        return items.filter { currentFilter ==  ContentType.ALL || it.type.equals(currentFilter.toString(), ignoreCase = true) }
            .filter(excludeByTag())
            .filterNot { excludedAssetIds.contains(it.id) }
    }

    private fun excludeByTag() = { asset: Asset ->
        (asset.tags?.none { t -> t.name == "exclude_immich_tv" } ?: true) && asset.visibility != "archive" && asset.isArchived != true
    }

    override fun onItemLongClicked(card: Card) {
        AlertDialog.Builder(requireContext())
            .setItems(arrayOf("Archive")) { _, _ -> archive(card) }
            .show()
    }

    private fun archive(card: Card) {
        lifecycleScope.launch {
            apiClient.archiveAsset(card.id).fold(
                { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() },
                {
                    removeItem(card) { a -> a.id == card.id }
                    Toast.makeText(requireContext(), "Archived", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    override suspend fun loadData(): Either<String, List<Asset>> {
        if (!excludedAssetsLoaded) {
            val excludedAlbums = PreferenceManager.get(EXCLUDE_ASSETS_IN_ALBUM)
            if (excludedAlbums.isNotEmpty()) {
                excludedAssetIds = excludedAlbums.toList().pmap {
                    apiClient.listAssetsFromAlbum(listOf(it), pageCount = 1000)
                        .getOrElse { AssetResponse(emptyList(), false) }
                        .assets
                        .map { it -> it.id }
                }.flatten().toSet()
            }
            excludedAssetsLoaded = true
        }
        return super.loadData()
    }

    open fun getSortingKey(): EnumByTitlePref<PhotosOrder>{
        return ALL_ASSETS_SORTING
    }

    open fun getFilterKey(): EnumByTitlePref<ContentType>{
        return FILTER_CONTENT_TYPE
    }

    override fun sortItems(items: List<Asset>): List<Asset> {
        return items.sortedWith(currentSort.sort)
    }

    override fun onItemSelected(card: Card, indexOf: Int) {
        val scrubber = dateScrubber ?: return
        if (scrubber.hasFocus()) return
        val asset = assets.firstOrNull { it.id == card.id } ?: return
        val date = asset.exifInfo?.dateTimeOriginal ?: asset.fileCreatedAt ?: asset.fileModifiedAt ?: return
        scrubber.setIndicatorMonthKey("${YearMonth.from(date.toInstant().atZone(ZoneId.systemDefault()))}-01")
    }

    // ---- Date navigation (chronological lists): year/month picker on Menu + right-edge month rail.

    /** Month buckets for this list, newest first; null (default) turns date navigation off. */
    protected open suspend fun loadDateBuckets(apiClient: ApiClient): List<TimeBucketSummary>? = null

    /** When set, the list restarts at this month (set by the picker / rail). */
    protected var jumpMonth: YearMonth? = null

    /** `takenAfter` for an oldest-first list that was jumped to [jumpMonth]. */
    protected fun jumpFrom(): LocalDateTime? =
        jumpMonth?.takeIf { currentSort == PhotosOrder.OLDEST_NEWEST }?.atDay(1)?.atStartOfDay()

    /** `takenBefore` for a newest-first list that was jumped to [jumpMonth]. */
    protected fun jumpTo(): LocalDateTime? =
        jumpMonth?.takeIf { currentSort == PhotosOrder.NEWEST_OLDEST }?.atEndOfMonth()?.atTime(LocalTime.MAX)

    private var dateBuckets: List<TimeBucketSummary> = emptyList()
    private var dateScrubber: TimelineScrubberView? = null
    private var datePicker: Dialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!PreferenceManager.isLoggedId()) return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val buckets = loadDateBuckets(apiClient)?.takeIf { it.isNotEmpty() } ?: return@launch
            withContext(Dispatchers.Main) {
                if (isAdded && dateScrubber == null) installDateScrubber(view, buckets)
            }
        }
    }

    private fun installDateScrubber(root: View, buckets: List<TimeBucketSummary>) {
        dateBuckets = buckets
        val scrubber = TimelineScrubberView(requireContext())
        scrubber.setBuckets(buckets)
        scrubber.onCommit = { monthKey, exitToGrid ->
            jumpToMonth(monthKey)
            if (exitToGrid) focusGrid()
        }
        scrubber.onRightEdge = { openSettings() }
        val width = (80 * resources.displayMetrics.density).toInt()
        (root as ViewGroup).addView(scrubber, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
        dateScrubber = scrubber
    }

    private fun jumpToMonth(bucketKey: String) {
        jumpMonth = runCatching { YearMonth.parse(bucketKey.take(7)) }.getOrNull() ?: return
        clearState()
        fetchInitialItems()
    }

    private fun focusGrid() {
        view?.findViewById<View>(R.id.browse_grid_dock)?.requestFocus()
    }

    override fun onMenuKey() {
        if (dateBuckets.isEmpty() || datePicker?.isShowing == true) return
        datePicker = TimelineDatePicker.show(requireContext(), dateBuckets) { bucketKey ->
            jumpToMonth(bucketKey)
            focusGrid()
        }
    }

    open fun showMediaCount(): Boolean {
        return false
    }

    /** Right edge of the grid: go to the month rail when there is one, otherwise open the settings. */
    override fun openPopUpMenu() {
        val scrubber = dateScrubber
        if (scrubber != null && !scrubber.hasFocus()) scrubber.requestFocus() else openSettings()
    }

    protected open fun openSettings() {
        findNavController().navigate(
            HomeFragmentDirections.actionGlobalToSettingsDialog("generic_asset_settings")
        )
    }

    /**
     * Handles clicking an asset card to open the photo slider.
     *
     * Similar to the Timeline, preparing the full list of slider items (potentially
     * thousands) is done on a background thread to keep the UI responsive and
     * provide immediate visual feedback via a loading spinner.
     */
    override fun onItemClicked(card: Card) {
        progressBar?.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {
            val toSliderItems = assets.toSliderItems(
                keepOrder = true,
                mergePortrait = PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS)
            )
            val loadMore: LoadMore = suspend {
                val moreAssets = loadMoreAssets()
                // also load the data in the overview
                setDataOnMain(moreAssets)
                LoadMoreResult(
                    moreAssets.toSliderItems(true, PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS)),
                    moreAssets.isNotEmpty() && !allPagesLoaded
                )
            }

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                progressBar?.visibility = View.GONE
                val config = MediaSliderConfiguration(
                    toSliderItems.indexOfFirst { it.ids().contains(card.id) },
                    PreferenceManager.get(SLIDER_INTERVAL),
                    PreferenceManager.get(SLIDER_ONLY_USE_THUMBNAILS),
                    isVideoSoundEnable = true,
                    toSliderItems,
                    loadMore,
                    { item -> manualUpdatePosition(this@GenericAssetFragment.assets.indexOfFirst { item.ids().contains(it.id) }) },
                    animationSpeedMillis = PreferenceManager.get(SLIDER_ANIMATION_SPEED),
                    maxCutOffHeight = PreferenceManager.get(SLIDER_MAX_CUT_OFF_HEIGHT),
                    maxCutOffWidth = PreferenceManager.get(SLIDER_MAX_CUT_OFF_WIDTH),
                    glideTransformation = PreferenceManager.get(SLIDER_GLIDE_TRANSFORMATION),
                    enableSlideAnimation = PreferenceManager.get(SCREENSAVER_ANIMATE_ASSET_SLIDE),
                    gradiantOverlay = false,
                    metaDataConfig = PreferenceManager.getAllMetaData(MetaDataScreen.VIEWER),
                    zoomAndScrollPanorama = PreferenceManager.get(SLIDER_ZOOM_SCROLL_PANORAMAS),
                    zoomEffectPercent = PreferenceManager.get(SLIDER_ZOOM_EFFECT),
                    panEffectPercent = PreferenceManager.get(SLIDER_PAN_EFFECT),
                    useLargeVideoBuffer = PreferenceManager.get(SLIDER_FORCE_ORIGINAL_VIDEO),
                    dpadSeeksInVideo = PreferenceManager.get(SLIDER_DPAD_SEEK_IN_VIDEO),
                    showDateTopLeft = PreferenceManager.get(SLIDER_SHOW_DATE_TOP_LEFT)
                )
                sliderViewModel.configuration = config
                findNavController().navigate(AlbumDetailsFragmentDirections.actionToPhotoSlider())
            }
        }
    }

    override fun getBackgroundPicture(it: Asset): String? {
        return ApiUtil.getFileUrl(it.id, "IMAGE")
    }

    override fun createCard(a: Asset): Card {
        return a.toCard()
    }
}
