package com.ntech.nkara.feature.karaoke.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntech.nkara.core.model.Song
import com.ntech.nkara.data.local.SingingHistoryEntity
import com.ntech.nkara.data.local.SongDao
import com.ntech.nkara.data.remote.NativePlaybackSource
import com.ntech.nkara.data.remote.NewPipeRepository
import com.ntech.nkara.feature.karaoke.domain.QueueStore
import com.ntech.nkara.feature.lan.LanHostServer
import com.ntech.nkara.feature.lan.LanNetworkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@HiltViewModel
class KaraViewModel @Inject constructor(
    private val queueStore: QueueStore,
    private val songDao: SongDao,
    private val lanHostServer: LanHostServer,
    private val newPipeRepository: NewPipeRepository,
) : ViewModel() {
    private val premiumWarningVisible = MutableStateFlow(true)
    private val hostAddress = MutableStateFlow(LanNetworkInfo.hostAddress())
    private val playbackSource = MutableStateFlow<NativePlaybackSource?>(null)
    private val playbackError = MutableStateFlow<String?>(null)
    private val karaokeScore = MutableStateFlow<Int?>(null)
    private var scoreTransitionJob: Job? = null

    init {
        lanHostServer.start()
        viewModelScope.launch {
            queueStore.currentSong.map { it?.videoId }.distinctUntilChanged().collectLatest { videoId ->
                playbackSource.value = null
                playbackError.value = null
                if (videoId == null) return@collectLatest
                newPipeRepository.resolvePlayback(videoId).fold(
                    onSuccess = { source -> playbackSource.value = source },
                    onFailure = { error -> playbackError.value = error.message ?: "Khong tai duoc luong video." },
                )
            }
        }
    }

    private val baseQueueUiState = combine(
        queueStore.currentSong,
        queueStore.queuedSongs,
        queueStore.isPlaying,
        premiumWarningVisible,
        hostAddress,
    ) { current, queue, playing, warning, address ->
        KaraUiState(
            currentSong = current,
            queuedSongs = queue,
            isPlaying = playing,
            premiumWarningVisible = warning,
            hostAddress = address,
        )
    }

    private val queueUiState = combine(baseQueueUiState, playbackSource, playbackError, karaokeScore) { state, source, error, score ->
        state.copy(playbackSource = source, playbackError = error, karaokeScore = score)
    }

    private val hostConnectionState = combine(lanHostServer.connectedClientCount, lanHostServer.latestReaction, lanHostServer.latestNotice) { count, reaction, notice ->
        Triple(count, reaction, notice)
    }

    val uiState = combine(queueUiState, hostConnectionState) { state, hostState ->
        state.copy(connectedControllerCount = hostState.first, latestReaction = hostState.second, latestNotice = hostState.third)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KaraUiState())

    fun onEvent(event: KaraUiEvent) {
        when (event) {
            is KaraUiEvent.AddSong -> parseVideoId(event.input)?.let {
                queueStore.add(Song(videoId = it))
                queueStore.play()
            }
            is KaraUiEvent.Prioritize -> queueStore.prioritize(event.queueId)
            is KaraUiEvent.Remove -> queueStore.remove(event.queueId)
            KaraUiEvent.Next -> {
                scoreTransitionJob?.cancel()
                karaokeScore.value = null
                advanceQueue()
            }
            KaraUiEvent.TogglePlayback -> queueStore.togglePlayback()
            KaraUiEvent.DismissPremiumWarning -> premiumWarningVisible.value = false
        }
    }

    fun onVideoEnded() {
        if (queueStore.currentSong.value == null || scoreTransitionJob?.isActive == true) return
        scoreTransitionJob = viewModelScope.launch {
            karaokeScore.value = Random.nextInt(from = 80, until = 101)
            // Keep the result visible long enough for the six-second applause audio.
            delay(8_000)
            karaokeScore.value = null
            advanceQueue()
        }
    }

    fun updatePlaybackProgress(positionMs: Long, durationMs: Long) {
        lanHostServer.updatePlaybackProgress(positionMs, durationMs)
    }

    fun pauseForBackground() {
        if (queueStore.isPlaying.value) queueStore.pause()
    }

    private fun advanceQueue() {
        queueStore.currentSong.value?.let { completed ->
            viewModelScope.launch {
                songDao.insertHistory(
                    SingingHistoryEntity(
                        videoId = completed.videoId,
                        title = completed.title,
                        playedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
        queueStore.next()
    }

    private fun parseVideoId(input: String): String? {
        val value = input.trim()
        val match = VIDEO_ID_PATTERN.find(value)?.groupValues?.getOrNull(1)
        return match ?: value.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }

    private companion object {
        val VIDEO_ID_PATTERN = Regex("(?:v=|youtu\\.be/|embed/)([A-Za-z0-9_-]{11})")
    }
}
