package com.m3sv.plainupnp.upnp.playback

import kotlinx.coroutines.flow.StateFlow

data class PlaybackItem(val id: String, val title: String)

interface PlaybackManager {
    val playbackQueue: StateFlow<Set<PlaybackItem>>

    suspend fun addToQueue(item: PlaybackItem)
    suspend fun removeFromQueue(item: PlaybackItem)
    suspend fun clearQueue()

    suspend fun resumePlayback()
    suspend fun pausePlayback()
    suspend fun stopPlayback()
    suspend fun playNext()
    suspend fun playPrevious()
    suspend fun seekTo(progress: Int)
}
