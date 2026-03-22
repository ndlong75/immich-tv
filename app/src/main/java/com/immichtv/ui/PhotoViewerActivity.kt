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
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.signature.ObjectKey
import com.immichtv.R
import com.immichtv.api.*
import com.immichtv.util.AssetStore
import com.immichtv.util.PrefsManager
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

/**
 * Full-screen photo viewer. Uses AssetStore singleton for asset list
 * (avoids Intent TransactionTooLargeException).
 *
 * Approach borrowed from giejay/Immich-Android-TV which also avoids
 * passing asset lists through Intent extras.
 */
class PhotoViewerActivity : FragmentActivity() {

    companion object {
        const val EXTRA_ASSET_ID = "asset_id"
    }

    private lateinit var imageView: ImageView
    private lateinit var infoOverlay: TextView
    private lateinit var counterOverlay: TextView
    private lateinit var exifPanel: LinearLayout
    private lateinit var exifText: TextView
    private lateinit var favoriteIndicator: TextView
    private lateinit var facesOverlay: TextView
    private lateinit var slideshowIndicator: TextView
    private lateinit var actionPanel: LinearLayout
    private lateinit var btnFavorite: Button
    private lateinit var btnSlideshow: Button
    private lateinit var btnTimeline: Button
    private lateinit var btnEditPerson: Button
    private lateinit var btnExif: Button

    private var showExif = false
    private var showActions = false
    private var slideshowRunning = false
    private var currentAsset: Asset? = null
    private var currentPhotoDate: String? = null
    private var currentFaces: List<AssetFace> = emptyList()

    private val handler = Handler(Looper.getMainLooper())
    private val hideHintsRunnable = Runnable { fadeOutHints() }
    private val slideshowRunnable = object : Runnable {
        override fun run() {
            if (slideshowRunning) {
                val next = AssetStore.nextIndex()
                if (next != null) {
                    AssetStore.currentIndex = next
                    loadCurrentPhoto()
                    handler.postDelayed(this, PrefsManager.slideshowIntervalSeconds * 1000L)
                } else {
                    stopSlideshow()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
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
            actionPanel = findViewById(R.id.action_panel)
            btnFavorite = findViewById(R.id.btn_favorite)
            btnSlideshow = findViewById(R.id.btn_slideshow)
            btnTimeline = findViewById(R.id.btn_timeline)
            btnEditPerson = findViewById(R.id.btn_edit_person)
            btnExif = findViewById(R.id.btn_exif)

            btnFavorite.setOnClickListener { toggleFavorite(); closeActionPanel() }
            btnSlideshow.setOnClickListener { toggleSlideshow(); closeActionPanel() }
            btnTimeline.setOnClickListener { jumpToTimeline(); closeActionPanel() }
            btnEditPerson.setOnClickListener { editPerson(); closeActionPanel() }
            btnExif.setOnClickListener { toggleExifPanel(); closeActionPanel() }

            // If AssetStore is empty, use the single asset ID from intent
            if (!AssetStore.hasAssets()) {
                val singleId = intent.getStringExtra(EXTRA_ASSET_ID)
                if (singleId != null) {
                    AssetStore.set(listOf(Asset(
                        id = singleId, type = AssetType.IMAGE,
                        originalFileName = "", originalMimeType = null,
                        thumbhash = null, localDateTime = null,
                        fileCreatedAt = null
                    )), 0)
                } else {
                    finish(); return
                }
            }

            loadCurrentPhoto()
            showHints()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FIRE TV STICK REMOTE MAPPING
    // ══════════════════════════════════════════════════════════════════════

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (showActions) {
            return when (keyCode) {
                KeyEvent.KEYCODE_BACK -> { closeActionPanel(); true }
                else -> super.onKeyDown(keyCode, event)
            }
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { navigateNext(); true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { navigatePrevious(); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_DPAD_UP -> { openActionPanel(); true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_DOWN -> { toggleSlideshow(); true }
            KeyEvent.KEYCODE_BACK -> {
                when {
                    showExif -> { toggleExifPanel(); true }
                    slideshowRunning -> { stopSlideshow(); true }
                    else -> { finish(); true }
                }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun navigateNext() {
        if (slideshowRunning) stopSlideshow()
        val next = AssetStore.nextIndex()
        if (next != null) { AssetStore.currentIndex = next; loadCurrentPhoto() }
    }

    private fun navigatePrevious() {
        if (slideshowRunning) stopSlideshow()
        val prev = AssetStore.prevIndex()
        if (prev != null) { AssetStore.currentIndex = prev; loadCurrentPhoto() }
    }

    // ── Photo loading ───────────────────────────────────────────────────

    private fun loadCurrentPhoto() {
        val asset = AssetStore.currentAsset() ?: return
        val assetId = asset.id

        try {
            // Use PREVIEW size (1440px) - same approach as giejay/Immich-Android-TV
            val url = ImmichClient.thumbnailUrl(assetId)
            val headers = ImmichClient.authHeaders()
            val glideUrl = GlideUrl(url, LazyHeaders.Builder().apply {
                headers.forEach { (k, v) -> addHeader(k, v) }
            }.build())

            Glide.with(this).clear(imageView)
            Glide.with(this)
                .load(glideUrl)
                .signature(ObjectKey(assetId))
                .transform(FitCenter())
                .placeholder(ColorDrawable(Color.BLACK))
                .error(ColorDrawable(Color.parseColor("#1a1a2e")))
                .into(imageView)

            counterOverlay.text = "${AssetStore.currentIndex + 1} / ${AssetStore.assets.size}"
            counterOverlay.visibility = View.VISIBLE

            loadAssetDetails(assetId)
        } catch (e: Exception) {
            Toast.makeText(this, "Load error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAssetDetails(assetId: String) {
        lifecycleScope.launch {
            try {
                val asset = ImmichClient.getApi().getAssetInfo(assetId)
                currentAsset = asset
                currentPhotoDate = asset.localDateTime?.take(10)
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
                        val age = calculateAge(person.birthDate, photoDate)
                        if (age != null) "${person.name} ($age)" else person.name
                    }.distinct()
                    facesOverlay.text = labels.joinToString("  \u2022  ")
                    facesOverlay.visibility = View.VISIBLE
                    currentFaces = faces
                } else { facesOverlay.visibility = View.GONE; currentFaces = emptyList() }
            } catch (_: Exception) { facesOverlay.visibility = View.GONE; currentFaces = emptyList() }
        }
    }

    private fun calculateAge(birthDate: String?, photoDate: String?): String? {
        if (birthDate.isNullOrBlank() || photoDate.isNullOrBlank()) return null
        return try {
            val birth = LocalDate.parse(birthDate.take(10))
            val photo = LocalDate.parse(photoDate.take(10))
            val p = Period.between(birth, photo)
            when { p.years > 0 -> "${p.years}y"; p.months > 0 -> "${p.months}m"; else -> "${p.days}d" }
        } catch (_: Exception) { null }
    }

    // ── Action Panel ────────────────────────────────────────────────────

    private fun openActionPanel() {
        showActions = true; actionPanel.visibility = View.VISIBLE
        btnFavorite.text = if (currentAsset?.isFavorite == true) "\u2665 Unfavorite" else "\u2661 Favorite"
        btnSlideshow.text = if (slideshowRunning) "\u23F8 Stop" else "\u25B6 Slideshow"
        btnEditPerson.isEnabled = currentFaces.any { it.person?.name?.isNotBlank() == true }
        btnFavorite.requestFocus()
    }

    private fun closeActionPanel() {
        showActions = false; actionPanel.visibility = View.GONE; imageView.requestFocus()
    }

    // ── Actions ─────────────────────────────────────────────────────────

    private fun toggleFavorite() {
        val asset = AssetStore.currentAsset() ?: return
        val newFav = !(currentAsset?.isFavorite ?: false)
        lifecycleScope.launch {
            try {
                ImmichClient.getApi().updateAssets(UpdateAssetsRequest(ids = listOf(asset.id), isFavorite = newFav))
                currentAsset = currentAsset?.copy(isFavorite = newFav)
                updateFavoriteIndicator(newFav)
            } catch (_: Exception) {}
        }
    }

    private fun updateFavoriteIndicator(isFav: Boolean) {
        favoriteIndicator.text = if (isFav) "\u2665" else "\u2661"
        favoriteIndicator.setTextColor(if (isFav) Color.parseColor("#EF4444") else Color.WHITE)
        favoriteIndicator.visibility = View.VISIBLE
    }

    private fun toggleSlideshow() {
        if (slideshowRunning) stopSlideshow()
        else {
            slideshowRunning = true
            slideshowIndicator.text = "\u25B6 Slideshow"; slideshowIndicator.visibility = View.VISIBLE
            handler.postDelayed(slideshowRunnable, PrefsManager.slideshowIntervalSeconds * 1000L)
        }
    }

    private fun stopSlideshow() {
        slideshowRunning = false; handler.removeCallbacks(slideshowRunnable); slideshowIndicator.visibility = View.GONE
    }

    private fun jumpToTimeline() {
        val date = currentPhotoDate ?: return
        startActivity(Intent(this, BrowseGridActivity::class.java).apply {
            putExtra(BrowseGridActivity.EXTRA_MODE, BrowseGridActivity.MODE_DATE)
            putExtra(BrowseGridActivity.EXTRA_ID, date)
            putExtra(BrowseGridActivity.EXTRA_TITLE, "Photos from $date")
        })
    }

    private fun editPerson() {
        val person = currentFaces.firstOrNull { it.person?.name?.isNotBlank() == true }?.person
        if (person == null) { Toast.makeText(this, "No person detected", Toast.LENGTH_SHORT).show(); return }
        val asset = AssetStore.currentAsset() ?: return
        startActivity(Intent(this, PersonEditActivity::class.java).apply {
            putExtra(PersonEditActivity.EXTRA_PERSON_ID, person.id)
            putExtra(PersonEditActivity.EXTRA_PERSON_NAME, person.name)
            putExtra(PersonEditActivity.EXTRA_PERSON_BIRTH, person.birthDate ?: "")
            putExtra(PersonEditActivity.EXTRA_CURRENT_ASSET_ID, asset.id)
        })
    }

    private fun toggleExifPanel() {
        showExif = !showExif
        if (showExif) { currentAsset?.let { updateExifPanel(it) }; exifPanel.visibility = View.VISIBLE }
        else exifPanel.visibility = View.GONE
    }

    private fun updateExifPanel(asset: Asset) {
        val exif = asset.exifInfo ?: return
        val sb = StringBuilder()
        sb.appendLine(asset.originalFileName)
        asset.localDateTime?.let { sb.appendLine("Date: ${it.replace("T", " ").take(19)}") }
        listOfNotNull(exif.make, exif.model).joinToString(" ").takeIf { it.isNotBlank() }?.let { sb.appendLine("Camera: $it") }
        exif.lensModel?.let { sb.appendLine("Lens: $it") }
        listOfNotNull(
            exif.fNumber?.let { "f/$it" }, exif.exposureTime?.let { "${it}s" },
            exif.focalLength?.let { "${it}mm" }, exif.iso?.let { "ISO $it" }
        ).joinToString("  ").takeIf { it.isNotBlank() }?.let { sb.appendLine(it) }
        listOfNotNull(exif.city, exif.state, exif.country).joinToString(", ").takeIf { it.isNotBlank() }?.let { sb.appendLine("Location: $it") }
        exif.fileSizeInByte?.let { sb.appendLine("Size: ${"%.1f".format(it / (1024.0 * 1024.0))} MB") }
        exif.description?.takeIf { it.isNotBlank() }?.let { sb.appendLine("\"$it\"") }
        exifText.text = sb.toString().trim()
    }

    // ── Hints ───────────────────────────────────────────────────────────

    private fun showHints() {
        infoOverlay.text = "\u2190\u2192 Navigate   OK Actions   \u2193/\u25B6\u23F8 Slideshow   Back Exit"
        infoOverlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideHintsRunnable)
        handler.postDelayed(hideHintsRunnable, 3000)
    }

    private fun fadeOutHints() {
        infoOverlay.animate().alpha(0f).setDuration(300).withEndAction {
            infoOverlay.visibility = View.GONE; infoOverlay.alpha = 1f
        }
    }

    override fun onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null) }
}
