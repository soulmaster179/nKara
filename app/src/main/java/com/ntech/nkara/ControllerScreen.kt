package com.ntech.nkara

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.AsyncImage
import com.ntech.nkara.core.model.Song
import com.ntech.nkara.data.remote.YouTubeSearchVideo
import com.ntech.nkara.feature.controller.ControllerViewModel
import kotlinx.coroutines.delay

private val ControllerBackground = Color(0xFF0B1228)
private val ControllerSurface = Color(0xFF17223A)
private val ControllerBorder = Color(0xFF2D3B56)
private val ControllerBlue = Color(0xFF2F6DF6)
private val ControllerMuted = Color(0xFF92A0B9)

@Composable
fun ControllerScreen(
    onBack: () -> Unit,
    viewModel: ControllerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
    }
    var hostAddress by remember { mutableStateOf(viewModel.savedHostAddress) }
    var query by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(ControllerTab.Search) }
    var showConnectionDialog by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(2_500)
            feedbackMessage = null
        }
    }

    if (showConnectionDialog) {
        HostConnectionDialog(
            hostAddress = hostAddress,
            onAddressChange = { hostAddress = it },
            onConnect = {
                viewModel.connect(hostAddress)
                showConnectionDialog = false
            },
            onDismiss = { showConnectionDialog = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(ControllerBackground).statusBarsPadding()) {
        ControllerHeader(
            isConnected = isConnected,
            connectionStatus = connectionStatus,
            positionMs = snapshot.positionMs,
            durationMs = snapshot.durationMs,
            onPlay = { viewModel.play(); feedbackMessage = if (isConnected) "Đã gửi lệnh Play" else "Chưa kết nối Host" },
            onPause = { viewModel.pause(); feedbackMessage = if (isConnected) "Đã gửi lệnh Pause" else "Chưa kết nối Host" },
            onNext = { viewModel.next(); feedbackMessage = if (isConnected) "Đã gửi lệnh Next" else "Chưa kết nối Host" },
            onConnectionClick = { showConnectionDialog = true },
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (activeTab) {
                ControllerTab.Search -> SearchContent(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { viewModel.search(query) },
                    isLoading = searchState.isLoading,
                    errorMessage = searchState.errorMessage,
                    hasSearched = searchState.hasSearched,
                    videos = searchState.videos,
                    onAdd = {
                        viewModel.addSearchResult(it)
                        feedbackMessage = if (isConnected) "Đã thêm vào hàng chờ" else "Đang lưu lệnh, chờ kết nối Host"
                    },
                    onPrioritize = {
                        viewModel.prioritizeSearchResult(it)
                        feedbackMessage = if (isConnected) "Đã ưu tiên bài hát" else "Đang lưu lệnh, chờ kết nối Host"
                    },
                )
                ControllerTab.Queue -> QueueContent(
                    currentSong = snapshot.currentSong,
                    queuedSongs = snapshot.queuedSongs,
                    onPrioritize = { viewModel.prioritize(it); feedbackMessage = "Đã ưu tiên bài hát" },
                    onRemove = { viewModel.remove(it); feedbackMessage = "Đã xóa khỏi hàng chờ" },
                )
                ControllerTab.Effects -> EffectsContent()
            }
            feedbackMessage?.let { message ->
                Card(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE21365C)),
                ) { Text(message, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }
            }
        }

        ControllerBottomNavigation(
            activeTab = activeTab,
            queueCount = snapshot.queuedSongs.size,
            onSelect = { activeTab = it },
        )
    }
}

@Composable
private fun ControllerHeader(
    isConnected: Boolean,
    connectionStatus: String,
    positionMs: Long,
    durationMs: Long,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onConnectionClick: () -> Unit,
) {
    val connectionColor = when {
        isConnected -> Color(0xFF27C66D)
        connectionStatus.startsWith("Mat") || connectionStatus.startsWith("IP") -> Color(0xFFE34455)
        else -> ControllerMuted
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("nKara", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            }
            HeaderControl(Icons.Default.Pause, "Pause", onPause)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(60.dp).clip(CircleShape).background(ControllerBlue).clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
            Spacer(Modifier.width(8.dp))
            HeaderControl(Icons.Default.SkipNext, "Next", onNext)
            Spacer(Modifier.width(10.dp))
            IconButton(onClick = onConnectionClick) {
                Icon(
                    if (isConnected) Icons.Default.CheckCircle else Icons.Default.SettingsInputAntenna,
                    contentDescription = "Kết nối Host",
                    tint = connectionColor,
                )
            }
        }
        if (durationMs > 0) {
            val progress = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = ControllerBlue, trackColor = ControllerBorder)
            Text("${formatPlaybackTime(positionMs)} / ${formatPlaybackTime(durationMs)}", color = ControllerMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End).padding(top = 3.dp))
        }
    }
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun HeaderControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).border(1.dp, ControllerBorder, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color(0xFFEAF0FF)) }
}

@Composable
private fun SearchContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    hasSearched: Boolean,
    videos: List<YouTubeSearchVideo>,
    onAdd: (YouTubeSearchVideo) -> Unit,
    onPrioritize: (YouTubeSearchVideo) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }),
                shape = RoundedCornerShape(22.dp),
                placeholder = { Text("Tìm bài hát hoặc ca sĩ", color = ControllerMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = ControllerMuted) },
            )
        }
        if (isLoading) item { Text("Đang tìm bài hát…", color = ControllerMuted, modifier = Modifier.padding(12.dp)) }
        errorMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) } }
        if (hasSearched && !isLoading && errorMessage == null && videos.isEmpty()) {
            item { Text("Không tìm thấy bài phù hợp.", color = ControllerMuted, modifier = Modifier.padding(12.dp)) }
        }
        items(videos, key = { it.videoId }) { video ->
            SearchResultCard(video = video, onAdd = { onAdd(video) }, onPrioritize = { onPrioritize(video) })
        }
    }
}

@Composable
private fun SearchResultCard(video: YouTubeSearchVideo, onAdd: () -> Unit, onPrioritize: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ControllerSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, ControllerBorder),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(width = 116.dp, height = 76.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF26344E)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(video.title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text("YOUTUBE VIDEO", color = ControllerMuted, style = MaterialTheme.typography.labelSmall, letterSpacing = androidx.compose.ui.unit.TextUnit(0.08f, androidx.compose.ui.unit.TextUnitType.Em))
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(ControllerBlue).clickable(onClick = onPrioritize),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.ArrowUpward, "Ưu tiên bài hát", tint = Color.White) }
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, ControllerBorder, CircleShape).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Add, "Thêm vào hàng chờ", tint = Color(0xFFEAF0FF)) }
        }
    }
}

@Composable
private fun QueueContent(currentSong: Song?, queuedSongs: List<Song>, onPrioritize: (String) -> Unit, onRemove: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("ĐANG HÁT", color = ControllerBlue, fontWeight = FontWeight.Bold) }
        item { Text(currentSong?.title ?: "Chưa có bài đang phát", color = Color.White, style = MaterialTheme.typography.titleMedium) }
        item { Text("DANH SÁCH ĐÃ CHỌN (${queuedSongs.size})", color = ControllerBlue, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
        items(queuedSongs, key = { it.queueId }) { song ->
            Card(colors = CardDefaults.cardColors(containerColor = ControllerSurface), border = androidx.compose.foundation.BorderStroke(1.dp, ControllerBorder)) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(song.title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onPrioritize(song.queueId) }) { Icon(Icons.Default.ArrowUpward, "Ưu tiên", tint = ControllerBlue) }
                    IconButton(onClick = { onRemove(song.queueId) }) { Icon(Icons.Default.Close, "Xóa", tint = ControllerMuted) }
                }
            }
        }
    }
}

@Composable
private fun EffectsContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Hiệu ứng sẽ có ở phase tiếp theo", color = ControllerMuted)
    }
}

@Composable
private fun ControllerBottomNavigation(activeTab: ControllerTab, queueCount: Int, onSelect: (ControllerTab) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(ControllerSurface).padding(vertical = 12.dp)) {
        BottomTab(ControllerTab.Search, activeTab, Icons.Default.Search, "TÌM BÀI", 0, onSelect, Modifier.weight(1f))
        BottomTab(ControllerTab.Queue, activeTab, Icons.Default.QueueMusic, "ĐÃ CHỌN", queueCount, onSelect, Modifier.weight(1f))
        BottomTab(ControllerTab.Effects, activeTab, Icons.Default.GraphicEq, "HIỆU ỨNG", 0, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun BottomTab(tab: ControllerTab, activeTab: ControllerTab, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, count: Int, onSelect: (ControllerTab) -> Unit, modifier: Modifier) {
    val selected = tab == activeTab
    Column(modifier = modifier.clickable { onSelect(tab) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(icon, label, tint = if (selected) ControllerBlue else ControllerMuted, modifier = Modifier.size(28.dp))
            if (count > 0) Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(Color(0xFFE34455)), contentAlignment = Alignment.Center) { Text(count.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall) }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (selected) ControllerBlue else ControllerMuted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HostConnectionDialog(
    hostAddress: String,
    onAddressChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kết nối nKara Host") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(hostAddress, onAddressChange, label = { Text("Host: 192.168.1.20:8877") }, singleLine = true)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onConnect, enabled = hostAddress.isNotBlank()) { Text("Kết nối") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Đóng") } },
    )
}

private enum class ControllerTab { Search, Queue, Effects }
