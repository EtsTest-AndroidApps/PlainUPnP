package com.m3sv.plainupnp.presentation.main

import com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume.Volume
import com.m3sv.plainupnp.upnp.volume.VolumeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.properties.Delegates


class BufferedVolumeManager @Inject constructor(volumeManager: VolumeManager) : VolumeManager by volumeManager {

    private var timeoutJob: Job? = null

    private var currentStep: Int by Delegates.vetoable(1) { _, _, new ->
        new <= MAX_STEP
    }

    suspend fun lowerVolume() {
        lowerVolume(Volume(currentStep))
        triggerStep()
    }

    suspend fun raiseVolume() {
        raiseVolume(Volume(currentStep))
        triggerStep()
    }

    private suspend fun triggerStep() = coroutineScope {
        timeoutJob?.cancel()
        timeoutJob = launch {
            delay(2000)
            currentStep = 1
        }

        currentStep++
    }

    companion object {
        private const val MAX_STEP = 3
    }
}
