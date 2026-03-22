package com.immichtv.ui

import android.content.Intent
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
import com.immichtv.api.*
import com.immichtv.util.PrefsManager
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

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
    private var currentPhotoDate: String? = null  // for jump-to-timeline

    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlayRunnable = Runnable { fadeOutOverlay() }
    private val slideshowRunnable = object : Runnable {
        override fun run() {
            if (slideshowRunning && currentIndex < assetIds.size - 1) {
                currentIndex++
                loadCurrentPhoto()
                handler.postDelayed(this, PrefsManager.slideshowIntervalSeconds * 1000L)
            } else { slideshowRunning = false; slideshowIndicator.visibility = View.GONE }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
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

        if (assetIds.isNotEmpty()) loadCurrentPhoto() else finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { navigateNext(); true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { navigatePrevious(); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { toggleOverlay(); true }
            KeyEvent.KEYCODE_DPAD_UP -> { toggleExifPanel(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { toggleSlideshow(); true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { toggleSlideshow(); true }
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BOOKMARK -> { toggleFavorite(); true }
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_T -> { jumpToTimeline(); true }
            KeyEvent.KEYCODE_MENU -> { toggleExifPanel(); true }
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
        if (currentIndex < assetIds.size - 1) { currentIndex++; loadCurrentPhoto() }
    }
    private fun navigatePrevious() {
        if (slideshowRunning) stopSlideshow()
        if (currentIndex > 0) { currentIndex--; loadCurrentPhoto() }
    }

    private fun loadCurrentPhoto() {
        val assetId = assetIds[currentIndex]
        val headers = ImmichClient.authHeaders()
        val previewUrl = ImmichClient.thumbnailUrl(assetId)
        val originalUrl = ImmichClient.originalUrl(assetId)

        val previewGlide = GlideUrl(previewUrl, LazyHeaders.Builder().apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.build())
        val originalGlide = GlideUrl(originalUrl, LazyHeaders.Builder().apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.build())

        Glide.with(this).clear(imageView)
        Glide.with(this)
            .load(originalGlide)
            .thumbnail(Glide.with(this).load(previewGlide).transform(FitCenter()))
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
                currentPhotoDate = asset.localDateTime?.take(10)  // "YYYY-MM-DD"
                updateFavoriteIndicator(asset.isFavorite)
                if (showExif) updateExifPanel(asset)
                loadFacesWithAge(assetId, asset.localDateTime)
            } catch (_: Exception) {}
        }
    }

    private fun loadFacesWithAge(assetId: String, photoDate: String?) {
        lifecycleScope.launch {
            try {
                val faces = ImmichClient.getApi().getAssetFaces(assetId)
                val named = faces.filter { it.person?.name?.isNotBlank() == true }
                if (named.isNotEmpty()) {
                    val labels = named.mapNotNull { face ->
                        val person = face.person ?: return@mapNotNull null
                        val name = person.name
                        val age = calculateAge(person.birthDate, photoDate)
                        if (age != null) "$name ($age)" else name
                    }.distinct()
                    facesOverlay.text = labels.joinToString("  \u2022  ")
                    facesOverlay.visibility = View.VISIBLE
                } else {
                    facesOverlay.visibility = View.GONE
                }
            } catch (_: Exception) { facesOverlay.visibility = View.GONE }
        }
    }

    private fun calculateAge(birthDate: String?, photoDate: String?): String? {
        if (birthDate.isNullOrBlank() || photoDate.isNullOrBlank()) return null
        return try {
            val birth = LocalDate.parse(birthDate.take(10))
            val photo = LocalDate.parse(photoDate.take(10))
            val period = Period.between(birth, photo)
            if (period.years > 0) "${period.years}y"
            else if (period.months > 0) "${period.months}m"
            else "${period.days}d"
        } catch (_: Exception) { null }
    }

    private fun toggleFavorite() {
        val assetId = assetIds[currentIndex]
        val newFav = !(currentAsset?.isFavorite ?: false)
        lifecycleScope.launch {
            try {
                ImmichClient.getApi().updateAssets(UpdateAssetsRequest(ids = listOf(assetId), isFavorite = newFav))
                currentAsset = currentAsset?.copy(isFavorite = newFav)
                updateFavoriteIndicator(newFav)
            } catch (_: Exception) {}
        }
    }

    private fun jumpToTimeline() {
        val date = currentPhotoDate ?: return
        startActivity(Intent(this, BrowseGridActivity::class.java).apply {
            putExtra(BrowseGridActivity.EXTRA_MODE, BrowseGridActivity.MODE_DATE)
            putExtra(BrowseGridActivity.EXTRA_ID, date)
            putExtra(BrowseGridActivity.EXTRA_TITLE, "Photos from $date")
        })
    }

    private fun updateFavoriteIndicator(isFav: Boolean) {
        favoriteIndicator.text = if (isFav) "\u2665" else "\u2661"
        favoriteIndicator.setTextColor(if (isFav) Color.parseColor("#EF4444") else Color.WHITE)
        favoriteIndicator.visibility = View.VISIBLE
    }

    private fun toggleExifPanel() {
        showExif = !showExif
        if (showExif) { currentAsset?.let { updateExifPanel(it) }; exifPanel.visibility = View.VISIBLE }
        else exifPanel.visibility = View.GONE
    }

    private fun updateExifPanel(asset: Asset) {
        val exif = asset.exifInfo
        val sb = StringBuilder()
        sb.appendLine(asset.originalFileName)
        if (asset.localDateTime != null) sb.appendLine("Date: ${asset.localDateTime.replace("T", " ").take(19)}")
        if (exif != null) {
            if (exif.make != null || exif.model != null)
                sb.appendLine("Camera: ${listOfNotNull(exif.make, exif.model).joinToString(" ")}")
            if (exif.lensModel != null) sb.appendLine("Lens: ${exif.lensModel}")
            val settings = listOfNotNull(
                exif.fNumber?.let { "f/$it" }, exif.exposureTime?.let { "${it}s" },
                exif.focalLength?.let { "${it}mm" }, exif.iso?.let { "ISO $it" }
            )
            if (settings.isNotEmpty()) sb.appendLine(settings.joinToString("  "))
            val loc = listOfNotNull(exif.city, exif.state, exif.country)
            if (loc.isNotEmpty()) sb.appendLine("Location: ${loc.joinToString(", ")}")
            if (exif.fileSizeInByte != null) sb.appendLine("Size: ${"%.1f".format(exif.fileSizeInByte / 1048576.0)} MB")
            if (exif.description?.isNotBlank() == true) sb.appendLine("\"${exif.description}\"")
        }
        sb.appendLine("\n\u2190\u2192 Navigate  \u2191 Close  \u2193 Slideshow")
        sb.appendLine("A Favorite  B Jump to timeline")
        exifText.text = sb.toString().trim()
    }

    private fun toggleSlideshow() {
        if (slideshowRunning) stopSlideshow()
        else {
            slideshowRunning = true
            slideshowIndicator.text = "\u25B6 Slideshow"
            slideshowIndicator.visibility = View.VISIBLE
            handler.postDelayed(slideshowRunnable, PrefsManager.slideshowIntervalSeconds * 1000L)
        }
    }
    private fun stopSlideshow() {
        slideshowRunning = false; handler.removeCallbacks(slideshowRunnable)
        slideshowIndicator.visibility = View.GONE
    }

    private fun updateOverlay() {
        if (showOverlay) {
            counterOverlay.text = "${currentIndex + 1} / ${assetIds.size}"
            counterOverlay.visibility = View.VISIBLE
            infoOverlay.text = "\u2190\u2192 Nav  \u2191 EXIF  \u2193 Slideshow  A Fav  B Timeline"
            infoOverlay.visibility = View.VISIBLE
            handler.removeCallbacks(hideOverlayRunnable)
            handler.postDelayed(hideOverlayRunnable, 4000)
        }
    }
    private fun toggleOverlay() { showOverlay = !showOverlay; if (showOverlay) updateOverlay() else fadeOutOverlay() }
    private fun fadeOutOverlay() {
        infoOverlay.animate().alpha(0f).setDuration(300).withEndAction { infoOverlay.visibility = View.GONE; infoOverlay.alpha = 1f }
        counterOverlay.animate().alpha(0f).setDuration(300).withEndAction { counterOverlay.visibility = View.GONE; counterOverlay.alpha = 1f }
    }

    override fun onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null) }
}
