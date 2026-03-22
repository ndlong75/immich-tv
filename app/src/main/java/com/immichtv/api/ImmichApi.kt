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

    // ── Search (used for person assets, latest photos, etc.) ────────────────

    @POST("api/search/metadata")
    suspend fun searchAssets(
        @Body request: SearchRequest
    ): SearchResponse

    // ── Asset URLs (constructed, not API calls) ─────────────────────────────
    // Thumbnail: {baseUrl}/api/assets/{id}/thumbnail
    // Original:  {baseUrl}/api/assets/{id}/original
    // Video:     {baseUrl}/api/assets/{id}/video/playback
}
