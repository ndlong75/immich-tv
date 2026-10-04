package nl.giejay.android.tv.immich.slider

import nl.giejay.android.tv.immich.api.ApiClientFactory
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.timeline.TimelineJump
import nl.giejay.android.tv.immich.timeline.TimelineLeaveOff
import nl.giejay.android.tv.immich.timeline.TimelineViewModel
import nl.giejay.android.tv.immich.timeline.TimelineViewModelFactory
import nl.giejay.mediaslider.model.SliderInfo
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderPerson
import nl.giejay.mediaslider.plugin.InfoPanelPlugin
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.plugin.TimelineStoryProgressPlugin
import nl.giejay.mediaslider.view.MediaSliderFragment
import nl.giejay.mediaslider.view.MediaSliderView
import timber.log.Timber

class ImmichMediaSlider : MediaSliderFragment() {
    private val favoriteService = FavoriteService()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return MediaSliderView(requireContext())
    }

    private fun showInTimeline(item: SliderItem, apiClient: ApiClient) {
        val takenAt = item.takenAt
        if (takenAt == null) {
            Toast.makeText(requireContext(), "This photo has no date", Toast.LENGTH_SHORT).show()
            return
        }
        val day = Instant.ofEpochMilli(takenAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val timelineViewModel = ViewModelProvider(requireActivity(), TimelineViewModelFactory(apiClient))[TimelineViewModel::class.java]
        timelineViewModel.rememberSelection(day.toString(), item.id)
        timelineViewModel.applyLeaveOffSnapshot(
            TimelineLeaveOff.Snapshot(memoryId = null, pendingAssetId = item.id, lastAssetId = item.id, allowScrollAdjust = true)
        )
        TimelineJump.requested = true
        TimelineJump.scrollPending = true
        // A fresh Home always starts on its first page (Timeline); the old instance kept the People tab.
        findNavController().navigate(
            R.id.homeFragment,
            null,
            NavOptions.Builder().setPopUpTo(R.id.homeFragment, true).build()
        )
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.i("Loading ${this.javaClass.simpleName}")

        val bundle = ImmichMediaSliderArgs.fromBundle(requireArguments())
        val config = sliderViewModel.configuration

        if (config == null || config.items.isEmpty()) {
            Timber.i("No items to play for photoslider")
            Toast.makeText(requireContext(), getString(R.string.no_items_to_play), Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        setDefaultExoFactory(
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(ApiClientFactory.authHeaders(PreferenceManager.get(API_KEY)))
        )

        if (bundle.timelineView) {
            val timelinePlugin = TimelineStoryProgressPlugin()
            config.viewPlugins += timelinePlugin
            config.controllerPlugins += timelinePlugin
            config.keyEventPlugins += timelinePlugin
        }

        val enabledPlugins = PreferenceManager.createEnabledSliderPlugins(lifecycleScope, favoriteService)
        config.controllerPlugins += enabledPlugins.controllerPlugins
        config.viewPlugins += enabledPlugins.viewPlugins
        config.keyEventPlugins += enabledPlugins.keyEventPlugins

        val apiClient = ApiClient.getClient(ApiClientConfig.fromPrefs())
        val infoPanel = InfoPanelPlugin(
            loadInfo = { item ->
                val asset = apiClient.getAsset(item.id).getOrNull()
                SliderInfo(
                    people = asset?.people.orEmpty().map { SliderPerson(it.id.toString(), it.name) },
                    lines = asset?.let { AssetInfoFormatter.lines(it) }.orEmpty()
                )
            },
            avatarUrl = { ApiUtil.getPersonThumbnail(UUID.fromString(it.id)) },
            onPerson = { person ->
                findNavController().navigate(
                    R.id.personAssetsFragment,
                    bundleOf("personId" to person.id, "personName" to (person.name ?: "Unknown"))
                )
            },
            onShowInTimeline = { showInTimeline(it, apiClient) }
        )
        config.viewPlugins += infoPanel
        config.keyEventPlugins = listOf(infoPanel) + config.keyEventPlugins

        loadMediaSliderView(config)

        if (bundle.timelineView) {
            // Memories: timeline plugin mounts story progress; start autoplay.
            (view as MediaSliderView).toggleSlideshow(false)
        }
    }
}
