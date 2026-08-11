package com.ntech.nkara.feature.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntech.nkara.data.remote.NewPipeRepository
import com.ntech.nkara.data.remote.YouTubeSearchVideo
import com.ntech.nkara.feature.lan.LanControllerClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ControllerViewModel @Inject constructor(
    private val client: LanControllerClient,
    private val searchRepository: NewPipeRepository,
) : ViewModel() {
    val connectionStatus = client.connectionStatus
    val isConnected = client.isConnected
    val snapshot = client.snapshot
    val savedHostAddress: String = client.savedHostAddress()
    private val _searchState = MutableStateFlow(YouTubeSearchUiState())
    val searchState = _searchState.asStateFlow()

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

    override fun onCleared() = client.disconnect()
}

data class YouTubeSearchUiState(
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val videos: List<YouTubeSearchVideo> = emptyList(),
    val errorMessage: String? = null,
)
