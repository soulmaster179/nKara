package com.ntech.nkara.feature.karaoke.domain

import com.ntech.nkara.core.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueStore @Inject constructor() {
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _queuedSongs = MutableStateFlow<List<Song>>(emptyList())
    val queuedSongs: StateFlow<List<Song>> = _queuedSongs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun add(song: Song) {
        if (_currentSong.value == null) {
            _isPlaying.value = true
            _currentSong.value = song
        } else {
            _queuedSongs.value += song
        }
    }

    fun addAsPriority(song: Song) {
        if (_currentSong.value == null) {
            _isPlaying.value = true
            _currentSong.value = song
        } else {
            _queuedSongs.value = listOf(song) + _queuedSongs.value
        }
    }

    fun prioritize(queueId: String) {
        val selected = _queuedSongs.value.firstOrNull { it.queueId == queueId } ?: return
        _queuedSongs.value = listOf(selected) + _queuedSongs.value.filterNot { it.queueId == queueId }
    }

    fun remove(queueId: String) {
        _queuedSongs.value = _queuedSongs.value.filterNot { it.queueId == queueId }
    }

    fun next(): Song? {
        val next = _queuedSongs.value.firstOrNull()
        _currentSong.value = next
        _queuedSongs.value = _queuedSongs.value.drop(1)
        _isPlaying.value = next != null
        return next
    }

    fun play() {
        if (_currentSong.value != null) _isPlaying.value = true
    }

    fun pause() {
        _isPlaying.value = false
    }

    fun togglePlayback() {
        if (_currentSong.value != null) _isPlaying.value = !_isPlaying.value
    }

    fun updateTitle(videoId: String, title: String) {
        _currentSong.value = _currentSong.value?.takeIf { it.videoId != videoId } ?: _currentSong.value?.copy(title = title)
        _queuedSongs.value = _queuedSongs.value.map { song -> if (song.videoId == videoId) song.copy(title = title) else song }
    }
}
