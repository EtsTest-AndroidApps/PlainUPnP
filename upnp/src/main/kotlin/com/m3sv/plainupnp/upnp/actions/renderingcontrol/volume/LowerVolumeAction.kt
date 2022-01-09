package com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume

import org.fourthline.cling.model.meta.Service
import javax.inject.Inject

class LowerVolumeAction @Inject constructor(
    private val setVolumeAction: SetVolumeAction,
    private val getVolumeAction: GetVolumeAction
) {
    suspend operator fun invoke(
        renderingService: Service<*, *>,
        step: Volume
    ): Volume? {
        val currentVolume = getVolumeAction(renderingService) ?: return null

        var delta = currentVolume - step

        if (delta.value < 0) {
            delta = Volume(0)
        }

        return setVolumeAction(renderingService, delta)
    }
}
