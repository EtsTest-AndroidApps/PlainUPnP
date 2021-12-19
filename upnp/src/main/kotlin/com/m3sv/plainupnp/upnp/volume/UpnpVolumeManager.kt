package com.m3sv.plainupnp.upnp.volume

import com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume.Volume
import kotlinx.coroutines.flow.Flow

interface UpnpVolumeManager {
    val volumeFlow: Flow<Volume>
    suspend fun raiseVolume(step: Int)
    suspend fun lowerVolume(step: Int)
    suspend fun muteVolume(mute: Boolean)
    suspend fun setVolume(volume: Int)
    suspend fun getVolume(): Volume?
}
