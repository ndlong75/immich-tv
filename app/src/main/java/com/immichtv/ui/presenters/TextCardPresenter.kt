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
import com.immichtv.api.ImmichClient
import com.immichtv.ui.ExploreEntry
import com.immichtv.ui.FolderEntry

class TextCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(240, 180)
            setMainImageScaleType(ImageView.ScaleType.CENTER_CROP)
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            setInfoAreaBackgroundColor(Color.parseColor("#16213e"))
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cardView = viewHolder.view as ImageCardView

        when (item) {
            is ExploreEntry -> {
                cardView.titleText = item.value
                cardView.contentText = ""
                if (item.thumbnailAssetId != null) {
                    loadImage(cardView, ImmichClient.thumbnailSmallUrl(item.thumbnailAssetId))
                } else {
                    cardView.mainImageView.setImageDrawable(
                        ColorDrawable(Color.parseColor("#0f3460"))
                    )
                }
            }
            is FolderEntry -> {
                cardView.titleText = item.displayName
                cardView.contentText = if (item.isFolder) "Folder" else ""
                cardView.mainImageView.setImageDrawable(
                    ColorDrawable(Color.parseColor(if (item.isFolder) "#1E40AF" else "#2d3561"))
                )
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.mainImage = null
    }

    private fun loadImage(cardView: ImageCardView, url: String) {
        val headers = ImmichClient.authHeaders()
        val glideUrl = GlideUrl(url, LazyHeaders.Builder().apply {
            headers.forEach { (key, value) -> addHeader(key, value) }
        }.build())

        Glide.with(cardView.context)
            .load(glideUrl)
            .transform(CenterCrop(), RoundedCorners(12))
            .placeholder(ColorDrawable(Color.parseColor("#2d3561")))
            .into(cardView.mainImageView)
    }
}
