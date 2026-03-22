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
import com.immichtv.ui.NavItem

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

        // CRITICAL: Clear any pending/completed Glide load from recycled view FIRST
        Glide.with(cardView.context).clear(cardView.mainImageView)
        cardView.mainImageView.setImageDrawable(ColorDrawable(Color.parseColor("#2d3561")))

        when (item) {
            is AlbumSimple -> {
                cardView.titleText = item.albumName
                cardView.contentText = "${item.assetCount} items"
                if (item.albumThumbnailAssetId != null) {
                    loadImage(cardView, ImmichClient.thumbnailUrl(item.albumThumbnailAssetId), item.albumThumbnailAssetId)
                } else {
                    setPlaceholder(cardView)
                }
            }

            is Person -> {
                cardView.titleText = item.name
                cardView.contentText = if (item.assetCount > 0) "${item.assetCount} photos" else ""
                val url = "${ImmichClient.baseUrl}api/people/${item.id}/thumbnail"
                loadImage(cardView, url, "person_${item.id}", fitCenter = true)
            }

            is Asset -> {
                cardView.titleText = item.originalFileName
                cardView.contentText = when (item.type) {
                    AssetType.VIDEO -> "\u25B6 Video"
                    else -> item.localDateTime?.take(10) ?: ""
                }
                // Use asset ID as signature to ensure correct image mapping
                loadImage(cardView, ImmichClient.thumbnailSmallUrl(item.id), item.id)
            }

            is MemoryCard -> {
                cardView.titleText = item.title
                cardView.contentText = "${item.assets.size} memories"
                if (item.thumbnailAssetId != null) {
                    loadImage(cardView, ImmichClient.thumbnailUrl(item.thumbnailAssetId), "mem_${item.thumbnailAssetId}")
                } else {
                    setPlaceholder(cardView)
                }
            }

            is NavItem -> {
                cardView.titleText = item.title
                cardView.contentText = item.description
                cardView.mainImageView.setImageDrawable(
                    ColorDrawable(Color.parseColor("#0f3460"))
                )
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        // Cancel any pending Glide loads to prevent wrong image appearing
        Glide.with(cardView.context).clear(cardView.mainImageView)
        cardView.badgeImage = null
        cardView.mainImage = null
    }

    private fun loadImage(cardView: ImageCardView, url: String, cacheKey: String, fitCenter: Boolean = false) {
        val headers = ImmichClient.authHeaders()
        val glideUrl = GlideUrl(url, LazyHeaders.Builder().apply {
            headers.forEach { (key, value) -> addHeader(key, value) }
        }.build())

        val builder = Glide.with(cardView.context)
            .load(glideUrl)
            .signature(ObjectKey(cacheKey))
            .placeholder(ColorDrawable(Color.parseColor("#2d3561")))
            .error(ColorDrawable(Color.parseColor("#4a1942")))

        if (fitCenter) {
            builder.transform(com.bumptech.glide.load.resource.bitmap.FitCenter(), RoundedCorners(12))
        } else {
            builder.transform(CenterCrop(), RoundedCorners(12))
        }
        builder.into(cardView.mainImageView)
    }

    private fun setPlaceholder(cardView: ImageCardView) {
        cardView.mainImageView.setImageDrawable(
            ColorDrawable(Color.parseColor("#2d3561"))
        )
    }
}

// ── Simple icon card for Browse/Settings nav items ──────────────────────

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
            is NavItem -> {
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
