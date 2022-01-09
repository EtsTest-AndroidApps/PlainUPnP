package com.m3sv.plainupnp.upnp.playback

import com.m3sv.plainupnp.common.util.formatTime
import com.m3sv.plainupnp.logging.Logger
import com.m3sv.plainupnp.upnp.UpnpRepository
import com.m3sv.plainupnp.upnp.manager.UpnpManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val MAX_PROGRESS = 100L

class UpnpPlaybackManager @Inject constructor(
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

    override suspend fun seekTo(progress: Int) {
        upnpManager.avService?.let { service ->
            withContext(Dispatchers.IO) {
                try {
                    val positionInfo = upnpRepository.getPositionInfo(service)
                    val trackDuration = positionInfo?.trackDurationSeconds ?: return@withContext

                    upnpRepository.seekTo(
                        service = service,
                        time = formatTime(
                            max = MAX_PROGRESS,
                            progress = progress.toLong(),
                            duration = trackDuration
                        )
                    )
                } catch (e: Exception) {
                    logger.e(e, "Failed to seek progress $progress")
                }
            }
        }
    }
}
