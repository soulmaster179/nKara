package com.ntech.nkara.feature.lan

import com.ntech.nkara.core.model.Song
import com.ntech.nkara.feature.karaoke.domain.QueueStore
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class LanHostServer @Inject constructor(
    private val queueStore: QueueStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clients = Collections.synchronizedSet(mutableSetOf<BufferedWriter>())
    private val _connectedClientCount = MutableStateFlow(0)
    val connectedClientCount: StateFlow<Int> = _connectedClientCount.asStateFlow()
    private val playbackProgress = MutableStateFlow(PlaybackProgress())
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var stateJob: Job? = null

    fun start(port: Int = DEFAULT_PORT) {
        if (serverSocket != null) return
        acceptJob = scope.launch {
            ServerSocket(port).use { socket ->
                serverSocket = socket
                while (isActive && !socket.isClosed) {
                    // accept() must run before launch(). Launching accept itself in a
                    // tight loop creates an unbounded number of blocked IO coroutines.
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch { handleClient(client) }
                }
            }
        }
        stateJob = scope.launch {
            combine(queueStore.currentSong, queueStore.queuedSongs, playbackProgress) { current, queue, progress ->
                Triple(current, queue, progress)
            }.collect { (current, queue, progress) -> broadcast(snapshotJson(current, queue, progress)) }
        }
    }

    fun stop() {
        stateJob?.cancel()
        acceptJob?.cancel()
        serverSocket?.close()
        serverSocket = null
        synchronized(clients) { clients.forEach { runCatching { it.close() } }; clients.clear() }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            val writer = BufferedWriter(OutputStreamWriter(client.getOutputStream()))
            clients += writer
            _connectedClientCount.value = clients.size
            write(writer, snapshotJson(queueStore.currentSong.value, queueStore.queuedSongs.value, playbackProgress.value))
            runCatching {
                BufferedReader(InputStreamReader(client.getInputStream())).forEachLine(::handleCommand)
            }
            clients -= writer
            _connectedClientCount.value = clients.size
            runCatching { writer.close() }
        }
    }

    private fun handleCommand(raw: String) {
        val command = runCatching { JSONObject(raw) }.getOrNull() ?: return
        when (command.optString("type")) {
            "add" -> extractVideoId(command.optString("input"))?.let { videoId ->
                val title = command.optString("title").ifBlank { "YouTube: $videoId" }
                val song = Song(videoId = videoId, title = title)
                if (command.optBoolean("priority", false)) queueStore.addAsPriority(song) else queueStore.add(song)
            }
            "prioritize" -> queueStore.prioritize(command.optString("queueId"))
            "remove" -> queueStore.remove(command.optString("queueId"))
            "next" -> queueStore.next()
            "play" -> queueStore.play()
            "pause" -> queueStore.pause()
        }
    }

    private fun broadcast(message: String) = synchronized(clients) {
        clients.toList().forEach { writer -> if (!write(writer, message)) clients -= writer }
    }

    private fun write(writer: BufferedWriter, message: String): Boolean = runCatching {
        writer.write(message)
        writer.newLine()
        writer.flush()
    }.isSuccess

    fun updatePlaybackProgress(positionMs: Long, durationMs: Long) {
        val sanitized = PlaybackProgress(positionMs.coerceAtLeast(0), durationMs.coerceAtLeast(0))
        if (playbackProgress.value != sanitized) playbackProgress.value = sanitized
    }

    private fun snapshotJson(current: Song?, queue: List<Song>, progress: PlaybackProgress): String = JSONObject().apply {
        put("type", "snapshot")
        put("current", current?.toJson())
        put("queue", JSONArray(queue.map { it.toJson() }))
        put("positionMs", progress.positionMs)
        put("durationMs", progress.durationMs)
    }.toString()

    private fun Song.toJson(): JSONObject = JSONObject().apply {
        put("queueId", queueId)
        put("videoId", videoId)
        put("title", title)
    }

    private fun extractVideoId(input: String): String? {
        val match = VIDEO_ID_PATTERN.find(input.trim())?.groupValues?.getOrNull(1)
        return match ?: input.trim().takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }

    private companion object {
        const val DEFAULT_PORT = 8877
        val VIDEO_ID_PATTERN = Regex("(?:v=|youtu\\.be/|embed/)([A-Za-z0-9_-]{11})")
    }
}

private data class PlaybackProgress(val positionMs: Long = 0, val durationMs: Long = 0)
