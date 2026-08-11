package com.ntech.nkara.feature.karaoke.presentation

import com.ntech.nkara.core.model.Song
import com.ntech.nkara.data.remote.NativePlaybackSource

data class KaraUiState(
    val currentSong: Song? = null,
    val queuedSongs: List<Song> = emptyList(),
    val isPlaying: Boolean = false,
    val premiumWarningVisible: Boolean = true,
    val hostAddress: String = "Dang khoi tao LAN...",
    val connectedControllerCount: Int = 0,
    val playbackSource: NativePlaybackSource? = null,
    val playbackError: String? = null,
    val karaokeScore: Int? = null,
) {
    val nextSong: Song? get() = queuedSongs.firstOrNull()
}

sealed interface KaraUiEvent {
    data class AddSong(val input: String) : KaraUiEvent
    data class Prioritize(val queueId: String) : KaraUiEvent
    data class Remove(val queueId: String) : KaraUiEvent
    data object Next : KaraUiEvent
    data object TogglePlayback : KaraUiEvent
    data object DismissPremiumWarning : KaraUiEvent
}
