package com.immichtv.ui.presenters

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.ImageView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.signature.ObjectKey
import com.immichtv.api.*
import com.immichtv.ui.MemoryCard

class CardPresenter(private val baseUrl: String) : Presenter() {

    companion object {
        private const val CARD_WIDTH = 320
        private const val CARD_HEIGHT = 240
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
            setMainImageScaleType(ImageView.ScaleType.CENTER_CROP)
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            setInfoAreaBackgroundColor(Color.parseColor("#16213e"))
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cardView = viewHolder.view as ImageCardView

        when (item) {
            is AlbumSimple -> {
                cardView.titleText = item.albumName
                cardView.contentText = "${item.assetCount} items"
                loadImage(cardView, item.albumThumbnailAssetId?.let { ImmichClient.thumbnailUrl(it) }, item.id)
            }
            is Person -> {
                cardView.titleText = item.name
                val ageStr = item.birthDate?.let { calcAge(it) }
                val countStr = if (item.assetCount > 0) "${item.assetCount} photos" else ""
                cardView.contentText = listOfNotNull(ageStr, countStr).joinToString(" \u2022 ")
                loadImage(cardView, ImmichClient.personThumbnailUrl(item.id), item.id)
            }
            is Asset -> {
                cardView.titleText = item.originalFileName
                cardView.contentText = when (item.type) {
                    AssetType.VIDEO -> "\u25B6 Video"
                    else -> item.localDateTime?.take(10) ?: ""
                }
                // Use asset ID as signature to prevent Glide from showing cached wrong image
                loadImage(cardView, ImmichClient.thumbnailSmallUrl(item.id), item.id)
            }
            is MemoryCard -> {
                cardView.titleText = item.title
                cardView.contentText = "${item.assets.size} memories"
                loadImage(cardView, item.thumbnailAssetId?.let { ImmichClient.thumbnailUrl(it) }, item.id)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        // Clear Glide to prevent stale images on recycled views
        Glide.with(cardView.context).clear(cardView.mainImageView)
        cardView.badgeImage = null
        cardView.mainImage = null
    }

    private fun calcAge(birthDate: String): String? {
        return try {
            val parts = birthDate.take(10).split("-")
            if (parts.size < 3) return null
            val birthYear = parts[0].toInt()
            val birthMonth = parts[1].toInt()
            val birthDay = parts[2].toInt()
            val now = java.util.Calendar.getInstance()
            var age = now.get(java.util.Calendar.YEAR) - birthYear
            if (now.get(java.util.Calendar.MONTH) + 1 < birthMonth ||
                (now.get(java.util.Calendar.MONTH) + 1 == birthMonth &&
                 now.get(java.util.Calendar.DAY_OF_MONTH) < birthDay)) {
                age--
            }
            if (age >= 0) "${age}y" else null
        } catch (_: Exception) { null }
    }

    private fun loadImage(cardView: ImageCardView, url: String?, uniqueId: String) {
        if (url == null) {
            cardView.mainImageView.setImageDrawable(ColorDrawable(Color.parseColor("#2d3561")))
            return
        }

        val headers = ImmichClient.authHeaders()
        val glideUrl = GlideUrl(url, LazyHeaders.Builder().apply {
            headers.forEach { (key, value) -> addHeader(key, value) }
        }.build())

        Glide.with(cardView.context)
            .load(glideUrl)
            .signature(ObjectKey(uniqueId))  // Prevent cache mix-ups between recycled views
            .transform(CenterCrop(), RoundedCorners(12))
            .placeholder(ColorDrawable(Color.parseColor("#2d3561")))
            .error(ColorDrawable(Color.parseColor("#4a1942")))
            .into(cardView.mainImageView)
    }
}

// ── Icon Card Presenter for nav items ───────────────────────────────────

class IconCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(160, 160)
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            setInfoAreaBackgroundColor(Color.parseColor("#16213e"))
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cardView = viewHolder.view as ImageCardView
        when (item) {
            is com.immichtv.ui.NavItem -> {
                cardView.titleText = item.title
                cardView.contentText = item.description
                cardView.mainImageView.setImageDrawable(
                    ColorDrawable(Color.parseColor("#0f3460"))
                )
            }
            is com.immichtv.ui.SettingsItem -> {
                cardView.titleText = item.title
                cardView.contentText = item.description
                cardView.mainImageView.setImageDrawable(
                    ColorDrawable(Color.parseColor("#0f3460"))
                )
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // No-op
    }
}
