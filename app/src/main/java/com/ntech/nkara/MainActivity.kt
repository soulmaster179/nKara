package com.ntech.nkara

import android.os.Bundle
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.view.KeyEvent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import com.ntech.nkara.core.model.Song
import com.ntech.nkara.feature.karaoke.presentation.KaraUiEvent
import com.ntech.nkara.feature.karaoke.presentation.KaraUiState
import com.ntech.nkara.feature.karaoke.presentation.KaraViewModel
import com.ntech.nkara.feature.controller.ControllerViewModel
import com.ntech.nkara.data.remote.NativePlaybackSource
import dagger.hilt.android.AndroidEntryPoint
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.cos
import kotlin.math.sin

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFFB86C),
                    secondary = Color(0xFF84DCC6),
                    surface = Color(0xFF17212B),
                    background = Color(0xFF0B1118),
                ),
            ) {
                KaraApp()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) HostOverlaySignal.show()
        return super.dispatchKeyEvent(event)
    }
}

private object HostOverlaySignal {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()
    fun show() { _events.tryEmit(Unit) }
}

@Composable
private fun KaraApp() {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val rolePreferences = remember(context) { context.getSharedPreferences("app_role", android.content.Context.MODE_PRIVATE) }
    val isTelevision = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
    var role by remember {
        mutableStateOf(
            if (isTelevision) AppRole.Host else rolePreferences.getString("role", null)?.let { runCatching { AppRole.valueOf(it) }.getOrNull() },
        )
    }
    LaunchedEffect(role) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val isController = role == AppRole.Controller
        WindowCompat.setDecorFitsSystemWindows(window, isController)
        if (isController) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
    when (role) {
        AppRole.Host -> KaraRoute()
        AppRole.Controller -> ControllerScreen(onBack = { role = null })
        null -> RolePicker(
            onHost = { rolePreferences.edit().putString("role", AppRole.Host.name).apply(); role = AppRole.Host },
            onController = { rolePreferences.edit().putString("role", AppRole.Controller.name).apply(); role = AppRole.Controller },
        )
    }
}

private enum class AppRole { Host, Controller }

@Composable
private fun RolePicker(onHost: () -> Unit, onController: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("nKara", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Chọn vai trò cho thiết bị này")
        Spacer(Modifier.height(28.dp))
        Button(onClick = onHost, modifier = Modifier.fillMaxWidth()) { Text("Làm Host · phát Karaoke") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onController, modifier = Modifier.fillMaxWidth()) { Text("Làm Controller · chọn bài") }
    }
}

@Composable
private fun ControllerPlaceholder(
    onBack: () -> Unit,
    viewModel: ControllerViewModel = hiltViewModel(),
) {
    var hostAddress by remember { mutableStateOf(viewModel.savedHostAddress) }
    var songInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
        Text("nKara Controller", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text("Quét QR từ Host hoặc nhập địa chỉ IP hiển thị trên TV.")
        }
        item { Text(connectionStatus, color = MaterialTheme.colorScheme.secondary) }
        item {
            OutlinedTextField(hostAddress, { hostAddress = it }, label = { Text("Ví dụ: 192.168.1.20:8877") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Button(onClick = { viewModel.connect(hostAddress) }, enabled = hostAddress.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Kết nối Host") }
        }
        item {
            Text("Đang hát: ${snapshot.currentSong?.title ?: "Chưa có bài"}", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::play, modifier = Modifier.weight(1f)) { Text("Play") }
                OutlinedButton(onClick = viewModel::pause, modifier = Modifier.weight(1f)) { Text("Pause") }
            }
        }
        item {
            Text("Tìm YouTube", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(searchQuery, { searchQuery = it }, label = { Text("Tên bài hát hoặc ca sĩ") }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.search(searchQuery) }, enabled = searchQuery.isNotBlank()) { Text("Tìm") }
            }
            if (searchState.isLoading) Text("Đang tìm…")
            searchState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (searchState.hasSearched && !searchState.isLoading && searchState.errorMessage == null && searchState.videos.isEmpty()) {
                Text("Khong tim thay video phu hop.", color = MaterialTheme.colorScheme.secondary)
            }
        }
        items(searchState.videos, key = { "search_${it.videoId}" }) { video ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(video.title, modifier = Modifier.weight(1f))
                    Button(onClick = { viewModel.addSearchResult(video) }) { Text("Thêm") }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(songInput, { songInput = it }, label = { Text("YouTube URL hoặc Video ID") }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.add(songInput); songInput = "" }, enabled = songInput.isNotBlank()) { Text("Thêm") }
            }
        }
        item { Text("Queue (${snapshot.queuedSongs.size})", style = MaterialTheme.typography.titleLarge) }
        items(snapshot.queuedSongs, key = { it.queueId }) { song ->
            QueueRow(song, { viewModel.prioritize(song.queueId) }, { viewModel.remove(song.queueId) })
        }
        item {
            OutlinedButton(onClick = viewModel::next, modifier = Modifier.fillMaxWidth()) { Text("Chuyển bài tiếp") }
        }
        item {
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Đổi vai trò") }
        }
    }
}

@Composable
private fun KaraRoute(viewModel: KaraViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    KaraScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onVideoEnded = viewModel::onVideoEnded,
        onPlaybackProgress = viewModel::updatePlaybackProgress,
    )
}

@Composable
private fun KaraScreen(
    uiState: KaraUiState,
    onEvent: (KaraUiEvent) -> Unit,
    onVideoEnded: () -> Unit,
    onPlaybackProgress: (Long, Long) -> Unit,
) {
    if (uiState.premiumWarningVisible) {
        AlertDialog(
            onDismissRequest = { onEvent(KaraUiEvent.DismissPremiumWarning) },
            title = { Text("Lưu ý YouTube Premium") },
            text = {
                Text(
                    "Ứng dụng không thể xác minh tài khoản YouTube Premium hoặc tắt quảng cáo. " +
                        "Hãy dùng đúng tài khoản Premium trong player nếu có.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onEvent(KaraUiEvent.DismissPremiumWarning) }) {
                    Text("Đã hiểu")
                }
            },
        )
    }

    HostPlaybackScreen(uiState = uiState, onVideoEnded = onVideoEnded, onPlaybackProgress = onPlaybackProgress)
}

@Composable
private fun HostPlaybackScreen(
    uiState: KaraUiState,
    onVideoEnded: () -> Unit,
    onPlaybackProgress: (Long, Long) -> Unit,
) {
    var showConnectionOverlay by remember { mutableStateOf(false) }
    var overlayNonce by remember { mutableStateOf(0) }
    val hostEventKey = "${uiState.currentSong?.queueId}:${uiState.queuedSongs.joinToString { it.queueId }}:${uiState.connectedControllerCount}:$overlayNonce"
    LaunchedEffect(hostEventKey) {
        showConnectionOverlay = true
        delay(5_000)
        showConnectionOverlay = false
    }
    LaunchedEffect(Unit) {
        HostOverlaySignal.events.collect { overlayNonce += 1 }
    }
    Box(
        modifier = Modifier.fillMaxSize().onPreviewKeyEvent {
            overlayNonce += 1
            false
        },
    ) {
        NativePlayerPanel(
            source = uiState.playbackSource,
            shouldPlay = uiState.isPlaying,
            onEnded = onVideoEnded,
            onProgress = onPlaybackProgress,
            modifier = Modifier.fillMaxSize(),
        )
        if (uiState.currentSong == null) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF070C14), Color(0xFF15273A))),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("nKara", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineLarge)
                    Text("Vui lòng chọn bài", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text("Dùng điện thoại Controller để tìm và thêm bài hát", color = Color(0xFFB8C6D9))
                }
            }
        }
        uiState.playbackError?.let { error ->
            Card(
                modifier = Modifier.align(Alignment.Center),
                colors = CardDefaults.cardColors(containerColor = Color(0xDD5A1720)),
            ) { Text(error, modifier = Modifier.padding(20.dp), color = Color.White) }
        }
        uiState.karaokeScore?.let { score ->
            ScoreRouletteOverlay(score)
        }
        AnimatedVisibility(
            visible = showConnectionOverlay,
            modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xE617212B)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).width(280.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${uiState.hostAddress} · ${uiState.connectedControllerCount} máy", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                    Text("Đang: ${uiState.currentSong?.title ?: "Chưa có bài"}", color = Color.White, maxLines = 1)
                    Text("Tiếp: ${uiState.nextSong?.title ?: "Chưa có bài"} · ${uiState.queuedSongs.size} chờ", color = Color.White, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ScoreRouletteOverlay(finalScore: Int) {
    val context = LocalContext.current
    var shownScore by remember(finalScore) { mutableStateOf(80) }
    val fireworksProgress = remember(finalScore) { Animatable(0f) }
    val applause = remember(finalScore) { MediaPlayer.create(context, R.raw.applause_cheers) }
    DisposableEffect(applause) {
        onDispose { applause?.release() }
    }
    LaunchedEffect(finalScore) {
        repeat(20) {
            shownScore = kotlin.random.Random.nextInt(80, 101)
            delay(90)
        }
        shownScore = finalScore
        applause?.start()
    }
    LaunchedEffect(finalScore) {
        fireworksProgress.animateTo(1f, animationSpec = tween(durationMillis = 2_600))
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xB8000000)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val palette = listOf(Color(0xFFFFD166), Color(0xFFFF5E78), Color(0xFF52D1DC), Color(0xFF9D7CFF))
            val centers = listOf(
                Offset(size.width * 0.18f, size.height * 0.24f),
                Offset(size.width * 0.82f, size.height * 0.26f),
                Offset(size.width * 0.50f, size.height * 0.14f),
            )
            centers.forEachIndexed { centerIndex, center ->
                repeat(26) { particleIndex ->
                    val angle = particleIndex * (2.0 * Math.PI / 26.0)
                    val radius = (90f + (particleIndex % 5) * 22f) * fireworksProgress.value
                    val point = Offset(
                        x = center.x + cos(angle).toFloat() * radius,
                        y = center.y + sin(angle).toFloat() * radius + fireworksProgress.value * fireworksProgress.value * 90f,
                    )
                    drawCircle(
                        color = palette[(particleIndex + centerIndex) % palette.size].copy(alpha = 1f - fireworksProgress.value * 0.35f),
                        radius = 4f + (particleIndex % 3) * 2f,
                        center = point,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ĐIỂM KARAOKE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
            Text(shownScore.toString(), color = Color.White, style = MaterialTheme.typography.displayLarge)
            Text("Tuyệt vời!", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun HostPlayerColumn(
    uiState: KaraUiState,
    onEvent: (KaraUiEvent) -> Unit,
    onVideoEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("nKara", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("KARAOKE HOST", style = MaterialTheme.typography.labelLarge)
        }
        Text("Điện thoại kết nối: ${uiState.hostAddress}", color = MaterialTheme.colorScheme.secondary)
        HostQrCode(address = uiState.hostAddress)
        NativePlayerPanel(
            source = uiState.playbackSource,
            shouldPlay = uiState.isPlaying,
            onEnded = onVideoEnded,
            modifier = Modifier.fillMaxWidth().height(430.dp),
        )
        NowPlayingCard(uiState, onEvent)
    }
}

@Composable
private fun HostQrCode(address: String) {
    if (!address.contains(":")) return
    val payload = "nkra://host?address=$address"
    val bitmap = remember(payload) {
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 220, 220)
        Bitmap.createBitmap(220, 220, Bitmap.Config.RGB_565).also { output ->
            for (x in 0 until 220) for (y in 0 until 220) {
                output.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Image(bitmap.asImageBitmap(), contentDescription = "QR kết nối nKara Host", modifier = Modifier.padding(8.dp).height(128.dp).width(128.dp))
    }
}

@Composable
private fun NowPlayingCard(uiState: KaraUiState, onEvent: (KaraUiEvent) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17212B)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ĐANG HÁT", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(uiState.currentSong?.title ?: "Chưa có bài hát", style = MaterialTheme.typography.titleLarge)
            Text("Tiếp theo: ${uiState.nextSong?.title ?: "Chưa có bài"}")
            Row {
                ElevatedButton(onClick = { onEvent(KaraUiEvent.TogglePlayback) }) {
                    Text(if (uiState.isPlaying) "Pause" else "Play")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onEvent(KaraUiEvent.Next) }) { Text("Bài tiếp") }
            }
        }
    }
}

@Composable
private fun QueuePanel(
    songs: List<Song>,
    onEvent: (KaraUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF17212B))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DANH SÁCH CHỜ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text("${songs.size} bài còn lại", style = MaterialTheme.typography.headlineSmall)
            if (songs.isEmpty()) Text("Chưa có bài nào trong hàng chờ.")
            songs.take(6).forEach { song ->
                QueueRow(song, { onEvent(KaraUiEvent.Prioritize(song.queueId)) }, { onEvent(KaraUiEvent.Remove(song.queueId)) })
            }
            if (songs.size > 6) Text("+ ${songs.size - 6} bài khác")
        }
    }
}

@Composable
private fun AddSongPanel(value: String, onValueChange: (String) -> Unit, onAdd: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text("YouTube URL hoặc Video ID") }, singleLine = true, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Button(onClick = { onAdd(value) }, enabled = value.isNotBlank()) { Text("Thêm") }
    }
}

@Composable
private fun QueueRow(song: Song, onPrioritize: () -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(song.title, modifier = Modifier.weight(1f))
            TextButton(onClick = onPrioritize) { Text("Ưu tiên") }
            TextButton(onClick = onRemove) { Text("Xóa") }
        }
    }
}

@Composable
private fun NativePlayerPanel(
    source: NativePlaybackSource?,
    shouldPlay: Boolean,
    onEnded: () -> Unit,
    onProgress: (Long, Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(context) {
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(1280, 720)
                    .setMaxVideoBitrate(3_000_000),
            )
        }
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 16 * 1024))
            .setBufferDurationsMs(1_000, 2_000, 500, 750)
            .setTargetBufferBytes(1 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(0, false)
            .build()
        // AOSP TV emulators can abort in MediaCodec's async callback thread when
        // their small managed heap is exhausted. Use the synchronous adapter here:
        // it is slower to schedule but much more stable on low-memory decoders.
        val renderersFactory = DefaultRenderersFactory(context)
            .forceDisableMediaCodecAsynchronousQueueing()
        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
    }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                Log.d(NKARA_PLAYER_LOG_TAG, "state=$playbackState playWhenReady=${player.playWhenReady}")
                if (playbackState == Player.STATE_ENDED) onEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = "Phát lỗi: ${error.errorCodeName}"
                Log.e(NKARA_PLAYER_LOG_TAG, "Media3 playback failed: ${error.errorCodeName}", error)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(source?.videoUrl) {
        player.stop()
        player.clearMediaItems()
        playerError = null
        isBuffering = source != null
        if (source == null) return@LaunchedEffect
        Log.d(NKARA_PLAYER_LOG_TAG, "prepare video=${safeNkaraUrl(source.videoUrl)} audio=${source.audioUrl?.let(::safeNkaraUrl)}")
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(NKARA_USER_AGENT)
            .setDefaultRequestProperties(mapOf("Accept-Language" to "en-US,en;q=0.9", "Cookie" to NKARA_CONSENT_COOKIE))
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(source.videoUrl))
        val mediaSource = source.audioUrl?.let { audioUrl ->
            MergingMediaSource(videoSource, mediaSourceFactory.createMediaSource(MediaItem.fromUri(audioUrl)))
        } ?: videoSource
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = shouldPlay
    }
    LaunchedEffect(shouldPlay, source?.videoUrl) {
        if (source != null) player.playWhenReady = shouldPlay
    }
    LaunchedEffect(player, source?.videoUrl) {
        while (true) {
            onProgress(player.currentPosition, player.duration.takeIf { it > 0 } ?: 0)
            delay(1_000)
        }
    }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player; useController = false } },
        update = { it.player = player },
        modifier = modifier,
    )
    if (isBuffering || playerError != null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(playerError ?: "Đang tải video...", color = Color.White)
        }
    }
}

private const val NKARA_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/138.0.0.0 Safari/537.36"
private const val NKARA_CONSENT_COOKIE = "CONSENT=YES+cb.20210328-17-p0.en+FX+456; SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg"
private const val NKARA_PLAYER_LOG_TAG = "nKaraMedia3"
private fun safeNkaraUrl(url: String): String = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
