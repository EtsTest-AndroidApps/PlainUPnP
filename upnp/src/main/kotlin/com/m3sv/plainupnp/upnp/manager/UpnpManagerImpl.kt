package com.m3sv.plainupnp.upnp.manager


import com.m3sv.plainupnp.common.util.formatTime
import com.m3sv.plainupnp.data.upnp.*
import com.m3sv.plainupnp.logging.Logger
import com.m3sv.plainupnp.upnp.ContentUpdateState
import com.m3sv.plainupnp.upnp.UpnpContentRepositoryImpl
import com.m3sv.plainupnp.upnp.UpnpRepository
import com.m3sv.plainupnp.upnp.actions.misc.BrowseResult
import com.m3sv.plainupnp.upnp.didl.ClingContainer
import com.m3sv.plainupnp.upnp.didl.ClingDIDLObject
import com.m3sv.plainupnp.upnp.didl.ClingMedia
import com.m3sv.plainupnp.upnp.didl.MiscItem
import com.m3sv.plainupnp.upnp.discovery.device.ContentDirectories
import com.m3sv.plainupnp.upnp.discovery.device.Renderers
import com.m3sv.plainupnp.upnp.folder.Folder
import com.m3sv.plainupnp.upnp.folder.FolderModel
import com.m3sv.plainupnp.upnp.trackmetadata.TrackMetadata
import com.m3sv.plainupnp.upnp.usecase.LaunchLocallyUseCase
import com.m3sv.plainupnp.upnp.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.model.types.UDAServiceType
import org.fourthline.cling.support.model.PositionInfo
import org.fourthline.cling.support.model.TransportInfo
import org.fourthline.cling.support.model.TransportState
import org.fourthline.cling.support.model.item.*
import timber.log.Timber
import java.util.*
import javax.inject.Inject

private const val MAX_PROGRESS = 100
private const val ROOT_FOLDER_ID = "0"
private const val AV_TRANSPORT = "AVTransport"
private const val RENDERING_CONTROL = "RenderingControl"
private const val CONTENT_DIRECTORY = "ContentDirectory"

sealed interface Result {
    object Success : Result
    enum class Error : Result {
        AvServiceNotFound,
        ContentDirectoryNotFound,
        MissingStoragePermission,
        Generic
    }
}

private typealias DeviceCache = Map<String, UpnpDevice>

class UpnpManagerImpl @Inject constructor(
    renderersDiscovery: Renderers,
    contentDirectoriesDiscovery: ContentDirectories,
    private val launchLocally: LaunchLocallyUseCase,
    private val upnpRepository: UpnpRepository,
    private val contentRepository: UpnpContentRepositoryImpl,
    private val logger: Logger
) : UpnpManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _selectedRenderer: MutableStateFlow<UpnpDevice?> = MutableStateFlow(null)
    override val selectedRenderer: StateFlow<UpnpDevice?> = _selectedRenderer.asStateFlow()
    private val selectedContentDirectory: MutableStateFlow<UpnpDevice?> = MutableStateFlow(null)
    private val upnpInnerStateChannel = MutableSharedFlow<UpnpRendererState>()
    override val upnpRendererState: Flow<UpnpRendererState> = upnpInnerStateChannel

    private val _contentDirectories: StateFlow<DeviceCache> =
        contentDirectoriesDiscovery().scan<UpnpDeviceEvent, DeviceCache>(mapOf()) { acc, event ->
            val device = event.device

            when (event) {
                is UpnpDeviceEvent.Added -> acc + (device.identity to device)
                is UpnpDeviceEvent.Removed -> acc - device.identity
            }
        }.stateIn(scope, SharingStarted.Eagerly, mapOf())

    override val contentDirectories: Flow<Collection<UpnpDevice>> = _contentDirectories.map { it.values }

    private val _renderers: StateFlow<DeviceCache> =
        renderersDiscovery().scan<UpnpDeviceEvent, DeviceCache>(mapOf()) { acc, event ->
            val device = event.device

            when (event) {
                is UpnpDeviceEvent.Added -> acc + (device.identity to device)
                is UpnpDeviceEvent.Removed -> acc - device.identity
            }
        }.stateIn(scope, SharingStarted.Eagerly, mapOf())

    override val renderers: Flow<Collection<UpnpDevice>> = _renderers.map { it.values }

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

                            remotePaused = transportInfo.currentTransportState == TransportState.PAUSED_PLAYBACK

                            val state = UpnpRendererState.Default(
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

                            currentDuration = positionInfo.trackDurationSeconds

                            if (!pauseUpdate) upnpInnerStateChannel.emit(state)

                            Timber.d("Got new state: $state")

                            if (transportInfo.currentTransportState == TransportState.STOPPED) {
                                upnpInnerStateChannel.emit(UpnpRendererState.Empty)
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

        scope.launch {
            contentRepository.refreshState.collect {
                if (it is ContentUpdateState.Ready) {
                    val contentDirectory = selectedContentDirectory.value

                    if (contentDirectory != null) {
                        safeNavigateTo(
                            folderId = ROOT_FOLDER_ID,
                            folderName = contentDirectory.friendlyName
                        )
                    }
                }
            }
        }
    }

    override suspend fun selectContentDirectory(id: String?): Result = withContext(Dispatchers.IO) {
        val contentDirectory = _contentDirectories.value[id]
        folderStack.value = listOf()
        contentCache.clear()
        selectedContentDirectory.value = contentDirectory

        when (contentDirectory) {
            null -> Result.Error.ContentDirectoryNotFound
            else -> safeNavigateTo(
                folderId = ROOT_FOLDER_ID,
                folderName = contentDirectory.friendlyName
            )
        }
    }

    override suspend fun selectRenderer(id: String?) {
        withContext(Dispatchers.IO) {
            _selectedRenderer.value = if (selectedRenderer.value?.identity != id) {
                _renderers.value[id]
            } else {
                null
            }
        }
    }

    private var currentDuration: Long = 0L

    private var pauseUpdate = false

    private var remotePaused = false

    override suspend fun itemClick(id: String): Result = withContext(Dispatchers.IO) {
        val item: ClingDIDLObject = contentCache[id] ?: return@withContext Result.Error.Generic

        when (item) {
            is ClingContainer -> safeNavigateTo(folderId = id, folderName = item.title)
            is ClingMedia -> playItem(item)
            is MiscItem -> Result.Error.Generic
        }
    }

    private suspend fun playItem(item: ClingDIDLObject): Result = withContext(Dispatchers.IO) {
        if (selectedRenderer.value == null) {
            launchLocally(item)
            return@withContext Result.Success
        }

        val result = runCatching {
            withAvService { service ->
                val didlItem = item.didlObject as Item
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
                    is ImageItem -> upnpInnerStateChannel.emit(UpnpRendererState.Empty)
                }
                Result.Success
            } ?: Result.Error.AvServiceNotFound
        }

        if (result.isSuccess)
            result.getOrThrow()
        else
            Result.Error.Generic
    }

    override suspend fun seekTo(progress: Int) {
        withContext(Dispatchers.IO) {
            runCatching {
                avService?.let { service ->
                    pauseUpdate = true
                    upnpRepository.seekTo(
                        service = service,
                        time = formatTime(
                            max = MAX_PROGRESS,
                            progress = progress,
                            duration = currentDuration
                        )
                    )
                    pauseUpdate = false
                }
            }.onFailure { logger.e(it, "Failed to seek progress $progress") }
        }
    }

    override suspend fun navigateTo(folder: Folder) {
        withContext(Dispatchers.IO) {
            val index = folderStack.value.indexOf(folder)

            if (index == -1) {
                logger.e("Folder $folder isn't found in navigation stack!")
                return@withContext
            }

            folderStack.value = folderStack.value.subList(0, index + 1)
        }
    }

    override suspend fun navigateBack() {
        folderStack.value = folderStack.value.dropLast(1)
    }


    private val contentCache: MutableMap<String, ClingDIDLObject> = mutableMapOf()

    private var currentContent: MutableStateFlow<List<ClingDIDLObject>> = MutableStateFlow(listOf())

    private val folderStack: MutableStateFlow<List<Folder>> = MutableStateFlow(listOf())

    override val navigationStack: Flow<List<Folder>> = folderStack.onEach { folders ->
        if (folders.isEmpty()) {
            _selectedRenderer.value = null
        }
    }

    private suspend inline fun safeNavigateTo(
        folderId: String,
        folderName: String,
    ): Result = withContext(Dispatchers.IO) {
        Timber.d("Navigating to $folderId with name $folderName")

        val selectedDevice = selectedContentDirectory.value

        if (selectedDevice == null) {
            logger.e("Selected content directory is null!")
            return@withContext Result.Error.Generic
        }

        val service: Service<*, *>? =
            selectedDevice.device.findService(UDAServiceType(CONTENT_DIRECTORY))

        if (service == null || !service.hasActions()) {
            logger.e("Service is null or has no actions")
            return@withContext Result.Error.Generic
        }

        when (val result = upnpRepository.browse(service, folderId)) {
            is BrowseResult.Error.Generic -> Result.Error.Generic
            is BrowseResult.Error.MissingStoragePermission -> Result.Error.MissingStoragePermission
            is BrowseResult.Success -> {
                currentContent.value = result.contents
                contentCache.putAll(currentContent.value.associateBy { it.id })
                val currentFolderName = folderName.replace(UpnpContentRepositoryImpl.USER_DEFINED_PREFIX, "")

                val folder = when (folderId) {
                    ROOT_FOLDER_ID -> Folder.Root(
                        FolderModel(
                            id = folderId,
                            title = currentFolderName,
                            contents = currentContent.value
                        )
                    )
                    else -> Folder.SubFolder(
                        FolderModel(
                            id = folderId,
                            title = currentFolderName,
                            contents = currentContent.value
                        )
                    )
                }

                folderStack.value = when (folder) {
                    is Folder.Root -> listOf(folder)
                    is Folder.SubFolder -> folderStack
                        .value
                        .toMutableList()
                        .apply { add(folder) }
                        .toList()
                }

                Result.Success
            }
        }
    }

    override val avService: Service<*, *>?
        get() = selectedRenderer
            .value
            ?.let { renderer ->
                val service: Service<*, *> = renderer
                    .device
                    .findService(UDAServiceType(AV_TRANSPORT))
                    ?: return null

                if (service.hasActions()) {
                    service
                } else {
                    null
                }
            }

    override val rcService: Service<*, *>?
        get() = selectedRenderer
            .value?.let { renderer ->
                val service: Service<*, *> = renderer
                    .device
                    .findService(UDAServiceType(RENDERING_CONTROL))
                    ?: return null

                if (service.hasActions())
                    service
                else {
                    null
                }
            }

    private suspend fun <T> withAvService(block: suspend (Service<*, *>) -> T): T? {
        return when (val avService = avService) {
            null -> {
                logger.e("Av service is not found!")
                null
            }
            else -> block(avService)
        }
    }

    private suspend fun <T> withRcService(block: suspend (Service<*, *>) -> T): T? {
        return when (val rcService = rcService) {
            null -> {
                logger.e("Rc service is not found!")
                null
            }
            else -> block(rcService)
        }
    }
}
