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
import com.immichtv.R
import com.immichtv.api.*
import com.immichtv.ui.MemoryCard
import com.immichtv.ui.SettingsItem

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
            // Card styling
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            setInfoAreaBackgroundColor(Color.parseColor("#16213e"))
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cardView = viewHolder.view as ImageCardView
        val context = cardView.context

        when (item) {
            is AlbumSimple -> {
                cardView.titleText = item.albumName
                cardView.contentText = "${item.assetCount} items"
                val thumbUrl = if (item.albumThumbnailAssetId != null) {
                    ImmichClient.thumbnailUrl(item.albumThumbnailAssetId)
                } else null
                loadImage(cardView, thumbUrl)
            }

            is Person -> {
                cardView.titleText = item.name
                cardView.contentText = ""
                val thumbUrl = ImmichClient.personThumbnailUrl(item.id)
                loadImage(cardView, thumbUrl)
            }

            is Asset -> {
                cardView.titleText = item.originalFileName
                cardView.contentText = when (item.type) {
                    AssetType.VIDEO -> "▶ Video"
                    else -> item.localDateTime?.take(10) ?: ""
                }
                val thumbUrl = ImmichClient.thumbnailSmallUrl(item.id)
                loadImage(cardView, thumbUrl)
            }

            is MemoryCard -> {
                cardView.titleText = item.title
                cardView.contentText = "${item.assets.size} memories"
                val thumbUrl = if (item.thumbnailAssetId != null) {
                    ImmichClient.thumbnailUrl(item.thumbnailAssetId)
                } else null
                loadImage(cardView, thumbUrl)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.badgeImage = null
        cardView.mainImage = null
    }

    private fun loadImage(cardView: ImageCardView, url: String?) {
        if (url == null) {
            cardView.mainImageView.setImageDrawable(
                ColorDrawable(Color.parseColor("#2d3561"))
            )
            return
        }

        val headers = ImmichClient.authHeaders()
        val glideUrl = GlideUrl(url, LazyHeaders.Builder().apply {
            headers.forEach { (key, value) -> addHeader(key, value) }
        }.build())

        Glide.with(cardView.context)
            .load(glideUrl)
            .transform(CenterCrop(), RoundedCorners(12))
            .placeholder(ColorDrawable(Color.parseColor("#2d3561")))
            .error(ColorDrawable(Color.parseColor("#4a1942")))
            .into(cardView.mainImageView)
    }
}

// ── Simple text card presenter for settings ─────────────────────────────────

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
        if (item is SettingsItem) {
            cardView.titleText = item.title
            cardView.contentText = item.description
            cardView.mainImageView.setImageDrawable(
                ColorDrawable(Color.parseColor("#0f3460"))
            )
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // No-op
    }
}
