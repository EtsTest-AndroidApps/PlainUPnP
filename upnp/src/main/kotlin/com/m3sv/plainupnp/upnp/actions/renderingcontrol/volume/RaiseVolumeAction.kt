package com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume

import org.fourthline.cling.model.meta.Service
import javax.inject.Inject

class RaiseVolumeAction @Inject constructor(
    private val setVolumeAction: SetVolumeAction,
    private val getVolumeAction: GetVolumeAction
) {
    suspend operator fun invoke(service: Service<*, *>, step: Volume): Volume? {
        val currentVolume = getVolumeAction(service) ?: return null
        val newVolume = currentVolume + step
        return setVolumeAction(service, newVolume)
    }
}
