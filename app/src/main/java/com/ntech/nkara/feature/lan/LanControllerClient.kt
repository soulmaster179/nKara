package com.ntech.nkara.feature.lan

import android.content.Context
import com.ntech.nkara.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class RemoteQueueSnapshot(
    val currentSong: Song? = null,
    val queuedSongs: List<Song> = emptyList(),
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

@Singleton
class LanControllerClient @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val isConnecting = AtomicBoolean(false)
    private val pendingCommands = mutableListOf<JSONObject>()
    private val preferences = context.getSharedPreferences("lan_controller", Context.MODE_PRIVATE)
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null

    private val _connectionStatus = MutableStateFlow("Chua ket noi Host")
    val connectionStatus = _connectionStatus.asStateFlow()
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()
    private val _snapshot = MutableStateFlow(RemoteQueueSnapshot())
    val snapshot = _snapshot.asStateFlow()

    fun connect(address: String) = startConnection(address, closeExisting = true)

    fun disconnect() {
        runCatching { socket?.close() }
        synchronized(lock) {
            socket = null
            writer = null
            pendingCommands.clear()
        }
        _isConnected.value = false
        _connectionStatus.value = "Chua ket noi Host"
    }

    fun add(input: String, title: String? = null, priority: Boolean = false) = send(
        JSONObject().put("type", "add").put("input", input).apply {
            title?.takeIf { it.isNotBlank() }?.let { put("title", it) }
            put("priority", priority)
        },
    )

    fun prioritize(queueId: String) = send(JSONObject().put("type", "prioritize").put("queueId", queueId))
    fun remove(queueId: String) = send(JSONObject().put("type", "remove").put("queueId", queueId))
    fun next() = send(JSONObject().put("type", "next"))
    fun play() = send(JSONObject().put("type", "play"))
    fun pause() = send(JSONObject().put("type", "pause"))
    fun savedHostAddress(): String = preferences.getString(LAST_HOST_ADDRESS, "").orEmpty()

    private fun startConnection(address: String, closeExisting: Boolean) {
        val normalized = address.removePrefix("nkra://host?address=").removePrefix("http://").trim()
        val host = normalized.substringBefore(':')
        val port = normalized.substringAfter(':', DEFAULT_PORT.toString()).toIntOrNull() ?: DEFAULT_PORT
        if (host.isBlank()) {
            _connectionStatus.value = "IP Host khong hop le"
            return
        }
        if (!isConnecting.compareAndSet(false, true)) return
        if (closeExisting) closeSocket()
        _isConnected.value = false
        _connectionStatus.value = "Dang ket noi $host:$port"

        scope.launch {
            runCatching {
                Socket(host, port).also { connected ->
                    synchronized(lock) {
                        socket = connected
                        writer = BufferedWriter(OutputStreamWriter(connected.getOutputStream()))
                    }
                    _isConnected.value = true
                    _connectionStatus.value = "Da ket noi $host:$port"
                    preferences.edit().putString(LAST_HOST_ADDRESS, "$host:$port").apply()
                    flushPendingCommands()
                    BufferedReader(InputStreamReader(connected.getInputStream())).forEachLine(::handleMessage)
                }
            }.onFailure { error ->
                _connectionStatus.value = "Mat ket noi: ${error.javaClass.simpleName}"
            }
            closeSocket()
            _isConnected.value = false
            isConnecting.set(false)
        }
    }

    private fun send(command: JSONObject) {
        scope.launch {
            if (writeCommand(command)) return@launch
            synchronized(lock) { pendingCommands += command }
            val address = savedHostAddress()
            if (address.isBlank()) {
                _connectionStatus.value = "Chua cau hinh Host"
            } else {
                startConnection(address, closeExisting = false)
            }
        }
    }

    private fun flushPendingCommands() {
        val commands = synchronized(lock) { pendingCommands.toList().also { pendingCommands.clear() } }
        commands.forEach { command ->
            if (!writeCommand(command)) synchronized(lock) { pendingCommands.add(0, command) }
        }
    }

    private fun writeCommand(command: JSONObject): Boolean = synchronized(lock) {
        runCatching {
            val activeWriter = requireNotNull(writer) { "Host socket is not connected" }
            activeWriter.write(command.toString())
            activeWriter.newLine()
            activeWriter.flush()
        }.isSuccess
    }

    private fun closeSocket() = synchronized(lock) {
        runCatching { socket?.close() }
        writer = null
        socket = null
    }

    private fun handleMessage(raw: String) {
        val message = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (message.optString("type") != "snapshot") return
        val current = message.optJSONObject("current")?.toSong()
        val queue = buildList {
            val array = message.optJSONArray("queue")
            for (index in 0 until (array?.length() ?: 0)) {
                array?.optJSONObject(index)?.toSong()?.let(::add)
            }
        }
        _snapshot.value = RemoteQueueSnapshot(
            currentSong = current,
            queuedSongs = queue,
            positionMs = message.optLong("positionMs"),
            durationMs = message.optLong("durationMs"),
        )
    }

    private fun JSONObject.toSong() = Song(
        queueId = optString("queueId"),
        videoId = optString("videoId"),
        title = optString("title"),
    )

    private companion object {
        const val DEFAULT_PORT = 8877
        const val LAST_HOST_ADDRESS = "last_host_address"
    }
}
