package nl.giejay.android.tv.immich.slider

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.runBlocking
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.view.MediaSliderFragment
import timber.log.Timber

class ImmichMediaSlider : MediaSliderFragment() {

    @SuppressLint("UnsafeOptInUsageError")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.i("Loading ${this.javaClass.simpleName}")

        val bundle = ImmichMediaSliderArgs.fromBundle(requireArguments())

        if (bundle.config.items.isEmpty()) {
            Timber.i("No items to play for photoslider")
            Toast.makeText(requireContext(), getString(R.string.no_items_to_play), Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        val apiKey = PreferenceManager.get(API_KEY)
        val headers = if (apiKey.startsWith("Bearer:")) {
            mapOf("Authorization" to "Bearer " + apiKey.removePrefix("Bearer:"))
        } else {
            mapOf("x-api-key" to apiKey)
        }

        setDefaultExoFactory(
            DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
        )

        // Set up Menu button handler to show people + date info
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                showPhotoInfo()
                true
            } else {
                false
            }
        }

        loadMediaSliderView(bundle.config)
    }

    private fun showPhotoInfo() {
        try {
            val config = ImmichMediaSliderArgs.fromBundle(requireArguments()).config
            val currentIndex = view?.let {
                val field = it.javaClass.getDeclaredField("mPager")
                field.isAccessible = true
                val pager = field.get(it) as? androidx.viewpager.widget.ViewPager
                pager?.currentItem ?: 0
            } ?: 0

            if (currentIndex < config.items.size) {
                val item = config.items[currentIndex].mainItem
                val parts = mutableListOf<String>()

                // Get metadata synchronously for Toast
                runBlocking {
                    item.get(MetaDataType.PEOPLE)?.let { if (it.isNotBlank()) parts.add("👤 $it") }
                    item.get(MetaDataType.DATE)?.let { if (it.isNotBlank()) parts.add("📅 $it") }
                    item.get(MetaDataType.CITY)?.let { if (it.isNotBlank()) parts.add("📍 $it") }
                    item.get(MetaDataType.CAMERA)?.let { if (it.isNotBlank()) parts.add("📷 $it") }
                }

                val info = if (parts.isNotEmpty()) parts.joinToString("\n") else "No info available"
                Toast.makeText(requireContext(), info, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not show photo info")
        }
    }
}
