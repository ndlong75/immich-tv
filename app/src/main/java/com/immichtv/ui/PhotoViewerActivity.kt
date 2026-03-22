package com.immichtv.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.immichtv.R
import com.immichtv.api.Asset
import com.immichtv.api.ExifInfo
import com.immichtv.api.ImmichClient
import com.immichtv.api.UpdateAssetsRequest
import com.immichtv.util.PrefsManager
import kotlinx.coroutines.launch

class PhotoViewerActivity : FragmentActivity() {

    companion object {
        const val EXTRA_ASSET_ID = "asset_id"
        const val EXTRA_ASSET_IDS = "asset_ids"
        const val EXTRA_START_INDEX = "start_index"
    }

    private lateinit var imageView: ImageView
    private lateinit var infoOverlay: TextView
    private lateinit var counterOverlay: TextView
    private lateinit var exifPanel: LinearLayout
    private lateinit var exifText: TextView
    private lateinit var favoriteIndicator: TextView
    private lateinit var facesOverlay: TextView
    private lateinit var slideshowIndicator: TextView

    private var assetIds: List<String> = emptyList()
    private var currentIndex = 0
    private var showOverlay = true
    private var showExif = false
    private var slideshowRunning = false
    private var currentAsset: Asset? = null

    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlayRunnable = Runnable { fadeOutOverlay() }
    private val slideshowRunnable = object : Runnable {
        override fun run() {
            if (slideshowRunning && currentIndex < assetIds.size - 1) {
                currentIndex++
                loadCurrentPhoto()
                handler.postDelayed(this, PrefsManager.slideshowIntervalSeconds * 1000L)
            } else {
                slideshowRunning = false
                slideshowIndicator.visibility = View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_photo_viewer)

        imageView = findViewById(R.id.photo_image)
        infoOverlay = findViewById(R.id.info_overlay)
        counterOverlay = findViewById(R.id.counter_overlay)
        exifPanel = findViewById(R.id.exif_panel)
        exifText = findViewById(R.id.exif_text)
        favoriteIndicator = findViewById(R.id.favorite_indicator)
        facesOverlay = findViewById(R.id.faces_overlay)
        slideshowIndicator = findViewById(R.id.slideshow_indicator)

        val ids = intent.getStringArrayListExtra(EXTRA_ASSET_IDS)
        val singleId = intent.getStringExtra(EXTRA_ASSET_ID)
        assetIds = ids ?: if (singleId != null) listOf(singleId) else emptyList()
        currentIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
            .coerceIn(0, (assetIds.size - 1).coerceAtLeast(0))

        showOverlay = PrefsManager.showInfoOverlay

        if (assetIds.isNotEmpty()) {
            loadCurrentPhoto()
        } else {
            finish()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                navigateNext(); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                navigatePrevious(); true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                toggleOverlay(); true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                toggleExifPanel(); true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                toggleSlideshow(); true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                toggleSlideshow(); true
            }
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BOOKMARK -> {
                toggleFavorite(); true
            }
            KeyEvent.KEYCODE_MENU -> {
                toggleExifPanel(); true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (showExif) { toggleExifPanel(); true }
                else if (slideshowRunning) { stopSlideshow(); true }
                else { finish(); true }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun navigateNext() {
        if (slideshowRunning) stopSlideshow()
        if (currentIndex < assetIds.size - 1) {
            currentIndex++
            loadCurrentPhoto()
        }
    }

    private fun navigatePrevious() {
        if (slideshowRunning) stopSlideshow()
        if (currentIndex > 0) {
            currentIndex--
            loadCurrentPhoto()
        }
    }

    private fun loadCurrentPhoto() {
        val assetId = assetIds[currentIndex]
        val headers = ImmichClient.authHeaders()

        val previewUrl = ImmichClient.thumbnailUrl(assetId)
        val originalUrl = ImmichClient.originalUrl(assetId)

        val previewGlideUrl = GlideUrl(previewUrl, LazyHeaders.Builder().apply {
            headers.forEach { (key, value) -> addHeader(key, value) }
        }.build())

        val originalGlideUrl = GlideUrl(originalUrl, LazyHeaders.Builder().apply {
            headers.forEach { (key, value) -> addHeader(key, value) }
        }.build())

        Glide.with(this).clear(imageView)

        Glide.with(this)
            .load(originalGlideUrl)
            .thumbnail(
                Glide.with(this)
                    .load(previewGlideUrl)
                    .transform(FitCenter())
            )
            .transform(FitCenter())
            .placeholder(ColorDrawable(Color.BLACK))
            .error(ColorDrawable(Color.DKGRAY))
            .into(imageView)

        updateOverlay()
        loadAssetDetails(assetId)
    }

    private fun loadAssetDetails(assetId: String) {
        lifecycleScope.launch {
            try {
                val asset = ImmichClient.getApi().getAssetInfo(assetId)
                currentAsset = asset
                updateFavoriteIndicator(asset.isFavorite)
                if (showExif) updateExifPanel(asset)
                loadFaces(assetId)
            } catch (_: Exception) {}
        }
    }

    private fun loadFaces(assetId: String) {
        lifecycleScope.launch {
            try {
                val faces = ImmichClient.getApi().getAssetFaces(assetId)
                val named = faces.filter { it.person?.name?.isNotBlank() == true }
                if (named.isNotEmpty()) {
                    val names = named.mapNotNull { it.person?.name }.distinct().joinToString(", ")
                    facesOverlay.text = names
                    facesOverlay.visibility = View.VISIBLE
                } else {
                    facesOverlay.visibility = View.GONE
                }
            } catch (_: Exception) {
                facesOverlay.visibility = View.GONE
            }
        }
    }

    private fun toggleFavorite() {
        val assetId = assetIds[currentIndex]
        val newFav = !(currentAsset?.isFavorite ?: false)
        lifecycleScope.launch {
            try {
                ImmichClient.getApi().updateAssets(
                    UpdateAssetsRequest(ids = listOf(assetId), isFavorite = newFav)
                )
                currentAsset = currentAsset?.copy(isFavorite = newFav)
                updateFavoriteIndicator(newFav)
            } catch (_: Exception) {}
        }
    }

    private fun updateFavoriteIndicator(isFav: Boolean) {
        favoriteIndicator.text = if (isFav) "\u2665" else "\u2661"  // ♥ vs ♡
        favoriteIndicator.setTextColor(
            if (isFav) Color.parseColor("#EF4444") else Color.WHITE
        )
        favoriteIndicator.visibility = View.VISIBLE
    }

    private fun toggleExifPanel() {
        showExif = !showExif
        if (showExif) {
            currentAsset?.let { updateExifPanel(it) }
            exifPanel.visibility = View.VISIBLE
        } else {
            exifPanel.visibility = View.GONE
        }
    }

    private fun updateExifPanel(asset: Asset) {
        val exif = asset.exifInfo
        val sb = StringBuilder()
        sb.appendLine(asset.originalFileName)
        if (asset.localDateTime != null) sb.appendLine("Date: ${asset.localDateTime.replace("T", " ").take(19)}")
        if (exif != null) {
            if (exif.make != null || exif.model != null) {
                sb.appendLine("Camera: ${listOfNotNull(exif.make, exif.model).joinToString(" ")}")
            }
            if (exif.lensModel != null) sb.appendLine("Lens: ${exif.lensModel}")
            val settings = listOfNotNull(
                exif.fNumber?.let { "f/$it" },
                exif.exposureTime?.let { "${it}s" },
                exif.focalLength?.let { "${it}mm" },
                exif.iso?.let { "ISO $it" }
            )
            if (settings.isNotEmpty()) sb.appendLine(settings.joinToString("  "))
            val location = listOfNotNull(exif.city, exif.state, exif.country)
            if (location.isNotEmpty()) sb.appendLine("Location: ${location.joinToString(", ")}")
            if (exif.fileSizeInByte != null) {
                val mb = exif.fileSizeInByte / (1024.0 * 1024.0)
                sb.appendLine("Size: ${"%.1f".format(mb)} MB")
            }
            if (exif.description?.isNotBlank() == true) sb.appendLine("\"${exif.description}\"")
        }
        exifText.text = sb.toString().trim()
    }

    private fun toggleSlideshow() {
        if (slideshowRunning) {
            stopSlideshow()
        } else {
            slideshowRunning = true
            slideshowIndicator.text = "\u25B6 Slideshow"
            slideshowIndicator.visibility = View.VISIBLE
            handler.postDelayed(slideshowRunnable, PrefsManager.slideshowIntervalSeconds * 1000L)
        }
    }

    private fun stopSlideshow() {
        slideshowRunning = false
        handler.removeCallbacks(slideshowRunnable)
        slideshowIndicator.visibility = View.GONE
    }

    private fun updateOverlay() {
        if (showOverlay) {
            counterOverlay.text = "${currentIndex + 1} / ${assetIds.size}"
            counterOverlay.visibility = View.VISIBLE
            infoOverlay.text = "\u2190\u2192 Navigate  \u2191 EXIF  \u2193 Slideshow  A/\u2606 Fav  Back Exit"
            infoOverlay.visibility = View.VISIBLE
            handler.removeCallbacks(hideOverlayRunnable)
            handler.postDelayed(hideOverlayRunnable, 4000)
        }
    }

    private fun toggleOverlay() {
        showOverlay = !showOverlay
        if (showOverlay) updateOverlay() else fadeOutOverlay()
    }

    private fun fadeOutOverlay() {
        infoOverlay.animate().alpha(0f).setDuration(300).withEndAction {
            infoOverlay.visibility = View.GONE; infoOverlay.alpha = 1f
        }
        counterOverlay.animate().alpha(0f).setDuration(300).withEndAction {
            counterOverlay.visibility = View.GONE; counterOverlay.alpha = 1f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
