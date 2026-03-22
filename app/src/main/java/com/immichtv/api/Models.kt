package com.immichtv.api

import com.google.gson.annotations.SerializedName

// ── Albums ──────────────────────────────────────────────────────────────────

data class AlbumSimple(
    val id: String,
    val albumName: String,
    val albumThumbnailAssetId: String?,
    val assetCount: Int,
    val updatedAt: String?
)

data class AlbumDetail(
    val id: String,
    val albumName: String,
    val albumThumbnailAssetId: String?,
    val assetCount: Int,
    val assets: List<Asset>
)

// ── Assets ──────────────────────────────────────────────────────────────────

data class Asset(
    val id: String,
    val type: AssetType,
    val originalFileName: String,
    val originalMimeType: String?,
    val thumbhash: String?,
    val localDateTime: String?,
    val isFavorite: Boolean = false,
    val exifInfo: ExifInfo? = null
)

enum class AssetType {
    IMAGE, VIDEO, AUDIO, OTHER
}

data class ExifInfo(
    val city: String?,
    val country: String?,
    val description: String?,
    val make: String?,
    val model: String?
)

// ── People ──────────────────────────────────────────────────────────────────

data class PeopleResponse(
    val total: Int,
    val people: List<Person>
)

data class Person(
    val id: String,
    val name: String,
    val thumbnailPath: String?,
    val birthDate: String?,
    val assetCount: Int = 0    // Immich returns this — used for sorting
)

// ── Memories ────────────────────────────────────────────────────────────────

data class Memory(
    val id: String,
    val type: String,
    val data: MemoryData,
    val assets: List<Asset>
)

data class MemoryData(
    val year: Int?
)

// ── Search ──────────────────────────────────────────────────────────────────

data class SearchRequest(
    val query: String? = null,
    val type: String? = null,
    val page: Int = 1,
    val size: Int = 50,
    val personIds: List<String>? = null,
    val order: String? = null   // "asc" or "desc"
)

data class SearchResponse(
    val assets: SearchAssets
)

data class SearchAssets(
    val total: Int,
    val items: List<Asset>,
    val nextPage: String? = null
)

// ── Server Info ─────────────────────────────────────────────────────────────

data class ServerInfo(
    val version: String,
    val versionUrl: String?
)

data class UserInfo(
    val id: String,
    val email: String,
    val name: String
)

// ── Timeline / Buckets ─────────────────────────────────────────────────────

data class TimeBucket(
    val timeBucket: String,
    val count: Int
)
