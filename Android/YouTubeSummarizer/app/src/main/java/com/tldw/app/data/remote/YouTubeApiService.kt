package com.tldw.app.data.remote

import com.google.gson.Gson
import com.tldw.app.data.dto.InnerTubeResponseDto
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val WATCH_URL = "https://www.youtube.com/watch?v=%s"
private const val INNERTUBE_API_URL = "https://www.youtube.com/youtubei/v1/player"
private val INNERTUBE_CONTEXT =
    mapOf("client" to mapOf("clientName" to "ANDROID", "clientVersion" to "20.10.38"))

/** Low-level service responsible for making HTTP requests to YouTube. */
class YouTubeApiService(
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson(),
) {

    /** Fetches the raw HTML of the YouTube watch page for the given [videoId]. */
    @Throws(IOException::class)
    fun fetchVideoPageHtml(videoId: String): String {
        val url = WATCH_URL.format(videoId)
        val request = Request.Builder().url(url).header("Accept-Language", "en-US").get().build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful)
            throw IOException("HTTP ${response.code} for video page: $videoId")
        return response.body.string()
    }

    /** Posts to the InnerTube player endpoint and returns the parsed [InnerTubeResponseDto]. */
    @Throws(IOException::class)
    fun fetchInnerTubeData(videoId: String): InnerTubeResponseDto {
        val body = gson.toJson(mapOf("context" to INNERTUBE_CONTEXT, "videoId" to videoId))
        val requestBody = body.toRequestBody("application/json".toMediaType())
        val request =
            Request.Builder()
                .url(INNERTUBE_API_URL)
                .header("Accept-Language", "en-US")
                .post(requestBody)
                .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful)
            throw IOException("HTTP ${response.code} for InnerTube: $videoId")
        val responseBody = response.body.string()
        return gson.fromJson(responseBody, InnerTubeResponseDto::class.java)
    }

    /** Fetches the raw transcript XML from the given caption [url]. */
    @Throws(IOException::class)
    fun fetchTranscriptXml(url: String): String {
        val request = Request.Builder().url(url).header("Accept-Language", "en-US").get().build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for transcript XML")
        return response.body.string()
    }
}
