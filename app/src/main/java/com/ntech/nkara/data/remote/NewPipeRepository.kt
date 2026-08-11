package com.ntech.nkara.data.remote

import android.util.Log
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

data class YouTubeSearchVideo(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String = "",
)

/** Separate adaptive tracks are merged by Media3 on the Host. */
data class NativePlaybackSource(
    val videoUrl: String,
    val audioUrl: String?,
    val title: String,
)

@Singleton
class NewPipeRepository @Inject constructor() {
    init {
        synchronized(NEW_PIPE_LOCK) {
            if (runCatching { NewPipe.getDownloader() }.getOrNull() == null) {
                NewPipe.init(
                NkaraYouTubeDownloader(OkHttpClient()),
                    Localization.DEFAULT,
                    ContentCountry("US"),
                )
            }
        }
    }

    suspend fun search(query: String): Result<List<YouTubeSearchVideo>> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedQuery = query.trim().let {
                if (it.contains("karaoke", ignoreCase = true)) it else "$it karaoke"
            }
            Log.d(LOG_TAG, "search start query=$normalizedQuery")
            val extractor = ServiceList.YouTube.getSearchExtractor(
                normalizedQuery,
                listOf("all"),
                "relevance",
            )
            extractor.fetchPage()
            val items = extractor.initialPage.items
            val videos = items.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
                extractVideoId(item.url)?.let { videoId ->
                    YouTubeSearchVideo(
                        videoId = videoId,
                        title = item.name?.takeIf(String::isNotBlank) ?: "YouTube video",
                        thumbnailUrl = item.thumbnails.maxByOrNull { thumbnail -> thumbnail.width }?.url.orEmpty(),
                    )
                }
            }.take(MAX_SEARCH_RESULTS)
            Log.d(LOG_TAG, "search query=$normalizedQuery total=${items.size} mapped=${videos.size}")
            videos
        }.onFailure { error -> Log.e(LOG_TAG, "search failed for query=$query", error) }
    }

    suspend fun resolvePlayback(videoId: String): Result<NativePlaybackSource> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(LOG_TAG, "resolve start videoId=$videoId")
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, watchUrl(videoId))
            val isLive = streamInfo.streamType == StreamType.LIVE_STREAM ||
                streamInfo.streamType == StreamType.AUDIO_LIVE_STREAM
            if (isLive) {
                streamInfo.hlsUrl?.takeIf(String::isNotBlank)?.let { hlsUrl ->
                    Log.d(LOG_TAG, "resolve live hls title=${streamInfo.name} host=${safeStreamUrl(hlsUrl)}")
                    return@runCatching NativePlaybackSource(hlsUrl, null, streamInfo.name)
                }
            }

            val progressiveVideo = streamInfo.videoStreams
                .filter { !it.isVideoOnly && !it.url.isNullOrBlank() && it.format?.suffix == "mp4" }
                .minWithOrNull(compareBy<VideoStream> { qualityDistance(it.height) }.thenByDescending { it.bitrate })
            val adaptiveVideo = streamInfo.videoOnlyStreams
                .filter { !it.url.isNullOrBlank() && it.format?.suffix == "mp4" }
                .minWithOrNull(compareBy<VideoStream> { qualityDistance(it.height) }.thenByDescending { it.bitrate })
            val video = progressiveVideo ?: adaptiveVideo
                ?: error("No compatible video stream was returned.")
            val usesAdaptiveTracks = progressiveVideo == null && adaptiveVideo != null
            val audio = if (usesAdaptiveTracks) selectAudio(streamInfo.audioStreams.orEmpty()) else null
            Log.d(
                LOG_TAG,
                "resolve tracks adaptive=$usesAdaptiveTracks videoHeight=${video.height} videoBitrate=${video.bitrate} " +
                    "audioBitrate=${audio?.averageBitrate} videoHost=${safeStreamUrl(checkNotNull(video.url))} " +
                    "audioHost=${audio?.url?.let(::safeStreamUrl)}",
            )
            NativePlaybackSource(
                videoUrl = checkNotNull(video.url),
                audioUrl = audio?.url,
                title = streamInfo.name?.takeIf(String::isNotBlank) ?: "YouTube video",
            )
        }.onFailure { error -> Log.e(LOG_TAG, "playback resolve failed for videoId=$videoId", error) }
    }

    private fun selectAudio(streams: List<AudioStream>): AudioStream? = streams
        .filter { !it.url.isNullOrBlank() }
        .maxByOrNull { it.averageBitrate }

    private fun qualityDistance(height: Int): Int = kotlin.math.abs(height - PREFERRED_HEIGHT)

    private fun watchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

    private fun extractVideoId(url: String): String? = VIDEO_ID_PATTERN.find(url)?.groupValues?.getOrNull(1)

    private fun safeStreamUrl(url: String): String = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()

    private companion object {
        const val MAX_SEARCH_RESULTS = 20
        const val PREFERRED_HEIGHT = 720
        const val LOG_TAG = "nKaraNewPipe"
        val NEW_PIPE_LOCK = Any()
        val VIDEO_ID_PATTERN = Regex("(?:v=|youtu\\.be/|embed/)([A-Za-z0-9_-]{11})")
    }
}

private class NkaraYouTubeDownloader(private val client: OkHttpClient) : Downloader() {
    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val requestedUrl = request.url()
        Log.d(DOWNLOADER_LOG_TAG, "request method=${request.httpMethod()} url=${safeUrl(requestedUrl)}")
        val builder = OkHttpRequest.Builder()
            .url(requestedUrl)
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .addHeader("User-Agent", USER_AGENT)
        if (request.url().contains("youtube.com") || request.url().contains("googlevideo.com")) {
            builder.addHeader("Cookie", CONSENT_COOKIE)
        }
        request.headers().forEach { (name, values) -> values.forEach { value -> builder.addHeader(name, value) } }
        client.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body.string()
            val contentType = response.header("Content-Type")
            val hasInitialData = responseBody.contains("ytInitialData")
            val isConsent = responseBody.contains("consent.youtube.com")
            val hasCaptcha = responseBody.contains("captcha", ignoreCase = true)
            val hasUnusualTraffic = responseBody.contains("unusual traffic", ignoreCase = true)
            Log.d(
                DOWNLOADER_LOG_TAG,
                "response code=${response.code} finalUrl=${safeUrl(response.request.url.toString())} " +
                    "type=$contentType length=${responseBody.length} initialData=$hasInitialData consent=$isConsent " +
                    "captcha=$hasCaptcha unusualTraffic=$hasUnusualTraffic",
            )
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBody,
                requestedUrl,
            )
        }
    }

    private fun safeUrl(url: String): String = url.substringBefore('?')

    private companion object {
        const val DOWNLOADER_LOG_TAG = "nKaraNewPipeHttp"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/138.0.0.0 Safari/537.36"
        const val CONSENT_COOKIE = "CONSENT=YES+cb.20210328-17-p0.en+FX+456; SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg"
    }
}
