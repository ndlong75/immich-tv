package com.immichtv.api

import retrofit2.http.*

interface ImmichApi {

    // ── Auth ─────────────────────────────────────────────────────────────

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse

    // ── Server ──────────────────────────────────────────────────────────────

    @GET("api/server/version")
    suspend fun getServerVersion(): ServerInfo

    @GET("api/users/me")
    suspend fun getCurrentUser(): UserInfo

    // ── Albums ──────────────────────────────────────────────────────────────

    @GET("api/albums")
    suspend fun getAlbums(
        @Query("shared") shared: Boolean? = null
    ): List<AlbumSimple>

    @GET("api/albums/{id}")
    suspend fun getAlbumDetail(
        @Path("id") albumId: String
    ): AlbumDetail

    // ── Assets ──────────────────────────────────────────────────────────────

    @GET("api/assets/random")
    suspend fun getRandomAssets(
        @Query("count") count: Int = 20
    ): List<Asset>

    @GET("api/assets/{id}")
    suspend fun getAssetInfo(
        @Path("id") assetId: String
    ): Asset

    // ── People ──────────────────────────────────────────────────────────────

    @GET("api/people")
    suspend fun getPeople(
        @Query("withHidden") withHidden: Boolean = false
    ): PeopleResponse

    // ── Memories ────────────────────────────────────────────────────────────

    @GET("api/memories")
    suspend fun getMemories(): List<Memory>

    // ── Timeline ────────────────────────────────────────────────────────────

    @GET("api/timeline/buckets")
    suspend fun getTimeBuckets(
        @Query("size") size: String = "MONTH"
    ): List<TimeBucket>

    @GET("api/timeline/bucket")
    suspend fun getTimeBucket(
        @Query("size") size: String = "MONTH",
        @Query("timeBucket") timeBucket: String
    ): List<Asset>

    // ── Search ──────────────────────────────────────────────────────────────

    @POST("api/search/metadata")
    suspend fun searchAssets(
        @Body request: SearchRequest
    ): SearchResponse

    @POST("api/search/smart")
    suspend fun smartSearch(
        @Body request: SmartSearchRequest
    ): SearchResponse

    // ── Explore / Categories ────────────────────────────────────────────────

    @GET("api/search/explore")
    suspend fun getExploreData(): List<ExploreGroup>

    // ── Folders ─────────────────────────────────────────────────────────────

    @GET("api/view/folder")
    suspend fun getFolderTree(): List<FolderNode>

    @GET("api/view/folder")
    suspend fun getFolderContents(
        @Query("path") path: String
    ): List<FolderNode>

    // ── Map ─────────────────────────────────────────────────────────────────

    @GET("api/map/markers")
    suspend fun getMapMarkers(
        @Query("isArchived") isArchived: Boolean = false,
        @Query("isFavorite") isFavorite: Boolean? = null,
        @Query("withPartners") withPartners: Boolean = false
    ): List<MapMarker>

    // ── Faces on asset ──────────────────────────────────────────────────────

    @GET("api/faces")
    suspend fun getAssetFaces(
        @Query("id") assetId: String
    ): List<AssetFace>

    @PUT("api/faces/{id}")
    suspend fun reassignFace(
        @Path("id") faceId: String,
        @Body request: FaceReassignRequest
    ): AssetFace

    // ── Favorites ───────────────────────────────────────────────────────────

    @PUT("api/assets")
    suspend fun updateAssets(
        @Body request: UpdateAssetsRequest
    ): Unit
}
