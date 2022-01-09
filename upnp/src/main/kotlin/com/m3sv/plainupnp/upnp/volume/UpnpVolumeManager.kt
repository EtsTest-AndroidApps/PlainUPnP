package com.m3sv.plainupnp.upnp.volume

import com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume.GetVolumeAction
import com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume.LowerVolumeAction
import com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume.MuteVolumeAction
import com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume.RaiseVolumeAction
import com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume.SetVolumeAction
import com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume.Volume
import com.m3sv.plainupnp.upnp.manager.UpnpManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject

class UpnpVolumeManager @Inject constructor(
    private val upnpManager: UpnpManager,
    private val raiseVolumeAction: RaiseVolumeAction,
    private val lowerVolumeAction: LowerVolumeAction,
    private val getVolumeAction: GetVolumeAction,
    private val setVolumeAction: SetVolumeAction,
    private val muteVolumeAction: MuteVolumeAction,
) : VolumeManager {
    private val volumeChannel = MutableSharedFlow<Volume>()
    override val volumeFlow: Flow<Volume> = volumeChannel

    override suspend fun raiseVolume(step: Volume) {
        upnpManager.rcService?.let { service ->
            val volume = raiseVolumeAction(service, step) ?: return
            postVolume(volume)
        }
    }

    override suspend fun lowerVolume(step: Volume) {
        upnpManager.rcService?.let { service ->
            val volume = lowerVolumeAction(service, step) ?: return
            postVolume(volume)
        }
    }

    override suspend fun muteVolume(mute: Boolean) {
        upnpManager.rcService?.let { service ->
            muteVolumeAction(service, mute)
        }
    }

    override suspend fun setVolume(volume: Volume) {
        upnpManager.rcService?.let { service ->
            val newVolume = setVolumeAction(service, volume) ?: return
            postVolume(newVolume)
        }
    }

    override suspend fun getVolume(): Volume? = upnpManager.rcService?.let { service ->
        val volume = getVolumeAction(service) ?: return null
        postVolume(volume)
        volume
    }

    private suspend fun postVolume(volume: Volume) {
        volumeChannel.emit(volume)
    }
}

