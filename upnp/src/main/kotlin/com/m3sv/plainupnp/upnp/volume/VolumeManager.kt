package com.m3sv.plainupnp.upnp.volume

import com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume.Volume
import kotlinx.coroutines.flow.Flow

interface VolumeManager {
    val volumeFlow: Flow<Volume>
    suspend fun raiseVolume(step: Volume)
    suspend fun lowerVolume(step: Volume)
    suspend fun muteVolume(mute: Boolean)
    suspend fun setVolume(volume: Volume)
    suspend fun getVolume(): Volume?
}
