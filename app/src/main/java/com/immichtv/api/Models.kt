package com.immichtv.api

// ── Auth ────────────────────────────────────────────────────────────────
data class LoginRequest(val email: String, val password: String)
data class LoginResponse(
    val accessToken: String, val userId: String,
    val userEmail: String, val name: String, val isAdmin: Boolean = false
)

// ── Albums ──────────────────────────────────────────────────────────────
data class AlbumSimple(
    val id: String, val albumName: String, val albumThumbnailAssetId: String?,
    val assetCount: Int, val updatedAt: String?
)
data class AlbumDetail(
    val id: String, val albumName: String, val albumThumbnailAssetId: String?,
    val assetCount: Int, val assets: List<Asset>
)

// ── Assets ──────────────────────────────────────────────────────────────
data class Asset(
    val id: String,
    val type: AssetType? = AssetType.IMAGE,  // nullable to handle unknown values
    val originalFileName: String = "",
    val originalMimeType: String? = null,
    val thumbhash: String? = null,
    val localDateTime: String? = null,
    val fileCreatedAt: String? = null,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val exifInfo: ExifInfo? = null,
    val people: List<PersonInAsset>? = null
)
enum class AssetType { IMAGE, VIDEO, AUDIO, OTHER }

data class ExifInfo(
    val city: String?, val state: String?, val country: String?,
    val description: String?, val make: String?, val model: String?,
    val fNumber: Double?, val exposureTime: String?, val focalLength: Double?,
    val iso: Int?, val latitude: Double?, val longitude: Double?,
    val dateTimeOriginal: String?, val fileSizeInByte: Long?, val lensModel: String?
)

data class PersonInAsset(val id: String, val name: String?)

// ── People ──────────────────────────────────────────────────────────────
data class PeopleResponse(val total: Int, val people: List<Person>)
data class Person(
    val id: String, val name: String, val thumbnailPath: String?,
    val birthDate: String?, val assetCount: Int = 0
)
data class UpdatePersonRequest(
    val name: String? = null,
    val birthDate: String? = null,       // "YYYY-MM-DD"
    val featureFaceAssetId: String? = null  // set thumbnail from this asset
)

// ── Faces ───────────────────────────────────────────────────────────────
data class AssetFace(
    val id: String, val imageHeight: Int?, val imageWidth: Int?,
    val boundingBoxX1: Int?, val boundingBoxY1: Int?,
    val boundingBoxX2: Int?, val boundingBoxY2: Int?,
    val person: Person?
)

// ── Memories ────────────────────────────────────────────────────────────
data class Memory(val id: String, val type: String, val data: MemoryData, val assets: List<Asset>)
data class MemoryData(val year: Int?)

// ── Search ──────────────────────────────────────────────────────────────
data class SearchRequest(
    val query: String? = null, val type: String? = null,
    val page: Int = 1, val size: Int = 50,
    val personIds: List<String>? = null, val order: String? = null,
    val takenAfter: String? = null, val takenBefore: String? = null
)
data class SmartSearchRequest(val query: String, val page: Int = 1, val size: Int = 50)
data class SearchResponse(val assets: SearchAssets)
data class SearchAssets(val total: Int, val items: List<Asset>, val nextPage: String? = null)

// ── Explore ─────────────────────────────────────────────────────────────
data class ExploreGroup(val fieldName: String, val items: List<ExploreItem>)
data class ExploreItem(val value: String, val data: ExploreItemData)
data class ExploreItemData(val id: String, val type: AssetType?)

// ── Map ─────────────────────────────────────────────────────────────────
data class MapMarker(
    val id: String, val lat: Double, val lon: Double,
    val city: String?, val state: String?, val country: String?
)

// ── Update Assets ───────────────────────────────────────────────────────
data class UpdateAssetsRequest(
    val ids: List<String>, val isFavorite: Boolean? = null, val isArchived: Boolean? = null
)

// ── Server Info ─────────────────────────────────────────────────────────
data class ServerInfo(val version: String, val versionUrl: String?)
data class UserInfo(val id: String, val email: String, val name: String)
data class TimeBucket(val timeBucket: String, val count: Int)
