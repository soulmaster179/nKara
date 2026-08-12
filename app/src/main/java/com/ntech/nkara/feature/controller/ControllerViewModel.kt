package com.ntech.nkara.feature.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntech.nkara.data.remote.NewPipeRepository
import com.ntech.nkara.data.remote.YouTubeSearchVideo
import com.ntech.nkara.data.local.FavoriteSongEntity
import com.ntech.nkara.data.local.FavoriteSongsRepository
import com.ntech.nkara.data.backup.BackupPreferences
import com.ntech.nkara.data.backup.DriveBackupRepository
import com.ntech.nkara.core.model.AudienceReaction
import com.ntech.nkara.feature.lan.LanControllerClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ControllerViewModel @Inject constructor(
    private val client: LanControllerClient,
    private val searchRepository: NewPipeRepository,
    private val favoritesRepository: FavoriteSongsRepository,
    private val driveBackupRepository: DriveBackupRepository,
    private val backupPreferences: BackupPreferences,
) : ViewModel() {
    val connectionStatus = client.connectionStatus
    val isConnected = client.isConnected
    val snapshot = client.snapshot
    val savedHostAddress: String = client.savedHostAddress()
    private val _searchState = MutableStateFlow(YouTubeSearchUiState())
    val searchState = _searchState.asStateFlow()
    val favorites = favoritesRepository.favorites.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    private val _backupState = MutableStateFlow(
        BackupUiState(backupPreferences.isAutoBackupEnabled, backupPreferences.lastSyncEpochMillis),
    )
    val backupState = _backupState.asStateFlow()
    private val _autoBackupRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val autoBackupRequests = _autoBackupRequests.asSharedFlow()

    init {
        if (savedHostAddress.isNotBlank()) client.connect(savedHostAddress)
    }

    fun connect(address: String) = client.connect(address)
    fun add(input: String) = client.add(input)
    fun prioritize(queueId: String) = client.prioritize(queueId)
    fun remove(queueId: String) = client.remove(queueId)
    fun next() = client.next()
    fun play() = client.play()
    fun pause() = client.pause()
    fun react(reaction: AudienceReaction) = client.react(reaction)

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _searchState.value = YouTubeSearchUiState(isLoading = true, hasSearched = true)
            searchRepository.search(query).fold(
                onSuccess = { videos -> _searchState.value = YouTubeSearchUiState(videos = videos, hasSearched = true) },
                onFailure = { error -> _searchState.value = YouTubeSearchUiState(errorMessage = error.message ?: "Khong tim duoc bai") },
            )
        }
    }

    fun addSearchResult(video: YouTubeSearchVideo) = client.add(
        input = "https://youtu.be/${video.videoId}",
        title = video.title,
    )

    fun prioritizeSearchResult(video: YouTubeSearchVideo) = client.add(
        input = "https://youtu.be/${video.videoId}",
        title = video.title,
        priority = true,
    )

    fun addFavoriteToQueue(favorite: FavoriteSongEntity, priority: Boolean = false) = client.add(
        input = "https://youtu.be/${favorite.videoId}",
        title = favorite.title,
        priority = priority,
    )

    fun removeFavorite(favorite: FavoriteSongEntity) {
        viewModelScope.launch {
            favoritesRepository.remove(favorite.videoId)
            if (_backupState.value.isAutoBackupEnabled) _autoBackupRequests.emit(Unit)
        }
    }

    fun toggleFavorite(video: YouTubeSearchVideo) {
        viewModelScope.launch {
            favoritesRepository.toggle(video)
            if (_backupState.value.isAutoBackupEnabled) _autoBackupRequests.emit(Unit)
        }
    }

    fun setAutoBackup(enabled: Boolean) {
        backupPreferences.isAutoBackupEnabled = enabled
        _backupState.value = _backupState.value.copy(isAutoBackupEnabled = enabled)
        if (enabled) _autoBackupRequests.tryEmit(Unit)
    }

    fun backupToDrive(accessToken: String) {
        viewModelScope.launch {
            _backupState.value = _backupState.value.copy(isWorking = true, message = null)
            runCatching { driveBackupRepository.backup(accessToken) }.fold(
                onSuccess = { count -> markSync("Đã sao lưu $count bài yêu thích.") },
                onFailure = { error -> _backupState.value = _backupState.value.copy(isWorking = false, message = error.message ?: "Sao lưu thất bại.") },
            )
        }
    }

    fun restoreFromDrive(accessToken: String) {
        viewModelScope.launch {
            _backupState.value = _backupState.value.copy(isWorking = true, message = null)
            runCatching { driveBackupRepository.restore(accessToken) }.fold(
                onSuccess = { count -> markSync("Đã khôi phục $count bài yêu thích.") },
                onFailure = { error -> _backupState.value = _backupState.value.copy(isWorking = false, message = error.message ?: "Khôi phục thất bại.") },
            )
        }
    }

    private fun markSync(message: String) {
        val now = System.currentTimeMillis()
        backupPreferences.lastSyncEpochMillis = now
        _backupState.value = _backupState.value.copy(isWorking = false, lastSyncEpochMillis = now, message = message)
    }

    override fun onCleared() = client.disconnect()
}

data class YouTubeSearchUiState(
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val videos: List<YouTubeSearchVideo> = emptyList(),
    val errorMessage: String? = null,
)

data class BackupUiState(
    val isAutoBackupEnabled: Boolean = false,
    val lastSyncEpochMillis: Long = 0,
    val isWorking: Boolean = false,
    val message: String? = null,
)
