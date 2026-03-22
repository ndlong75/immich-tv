package com.immichtv.util

import com.immichtv.api.Asset

/**
 * In-memory singleton to share asset data between Grid and PhotoViewer.
 * This avoids passing large ArrayLists through Intent extras which causes
 * TransactionTooLargeException on Android TV (1MB Binder limit).
 *
 * Pattern borrowed from giejay/Immich-Android-TV which uses a similar
 * approach with their MediaSlider component.
 */
object AssetStore {
    var assets: List<Asset> = emptyList()
    var currentIndex: Int = 0

    fun set(assetList: List<Asset>, index: Int) {
        assets = assetList
        currentIndex = index.coerceIn(0, (assetList.size - 1).coerceAtLeast(0))
    }

    fun clear() {
        assets = emptyList()
        currentIndex = 0
    }

    fun hasAssets(): Boolean = assets.isNotEmpty()

    fun currentAsset(): Asset? {
        return if (currentIndex in assets.indices) assets[currentIndex] else null
    }

    fun nextIndex(): Int? {
        return if (currentIndex < assets.size - 1) currentIndex + 1 else null
    }

    fun prevIndex(): Int? {
        return if (currentIndex > 0) currentIndex - 1 else null
    }
}
