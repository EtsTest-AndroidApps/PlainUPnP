package com.m3sv.plainupnp.upnp.volume

import com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume.GetVolumeAction
import com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume.LowerVolumeAction
import com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume.MuteVolumeAction
import com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume.RaiseVolumeAction
import com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume.SetVolumeAction
import com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume.Volume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.fourthline.cling.model.meta.Service
import javax.inject.Inject

class VolumeManager @Inject constructor(
    private val raiseVolumeAction: RaiseVolumeAction,
    private val lowerVolumeAction: LowerVolumeAction,
    private val getVolumeAction: GetVolumeAction,
    private val setVolumeAction: SetVolumeAction,
    private val muteVolumeAction: MuteVolumeAction,
) {
    private val volumeChannel = MutableSharedFlow<Volume>()
    val volumeFlow: Flow<Volume> = volumeChannel

    suspend fun raiseVolume(service: Service<*, *>, step: Int) {
        val volume = raiseVolumeAction(service, step) ?: return
        postVolume(volume)
    }

    suspend fun lowerVolume(service: Service<*, *>, step: Int) {
        val volume = lowerVolumeAction(service, step) ?: return
        postVolume(volume)
    }

    suspend fun muteVolume(service: Service<*, *>, mute: Boolean): Boolean {
        return muteVolumeAction(service, mute)
    }

    suspend fun setVolume(service: Service<*, *>, newVolume: Volume) {
        val volume = setVolumeAction(service, newVolume) ?: return
        postVolume(volume)
    }

    suspend fun getVolume(service: Service<*, *>): Volume? {
        val volume = getVolumeAction(service) ?: return null
        postVolume(volume)
        return volume
    }

    private suspend fun postVolume(volume: Volume) {
        volumeChannel.emit(volume)
    }
}

