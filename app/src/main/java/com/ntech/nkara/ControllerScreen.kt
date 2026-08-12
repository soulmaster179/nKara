package com.ntech.nkara

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.ntech.nkara.core.model.AudienceReaction
import com.ntech.nkara.core.model.Song
import com.ntech.nkara.data.local.FavoriteSongEntity
import com.ntech.nkara.data.remote.YouTubeSearchVideo
import com.ntech.nkara.feature.controller.BackupUiState
import com.ntech.nkara.feature.controller.ControllerViewModel
import kotlinx.coroutines.delay

private val ControllerBackground = Color(0xFF0B1228)
private val ControllerSurface = Color(0xFF17223A)
private val ControllerBorder = Color(0xFF2D3B56)
private val ControllerBlue = Color(0xFF2F6DF6)
private val ControllerMuted = Color(0xFF92A0B9)

@Composable
fun ControllerScreen(onBack: () -> Unit, viewModel: ControllerViewModel = hiltViewModel()) {
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
    var selectedVideo by remember { mutableStateOf<YouTubeSearchVideo?>(null) }
    var selectedFavorite by remember { mutableStateOf<FavoriteSongEntity?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var pendingDriveAction by remember { mutableStateOf<DriveAction?>(null) }
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()

    fun runDriveAction(token: String) {
        when (pendingDriveAction) {
            DriveAction.Backup -> viewModel.backupToDrive(token)
            DriveAction.Restore -> viewModel.restoreFromDrive(token)
            null -> Unit
        }
        pendingDriveAction = null
    }
    val authorizationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        try {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data)
                .accessToken?.let(::runDriveAction)
        } catch (_: ApiException) {
            feedbackMessage = "Không cấp được quyền Google Drive"
            pendingDriveAction = null
        }
    }
    fun authorizeDrive(action: DriveAction) {
        val activity = context as? Activity ?: return
        pendingDriveAction = action
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        Identity.getAuthorizationClient(activity).authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    result.pendingIntent?.let { pending ->
                        authorizationLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    }
                } else result.accessToken?.let(::runDriveAction)
            }
            .addOnFailureListener { feedbackMessage = "Không kết nối được Google Drive"; pendingDriveAction = null }
    }

    LaunchedEffect(feedbackMessage) { if (feedbackMessage != null) { delay(2_500); feedbackMessage = null } }
    LaunchedEffect(Unit) {
        viewModel.autoBackupRequests.collect {
            authorizeDrive(DriveAction.Backup)
        }
    }
    backupState.message?.let { message -> LaunchedEffect(message) { feedbackMessage = message } }

    if (showConnectionDialog) HostConnectionDialog(
        hostAddress = hostAddress,
        onAddressChange = { hostAddress = it },
        onConnect = { viewModel.connect(hostAddress); showConnectionDialog = false },
        onDismiss = { showConnectionDialog = false },
    )
    selectedVideo?.let { video -> SearchResultActionsDialog(
        video = video,
        isFavorite = favorites.any { it.videoId == video.videoId },
        onAdd = { viewModel.addSearchResult(video); selectedVideo = null; feedbackMessage = "Đã thêm vào hàng chờ" },
        onPrioritize = { viewModel.prioritizeSearchResult(video); selectedVideo = null; feedbackMessage = "Đã ưu tiên bài hát" },
        onFavorite = { viewModel.toggleFavorite(video); selectedVideo = null },
        onDismiss = { selectedVideo = null },
    ) }
    selectedFavorite?.let { favorite -> FavoriteActionsSheet(
        favorite = favorite,
        onAdd = { viewModel.addFavoriteToQueue(favorite); selectedFavorite = null; feedbackMessage = "Đã thêm vào hàng chờ" },
        onPrioritize = { viewModel.addFavoriteToQueue(favorite, priority = true); selectedFavorite = null; feedbackMessage = "Đã ưu tiên bài hát" },
        onRemove = { viewModel.removeFavorite(favorite); selectedFavorite = null; feedbackMessage = "Đã xóa khỏi yêu thích" },
        onDismiss = { selectedFavorite = null },
    ) }

    Column(Modifier.fillMaxSize().background(ControllerBackground).statusBarsPadding()) {
        ControllerHeader(isConnected, connectionStatus, snapshot.positionMs, snapshot.durationMs,
            onPlay = viewModel::play, onPause = viewModel::pause, onNext = viewModel::next,
            onConnectionClick = { showConnectionDialog = true })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (activeTab) {
                ControllerTab.Search -> SearchContent(query, { query = it }, { query = "" }, { viewModel.search(query) }, searchState.isLoading,
                    searchState.errorMessage, searchState.hasSearched, searchState.videos, onSelect = { selectedVideo = it })
                ControllerTab.Queue -> QueueContent(snapshot.currentSong, snapshot.queuedSongs, viewModel::prioritize, viewModel::remove)
                ControllerTab.Favorites -> FavoritesContent(favorites, backupState,
                    onSelect = { selectedFavorite = it },
                    onBackup = { authorizeDrive(DriveAction.Backup) },
                    onRestore = { authorizeDrive(DriveAction.Restore) },
                    onAutoBackupChange = viewModel::setAutoBackup)
                ControllerTab.Effects -> EffectsContent(isConnected) { reaction ->
                    viewModel.react(reaction)
                    feedbackMessage = if (isConnected) "Đã gửi ${reaction.label}" else "Chưa kết nối Host"
                }
            }
            feedbackMessage?.let { Card(Modifier.align(Alignment.TopCenter).padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xEE21365C))) {
                Text(it, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            } }
        }
        ControllerBottomNavigation(activeTab, snapshot.queuedSongs.size) { activeTab = it }
    }
}

@Composable
private fun ControllerHeader(isConnected: Boolean, status: String, positionMs: Long, durationMs: Long,
    onPlay: () -> Unit, onPause: () -> Unit, onNext: () -> Unit, onConnectionClick: () -> Unit) {
    val color = when { isConnected -> Color(0xFF27C66D); status.startsWith("Mat") || status.startsWith("Mất") -> Color(0xFFE34455); else -> ControllerMuted }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("nKara", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            HeaderControl(Icons.Default.Pause, "Tạm dừng", onPause); Spacer(Modifier.width(8.dp))
            Box(Modifier.size(58.dp).clip(CircleShape).background(ControllerBlue).clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, "Phát", tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(8.dp)); HeaderControl(Icons.Default.SkipNext, "Bài tiếp", onNext); Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onConnectionClick)) {
                Icon(Icons.Default.WifiTethering, "Kết nối Host", tint = color, modifier = Modifier.size(30.dp))
                if (!isConnected) Text("↑ kết nối", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(status, color = color, fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
        if (durationMs > 0) {
            LinearProgressIndicator(progress = { (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(4.dp), color = ControllerBlue, trackColor = ControllerBorder)
            Text("${formatTime(positionMs)} / ${formatTime(durationMs)}", color = ControllerMuted, fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable private fun HeaderControl(icon: ImageVector, label: String, onClick: () -> Unit) = Box(
    Modifier.size(46.dp).clip(CircleShape).border(1.dp, ControllerBorder, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center,
) { Icon(icon, label, tint = Color(0xFFEAF0FF)) }

@Composable
private fun SearchContent(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit, onSearch: () -> Unit, isLoading: Boolean,
    error: String?, hasSearched: Boolean, videos: List<YouTubeSearchVideo>, onSelect: (YouTubeSearchVideo) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(query, onQueryChange, Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            shape = RoundedCornerShape(22.dp), placeholder = { Text("Tìm bài hát hoặc ca sĩ") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Xóa nội dung tìm kiếm") } }) }
        if (isLoading) item { Text("Đang tìm bài hát…", color = ControllerMuted) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (hasSearched && !isLoading && error == null && videos.isEmpty()) item { Text("Không tìm thấy bài phù hợp.", color = ControllerMuted) }
        items(videos, key = { it.videoId }) { SearchResultCard(it) { onSelect(it) } }
    }
}

@Composable
private fun SearchResultCard(video: YouTubeSearchVideo, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ControllerSurface), border = BorderStroke(1.dp, ControllerBorder)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(video.thumbnailUrl, null, Modifier.size(112.dp, 72.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF26344E)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, color = Color.White, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp)); Text("Nhấn để chọn thao tác", color = ControllerMuted, fontSize = 10.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResultActionsDialog(video: YouTubeSearchVideo, isFavorite: Boolean, onAdd: () -> Unit, onPrioritize: () -> Unit, onFavorite: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ControllerSurface, contentColor = Color.White, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(video.thumbnailUrl, null, Modifier.size(100.dp, 64.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(14.dp)); Text(video.title, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            ActionRow(Icons.Default.QueueMusic, "Thêm vào hàng chờ", onAdd)
            ActionRow(Icons.Default.ArrowUpward, "Ưu tiên hát tiếp", onPrioritize)
            ActionRow(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isFavorite) "Bỏ yêu thích" else "Lưu vào yêu thích", onFavorite)
        }
    }
}

@Composable private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
) { Icon(icon, null, tint = ControllerBlue); Spacer(Modifier.width(12.dp)); Text(label) }

@Composable
private fun FavoritesContent(favorites: List<FavoriteSongEntity>, backupState: BackupUiState, onSelect: (FavoriteSongEntity) -> Unit,
    onBackup: () -> Unit, onRestore: () -> Unit, onAutoBackupChange: (Boolean) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("BÀI YÊU THÍCH", color = ControllerBlue, fontWeight = FontWeight.Bold) }
        item { Card(colors = CardDefaults.cardColors(containerColor = ControllerSurface)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudSync, null, tint = ControllerBlue, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) { Text("Drive", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold); Text(if (backupState.isAutoBackupEnabled) "Tự động sao lưu" else "Sao lưu đang tắt", color = ControllerMuted, fontSize = 9.sp) }
                IconButton(onClick = onBackup, enabled = !backupState.isWorking) { Icon(Icons.Default.CloudSync, "Đồng bộ", tint = ControllerBlue) }
                IconButton(onClick = onRestore, enabled = !backupState.isWorking) { Icon(Icons.Default.Restore, "Khôi phục", tint = ControllerMuted) }
                Switch(checked = backupState.isAutoBackupEnabled, onCheckedChange = onAutoBackupChange, modifier = Modifier.size(width = 44.dp, height = 28.dp))
            }
        } }
        if (favorites.isEmpty()) item { Text("Chưa có bài yêu thích. Hãy nhấn một kết quả tìm kiếm để lưu.", color = ControllerMuted) }
        items(favorites, key = { it.videoId }) { favorite -> Card(Modifier.fillMaxWidth().clickable { onSelect(favorite) }, colors = CardDefaults.cardColors(containerColor = ControllerSurface)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF5C7A)); Spacer(Modifier.width(12.dp)); Text(favorite.title, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 3); Text("•••", color = ControllerMuted, fontWeight = FontWeight.Bold) }
        } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteActionsSheet(favorite: FavoriteSongEntity, onAdd: () -> Unit, onPrioritize: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ControllerSurface, contentColor = Color.White, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF5C7A), modifier = Modifier.size(34.dp)); Spacer(Modifier.width(12.dp)); Text(favorite.title, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)) }
            Spacer(Modifier.height(6.dp)); ActionRow(Icons.Default.QueueMusic, "Thêm vào hàng chờ", onAdd); ActionRow(Icons.Default.ArrowUpward, "Ưu tiên hát tiếp", onPrioritize); ActionRow(Icons.Default.Close, "Xóa khỏi yêu thích", onRemove)
        }
    }
}

@Composable
private fun EffectsContent(isConnected: Boolean, onReaction: (AudienceReaction) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("CỔ VŨ NGƯỜI ĐANG HÁT", color = ControllerBlue, fontWeight = FontWeight.Bold)
        Text(if (isConnected) "Chạm để gửi lên màn hình Host" else "Kết nối Host trước khi gửi", color = ControllerMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        LazyVerticalGrid(GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            gridItems(AudienceReaction.entries) { reaction ->
                Card(Modifier.fillMaxWidth().clickable(enabled = isConnected) { onReaction(reaction) }, colors = CardDefaults.cardColors(containerColor = ControllerSurface), border = BorderStroke(1.dp, ControllerBorder)) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(reaction.emoji, fontSize = 34.sp); Text(reaction.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun QueueContent(current: Song?, queue: List<Song>, onPrioritize: (String) -> Unit, onRemove: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("ĐANG HÁT", color = ControllerBlue, fontWeight = FontWeight.Bold); Text(current?.title ?: "Chưa có bài đang phát", color = Color.White) }
        item { Text("HÀNG CHỜ (${queue.size})", color = ControllerBlue, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        items(queue, key = { it.queueId }) { song -> Card(colors = CardDefaults.cardColors(containerColor = ControllerSurface)) { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(song.title, color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp, maxLines = 2); IconButton({ onPrioritize(song.queueId) }) { Icon(Icons.Default.ArrowUpward, "Ưu tiên", tint = ControllerBlue) }; IconButton({ onRemove(song.queueId) }) { Icon(Icons.Default.Close, "Xóa", tint = ControllerMuted) }
        } } }
    }
}

@Composable
private fun ControllerBottomNavigation(active: ControllerTab, queueCount: Int, onSelect: (ControllerTab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(ControllerSurface).padding(vertical = 10.dp)) {
        BottomTab(ControllerTab.Search, active, Icons.Default.Search, "TÌM", 0, onSelect, Modifier.weight(1f))
        BottomTab(ControllerTab.Queue, active, Icons.Default.QueueMusic, "ĐÃ CHỌN", queueCount, onSelect, Modifier.weight(1f))
        BottomTab(ControllerTab.Favorites, active, Icons.Default.Favorite, "YÊU THÍCH", 0, onSelect, Modifier.weight(1f))
        BottomTab(ControllerTab.Effects, active, Icons.Default.GraphicEq, "CỔ VŨ", 0, onSelect, Modifier.weight(1f))
    }
}

@Composable private fun BottomTab(tab: ControllerTab, active: ControllerTab, icon: ImageVector, label: String, count: Int, onSelect: (ControllerTab) -> Unit, modifier: Modifier) {
    val selected = tab == active
    Column(modifier.clickable { onSelect(tab) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd) { Icon(icon, label, tint = if (selected) ControllerBlue else ControllerMuted, modifier = Modifier.size(25.dp)); if (count > 0) Text(count.toString(), color = Color.White, fontSize = 9.sp, modifier = Modifier.background(Color.Red, CircleShape).padding(horizontal = 5.dp)) }
        Text(label, color = if (selected) ControllerBlue else ControllerMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun HostConnectionDialog(hostAddress: String, onAddressChange: (String) -> Unit, onConnect: () -> Unit, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss, title = { Text("Kết nối nKara Host") }, text = { OutlinedTextField(hostAddress, onAddressChange, label = { Text("192.168.1.20:8877") }, singleLine = true) },
    confirmButton = { TextButton(onConnect, enabled = hostAddress.isNotBlank()) { Text("Kết nối") } }, dismissButton = { TextButton(onDismiss) { Text("Đóng") } },
)

private fun formatTime(milliseconds: Long): String { val seconds = (milliseconds / 1_000).coerceAtLeast(0); return "%d:%02d".format(seconds / 60, seconds % 60) }
private enum class ControllerTab { Search, Queue, Favorites, Effects }
private enum class DriveAction { Backup, Restore }
private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
