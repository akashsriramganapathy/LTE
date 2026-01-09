package com.github.libretube.test.api

import com.github.libretube.test.api.obj.DeArrowContent
import com.github.libretube.test.api.obj.DeArrowSubmission
import com.github.libretube.test.api.obj.SegmentData
import com.github.libretube.test.api.obj.SubmitSegmentResponse
import com.github.libretube.test.api.obj.VoteInfo
import com.github.libretube.test.obj.update.UpdateInfo
import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import com.github.libretube.test.BuildConfig

private const val GITHUB_API_URL = "https://api.github.com/repos/akashsriramganapathy/lte/releases/latest"
private const val SB_API_URL = "https://sponsor.ajay.app"
private const val RYD_API_URL = "https://returnyoutubedislikeapi.com"
const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"

interface ExternalApi {


    // fetch latest version info
    @GET(GITHUB_API_URL)
    suspend fun getLatestRelease(): UpdateInfo

    @GET("https://api.github.com/repos/akashsriramganapathy/lte/releases/tags/{tag}")
    suspend fun getReleaseByTag(@Path("tag") tag: String): UpdateInfo

    @GET("$RYD_API_URL/votes")
    suspend fun getVotes(@Query("videoId") videoId: String): VoteInfo

    @POST("$SB_API_URL/api/skipSegments")
    suspend fun submitSegment(
        @Query("videoID") videoId: String,
        @Query("userID") userID: String,
        @Query("userAgent") userAgent: String,
        @Query("startTime") startTime: Float,
        @Query("endTime") endTime: Float,
        @Query("category") category: String,
        @Query("duration") duration: Float? = null,
        @Query("description") description: String = ""
    ): List<SubmitSegmentResponse>

    @GET("$SB_API_URL/api/skipSegments/{videoId}")
    suspend fun getSegments(
        @Path("videoId") videoId: String,
        @Query("category") category: List<String>,
        @Query("actionType") actionType: List<String>? = null
    ): List<SegmentData>

    /**
     * @param score: 0 for downvote, 1 for upvote, 20 for undoing previous vote (if existent)
     */
    @POST("$SB_API_URL/api/voteOnSponsorTime")
    suspend fun voteOnSponsorTime(
        @Query("UUID") uuid: String,
        @Query("userID") userID: String,
        @Query("type") score: Int
    )

    @GET("$SB_API_URL/api/branding")
    suspend fun getDeArrowContent(@Query("videoID") videoId: String): retrofit2.Response<DeArrowContent>

    @POST("$SB_API_URL/api/branding")
    suspend fun submitDeArrow(
        @Body submission: com.github.libretube.test.api.obj.DeArrowSubmissionBody
    ): retrofit2.Response<Unit>

    @POST("$SB_API_URL/api/voteOnBranding")
    suspend fun voteOnBranding(
        @Query("UUID") uuid: String,
        @Query("userID") userID: String,
        @Query("type") score: Int
    ): retrofit2.Response<Unit>

}

