package com.immichtv.api

import retrofit2.http.*

interface ImmichApi {

    // ── Auth ─────────────────────────────────────────────────────────────
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    // ── Server ──────────────────────────────────────────────────────────
    @GET("api/server/version")
    suspend fun getServerVersion(): ServerInfo

    @GET("api/users/me")
    suspend fun getCurrentUser(): UserInfo

    // ── Albums ──────────────────────────────────────────────────────────
    @GET("api/albums")
    suspend fun getAlbums(@Query("shared") shared: Boolean? = null): List<AlbumSimple>

    @GET("api/albums/{id}")
    suspend fun getAlbumDetail(@Path("id") albumId: String): AlbumDetail

    // ── Assets ──────────────────────────────────────────────────────────
    @GET("api/assets/random")
    suspend fun getRandomAssets(@Query("count") count: Int = 20): List<Asset>

    @GET("api/assets/{id}")
    suspend fun getAssetInfo(@Path("id") assetId: String): Asset

    @PUT("api/assets")
    suspend fun updateAssets(@Body request: UpdateAssetsRequest): Unit

    // ── People ──────────────────────────────────────────────────────────
    @GET("api/people")
    suspend fun getPeople(@Query("withHidden") withHidden: Boolean = false): PeopleResponse

    @PUT("api/people/{id}")
    suspend fun updatePerson(
        @Path("id") personId: String,
        @Body request: UpdatePersonRequest
    ): Person

    // ── Faces ───────────────────────────────────────────────────────────
    @GET("api/faces")
    suspend fun getAssetFaces(@Query("id") assetId: String): List<AssetFace>

    // ── Memories ────────────────────────────────────────────────────────
    @GET("api/memories")
    suspend fun getMemories(): List<Memory>

    // ── Timeline ────────────────────────────────────────────────────────
    @GET("api/timeline/buckets")
    suspend fun getTimeBuckets(
        @Query("size") size: String = "MONTH",
        @Query("isArchived") isArchived: Boolean = false
    ): List<TimeBucket>

    @GET("api/timeline/bucket")
    suspend fun getTimeBucket(
        @Query("size") size: String = "MONTH",
        @Query("timeBucket") timeBucket: String,
        @Query("isArchived") isArchived: Boolean = false
    ): List<Asset>

    // ── Search ──────────────────────────────────────────────────────────
    @POST("api/search/metadata")
    suspend fun searchAssets(@Body request: SearchRequest): SearchResponse

    @POST("api/search/smart")
    suspend fun smartSearch(@Body request: SmartSearchRequest): SearchResponse

    // ── Explore ─────────────────────────────────────────────────────────
    @GET("api/search/explore")
    suspend fun getExploreData(): List<ExploreGroup>

    // ── Map ─────────────────────────────────────────────────────────────
    @GET("api/map/markers")
    suspend fun getMapMarkers(
        @Query("isArchived") isArchived: Boolean = false,
        @Query("isFavorite") isFavorite: Boolean? = null
    ): List<MapMarker>
}
