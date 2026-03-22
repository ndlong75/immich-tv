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
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.immichtv.R
import com.immichtv.api.ImmichClient
import com.immichtv.util.PrefsManager

class PhotoViewerActivity : FragmentActivity() {

    companion object {
        const val EXTRA_ASSET_ID = "asset_id"
        const val EXTRA_ASSET_IDS = "asset_ids"
        const val EXTRA_START_INDEX = "start_index"
    }

    private lateinit var imageView: ImageView
    private lateinit var infoOverlay: TextView
    private lateinit var counterOverlay: TextView

    private var assetIds: List<String> = emptyList()
    private var currentIndex = 0
    private var showOverlay = true

    private val hideOverlayHandler = Handler(Looper.getMainLooper())
    private val hideOverlayRunnable = Runnable { fadeOutOverlay() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full screen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_photo_viewer)

        imageView = findViewById(R.id.photo_image)
        infoOverlay = findViewById(R.id.info_overlay)
        counterOverlay = findViewById(R.id.counter_overlay)

        // Get asset list
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
                navigateNext()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                navigatePrevious()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                toggleOverlay()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun navigateNext() {
        if (currentIndex < assetIds.size - 1) {
            currentIndex++
            loadCurrentPhoto()
        }
    }

    private fun navigatePrevious() {
        if (currentIndex > 0) {
            currentIndex--
            loadCurrentPhoto()
        }
    }

    private fun loadCurrentPhoto() {
        val assetId = assetIds[currentIndex]
        val url = ImmichClient.thumbnailUrl(assetId) // Use preview size for speed

        val headers = ImmichClient.authHeaders()
        val glideUrl = GlideUrl(url, LazyHeaders.Builder().apply {
            headers.forEach { (key, value) -> addHeader(key, value) }
        }.build())

        Glide.with(this)
            .load(glideUrl)
            .transform(FitCenter())
            .placeholder(ColorDrawable(Color.BLACK))
            .error(ColorDrawable(Color.DKGRAY))
            .into(imageView)

        updateOverlay()

        // Also preload the original resolution in background
        val originalUrl = ImmichClient.originalUrl(assetId)
        val originalGlideUrl = GlideUrl(originalUrl, LazyHeaders.Builder().apply {
            headers.forEach { (key, value) -> addHeader(key, value) }
        }.build())

        Glide.with(this)
            .load(originalGlideUrl)
            .transform(FitCenter())
            .into(imageView)
    }

    private fun updateOverlay() {
        if (showOverlay) {
            counterOverlay.text = "${currentIndex + 1} / ${assetIds.size}"
            counterOverlay.visibility = View.VISIBLE
            infoOverlay.text = "← → Navigate  •  OK Toggle info  •  Back Exit"
            infoOverlay.visibility = View.VISIBLE

            // Auto-hide after 3 seconds
            hideOverlayHandler.removeCallbacks(hideOverlayRunnable)
            hideOverlayHandler.postDelayed(hideOverlayRunnable, 3000)
        }
    }

    private fun toggleOverlay() {
        showOverlay = !showOverlay
        if (showOverlay) {
            updateOverlay()
        } else {
            fadeOutOverlay()
        }
    }

    private fun fadeOutOverlay() {
        infoOverlay.animate().alpha(0f).setDuration(300).withEndAction {
            infoOverlay.visibility = View.GONE
            infoOverlay.alpha = 1f
        }
        counterOverlay.animate().alpha(0f).setDuration(300).withEndAction {
            counterOverlay.visibility = View.GONE
            counterOverlay.alpha = 1f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlayHandler.removeCallbacks(hideOverlayRunnable)
    }
}
