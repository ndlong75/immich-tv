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
import com.immichtv.R
import com.immichtv.api.*
import com.immichtv.util.PrefsManager
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

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
    private lateinit var actionPanel: LinearLayout
    private lateinit var btnFavorite: Button
    private lateinit var btnSlideshow: Button
    private lateinit var btnTimeline: Button
    private lateinit var btnEditPerson: Button
    private lateinit var btnExif: Button

    private var assetIds: List<String> = emptyList()
    private var currentIndex = 0
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
        actionPanel = findViewById(R.id.action_panel)
        btnFavorite = findViewById(R.id.btn_favorite)
        btnSlideshow = findViewById(R.id.btn_slideshow)
        btnTimeline = findViewById(R.id.btn_timeline)
        btnEditPerson = findViewById(R.id.btn_edit_person)
        btnExif = findViewById(R.id.btn_exif)

        // Wire action buttons
        btnFavorite.setOnClickListener { toggleFavorite(); closeActionPanel() }
        btnSlideshow.setOnClickListener { toggleSlideshow(); closeActionPanel() }
        btnTimeline.setOnClickListener { jumpToTimeline(); closeActionPanel() }
        btnEditPerson.setOnClickListener { editPerson(); closeActionPanel() }
        btnExif.setOnClickListener { toggleExifPanel(); closeActionPanel() }

        val ids = intent.getStringArrayListExtra(EXTRA_ASSET_IDS)
        val singleId = intent.getStringExtra(EXTRA_ASSET_ID)
        assetIds = ids ?: if (singleId != null) listOf(singleId) else emptyList()
        currentIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
            .coerceIn(0, (assetIds.size - 1).coerceAtLeast(0))

        if (assetIds.isNotEmpty()) {
            loadCurrentPhoto()
            showHints()
        } else finish()
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FIRE TV STICK REMOTE MAPPING
    //
    //  ← →          Navigate photos
    //  Select (OK)   Open/close action menu
    //  Play/Pause    Toggle slideshow
    //  Rewind  ⏪    Previous photo
    //  Fast Fwd ⏩   Next photo
    //  Menu ☰        Open/close action menu
    //  Back          Close panel → stop slideshow → exit
    // ══════════════════════════════════════════════════════════════════════

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // When action panel is open, let D-pad navigate the buttons naturally
        if (showActions) {
            return when (keyCode) {
                KeyEvent.KEYCODE_BACK -> { closeActionPanel(); true }
                else -> super.onKeyDown(keyCode, event)
            }
        }

        return when (keyCode) {
            // Navigate photos
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                navigateNext(); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                navigatePrevious(); true
            }

            // Open action menu (Select or Menu or Up)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_DPAD_UP -> {
                openActionPanel(); true
            }

            // Play/Pause = slideshow
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                toggleSlideshow(); true
            }

            // Back = close things or exit
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

    // ── Navigation ──────────────────────────────────────────────────────

    private fun navigateNext() {
        if (slideshowRunning) stopSlideshow()
        if (currentIndex < assetIds.size - 1) { currentIndex++; loadCurrentPhoto() }
    }

    private fun navigatePrevious() {
        if (slideshowRunning) stopSlideshow()
        if (currentIndex > 0) { currentIndex--; loadCurrentPhoto() }
    }

    // ── Photo loading ───────────────────────────────────────────────────

    private fun loadCurrentPhoto() {
        val assetId = assetIds[currentIndex]
        val headers = ImmichClient.authHeaders()

        val previewUrl = ImmichClient.thumbnailUrl(assetId)
        val originalUrl = ImmichClient.originalUrl(assetId)

        val previewGlideUrl = GlideUrl(previewUrl, LazyHeaders.Builder().apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.build())
        val originalGlideUrl = GlideUrl(originalUrl, LazyHeaders.Builder().apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.build())

        Glide.with(this).clear(imageView)
        Glide.with(this)
            .load(originalGlideUrl)
            .thumbnail(Glide.with(this).load(previewGlideUrl).transform(FitCenter()))
            .transform(FitCenter())
            .placeholder(ColorDrawable(Color.BLACK))
            .error(ColorDrawable(Color.DKGRAY))
            .into(imageView)

        // Update counter
        counterOverlay.text = "${currentIndex + 1} / ${assetIds.size}"
        counterOverlay.visibility = View.VISIBLE

        loadAssetDetails(assetId)
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
                } else {
                    facesOverlay.visibility = View.GONE
                    currentFaces = emptyList()
                }
            } catch (_: Exception) {
                facesOverlay.visibility = View.GONE
                currentFaces = emptyList()
            }
        }
    }

    private fun calculateAge(birthDate: String?, photoDate: String?): String? {
        if (birthDate.isNullOrBlank() || photoDate.isNullOrBlank()) return null
        return try {
            val birth = LocalDate.parse(birthDate.take(10))
            val photo = LocalDate.parse(photoDate.take(10))
            val period = Period.between(birth, photo)
            when {
                period.years > 0 -> "${period.years}y"
                period.months > 0 -> "${period.months}m"
                else -> "${period.days}d"
            }
        } catch (_: Exception) { null }
    }

    // ── Action Panel ────────────────────────────────────────────────────

    private fun openActionPanel() {
        showActions = true
        actionPanel.visibility = View.VISIBLE

        // Update button labels with current state
        val isFav = currentAsset?.isFavorite ?: false
        btnFavorite.text = if (isFav) "\u2665 Unfavorite" else "\u2661 Favorite"
        btnSlideshow.text = if (slideshowRunning) "\u23F8 Stop slideshow" else "\u25B6 Slideshow"
        btnEditPerson.text = if (currentFaces.any { it.person?.name?.isNotBlank() == true })
            "\uD83D\uDC64 Edit person" else "\uD83D\uDC64 No person"
        btnEditPerson.isEnabled = currentFaces.any { it.person?.name?.isNotBlank() == true }

        // Focus the first button
        btnFavorite.requestFocus()
    }

    private fun closeActionPanel() {
        showActions = false
        actionPanel.visibility = View.GONE
        // Return focus to the photo
        imageView.requestFocus()
    }

    // ── Actions ─────────────────────────────────────────────────────────

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
        favoriteIndicator.text = if (isFav) "\u2665" else "\u2661"
        favoriteIndicator.setTextColor(if (isFav) Color.parseColor("#EF4444") else Color.WHITE)
        favoriteIndicator.visibility = View.VISIBLE
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
        slideshowRunning = false
        handler.removeCallbacks(slideshowRunnable)
        slideshowIndicator.visibility = View.GONE
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
        val face = currentFaces.firstOrNull { it.person?.name?.isNotBlank() == true }
        val person = face?.person
        if (person == null) {
            Toast.makeText(this, "No person detected in this photo", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, PersonEditActivity::class.java).apply {
            putExtra(PersonEditActivity.EXTRA_PERSON_ID, person.id)
            putExtra(PersonEditActivity.EXTRA_PERSON_NAME, person.name)
            putExtra(PersonEditActivity.EXTRA_PERSON_BIRTH, person.birthDate ?: "")
            putExtra(PersonEditActivity.EXTRA_CURRENT_ASSET_ID, assetIds[currentIndex])
        })
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
        if (asset.localDateTime != null)
            sb.appendLine("Date: ${asset.localDateTime.replace("T", " ").take(19)}")
        if (exif != null) {
            if (exif.make != null || exif.model != null)
                sb.appendLine("Camera: ${listOfNotNull(exif.make, exif.model).joinToString(" ")}")
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

    // ── Hints ───────────────────────────────────────────────────────────

    private fun showHints() {
        infoOverlay.text = "\u2190\u2192 Navigate   OK Actions   \u25B6\u23F8 Slideshow   Back Exit"
        infoOverlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideHintsRunnable)
        handler.postDelayed(hideHintsRunnable, 4000)
    }

    private fun fadeOutHints() {
        infoOverlay.animate().alpha(0f).setDuration(300).withEndAction {
            infoOverlay.visibility = View.GONE; infoOverlay.alpha = 1f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
