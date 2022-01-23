package com.m3sv.plainupnp.upnp.playback

import com.m3sv.plainupnp.common.util.formatTime
import com.m3sv.plainupnp.data.upnp.PlaybackState
import com.m3sv.plainupnp.data.upnp.UpnpItemType
import com.m3sv.plainupnp.logging.Logger
import com.m3sv.plainupnp.upnp.UpnpRepository
import com.m3sv.plainupnp.upnp.manager.Result
import com.m3sv.plainupnp.upnp.manager.UpnpManager
import com.m3sv.plainupnp.upnp.trackmetadata.TrackMetadata
import com.m3sv.plainupnp.upnp.usecase.LaunchLocallyUseCase
import com.m3sv.plainupnp.upnp.util.duration
import com.m3sv.plainupnp.upnp.util.position
import com.m3sv.plainupnp.upnp.util.remainingDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.support.model.PositionInfo
import org.fourthline.cling.support.model.TransportInfo
import org.fourthline.cling.support.model.TransportState
import org.fourthline.cling.support.model.item.AudioItem
import org.fourthline.cling.support.model.item.ImageItem
import org.fourthline.cling.support.model.item.Item
import org.fourthline.cling.support.model.item.PlaylistItem
import org.fourthline.cling.support.model.item.TextItem
import org.fourthline.cling.support.model.item.VideoItem
import javax.inject.Inject

private const val MAX_PROGRESS = 100L

class UpnpPlaybackManager @Inject constructor(
    private val upnpManager: UpnpManager,
    private val upnpRepository: UpnpRepository,
    private val logger: Logger,
    private val launchLocally: LaunchLocallyUseCase,
) : PlaybackManager {

    private val _playbackQueue: MutableStateFlow<LinkedHashSet<PlaybackItem>> = MutableStateFlow(LinkedHashSet())
    override val playbackQueue: StateFlow<Set<PlaybackItem>> = _playbackQueue.asStateFlow()

    private val _playbackState: MutableStateFlow<PlaybackState> = MutableStateFlow(PlaybackState.Empty)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateChannel = MutableSharedFlow<Pair<Item, Service<*, *>>?>()

    init {
        scope.launch {
            updateChannel.scan(launch { }) { accumulator, pair ->
                accumulator.cancel()

                if (pair == null)
                    return@scan launch { }

                val (didlItem, service) = pair

                val type = when (didlItem) {
                    is AudioItem -> UpnpItemType.AUDIO
                    is VideoItem -> UpnpItemType.VIDEO
                    else -> UpnpItemType.UNKNOWN
                }

                val title = didlItem.title
                val artist = didlItem.creator
                val uri = didlItem.firstResource?.value ?: return@scan launch {}

                launch {
                    while (isActive) {
                        delay(500)

                        val transportInfo = async {
                            runCatching { upnpRepository.getTransportInfo(service) }.getOrNull()
                        }

                        val positionInfo = async {
                            runCatching { upnpRepository.getPositionInfo(service) }.getOrNull()
                        }

                        suspend fun processInfo(transportInfo: TransportInfo?, positionInfo: PositionInfo?) {
                            if (transportInfo == null || positionInfo == null)
                                return

                            val state = PlaybackState.Active(
                                uri = uri,
                                type = type,
                                state = transportInfo.currentTransportState,
                                remainingDuration = positionInfo.remainingDuration,
                                duration = positionInfo.duration,
                                position = positionInfo.position,
                                elapsedPercent = positionInfo.elapsedPercent,
                                durationSeconds = positionInfo.trackDurationSeconds,
                                title = title,
                                artist = artist ?: ""
                            )

                            _playbackState.emit(state)

                            if (transportInfo.currentTransportState == TransportState.STOPPED) {
                                _playbackState.emit(PlaybackState.Empty)
                                cancel()
                            }
                        }

                        processInfo(
                            transportInfo.await(),
                            positionInfo.await()
                        )
                    }
                }
            }.collect()
        }
    }

    override suspend fun addToQueue(item: PlaybackItem) {
        _playbackQueue.value = _playbackQueue.value.apply { add(item) }
    }

    override suspend fun removeFromQueue(item: PlaybackItem) {
        _playbackQueue.value = _playbackQueue.value.apply { remove(item) }
    }

    override suspend fun clearQueue() {
        stopPlayback()
        _playbackQueue.value = LinkedHashSet()
    }

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

    override suspend fun play(item: PlaybackItem): Result = withContext(Dispatchers.IO) {
        val clingObject = upnpManager.getUpnpItemById(item.id) ?: return@withContext Result.Error.Generic

        if (upnpManager.selectedRenderer.value == null) {
            launchLocally(clingObject)
            return@withContext Result.Success
        }

        val result = runCatching {
            upnpManager.avService?.let { service ->
                val didlItem = clingObject.didlObject as Item
                val uri = didlItem.firstResource?.value ?: error("First resource or its value is null!")
                val didlType = when (didlItem) {
                    is AudioItem -> "audioItem"
                    is VideoItem -> "videoItem"
                    is ImageItem -> "imageItem"
                    is PlaylistItem -> "playlistItem"
                    is TextItem -> "textItem"
                    else -> null
                }

                val newMetadata = with(didlItem) {
                    // TODO genre && artURI
                    TrackMetadata(
                        id,
                        title,
                        creator,
                        "",
                        "",
                        firstResource.value,
                        "object.item.$didlType"
                    )
                }

                upnpRepository.setUri(service, uri, newMetadata)
                upnpRepository.play(service)

                when (didlItem) {
                    is AudioItem,
                    is VideoItem,
                    -> updateChannel.emit(didlItem to service)
                    is ImageItem -> _playbackState.emit(PlaybackState.Empty)
                }
                Result.Success
            } ?: Result.Error.AvServiceNotFound
        }

        if (result.isSuccess)
            result.getOrThrow()
        else
            Result.Error.Generic
    }
}
