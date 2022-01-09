package com.m3sv.plainupnp.upnp.manager

import com.m3sv.plainupnp.logging.Logger
import com.m3sv.plainupnp.upnp.UpnpRepository
import com.m3sv.plainupnp.upnp.playback.PlaybackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PlaybackManagerImpl @Inject constructor(
    private val upnpManager: UpnpManager,
    private val upnpRepository: UpnpRepository,
    private val logger: Logger
) : PlaybackManager {

    override suspend fun pausePlayback() {
        withContext(Dispatchers.IO) {
            runCatching {
                upnpManager.avService?.let { service -> upnpRepository.pause(service) }
            }.onFailure { logger.e(it, "Failed to pause playback") }
        }
    }

    override suspend fun stopPlayback() {
        withContext(Dispatchers.IO) {
            runCatching {
                upnpManager.avService?.let { service -> upnpRepository.stop(service) }
            }
        }
    }

    override suspend fun resumePlayback() {
        withContext(Dispatchers.IO) {
            runCatching {
                upnpManager.avService?.let { service -> upnpRepository.play(service) }
            }.onFailure { logger.e(it, "Failed to resume playback") }
        }
    }

    override suspend fun playNext() {
        TODO("Not yet implemented")
    }

    override suspend fun playPrevious() {
        TODO("Not yet implemented")
    }

    override suspend fun togglePlayback() {
//        withContext(Dispatchers.IO) {
//            runCatching {
//                upnpManager.avService?.let { service ->
//                    if (remotePaused)
//                        upnpRepository.play(service)
//                    else
//                        upnpRepository.pause(service)
//                }
//            }.onFailure { logger.e(it, "Failed to toggle playback, is remote paused? $remotePaused") }
//        }
    }
}
