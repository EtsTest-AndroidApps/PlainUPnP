package com.m3sv.plainupnp.upnp

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.documentfile.provider.DocumentFile
import com.m3sv.plainupnp.ContentModel
import com.m3sv.plainupnp.ContentRepository
import com.m3sv.plainupnp.common.preferences.PreferencesRepository
import com.m3sv.plainupnp.logging.Logger
import com.m3sv.plainupnp.upnp.mediacontainers.AllAudioContainer
import com.m3sv.plainupnp.upnp.mediacontainers.AllImagesContainer
import com.m3sv.plainupnp.upnp.mediacontainers.AllVideoContainer
import com.m3sv.plainupnp.upnp.mediacontainers.BaseContainer
import com.m3sv.plainupnp.upnp.mediacontainers.DefaultContainer
import com.m3sv.plainupnp.upnp.util.PORT
import com.m3sv.plainupnp.upnp.util.addAudioItem
import com.m3sv.plainupnp.upnp.util.addImageItem
import com.m3sv.plainupnp.upnp.util.addVideoItem
import com.m3sv.plainupnp.upnp.util.getLocalIpAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

sealed class ContentUpdateState {
    object Loading : ContentUpdateState()
    object Ready : ContentUpdateState()
}

@Singleton
class UpnpContentRepositoryImpl @Inject constructor(
    private val application: Application,
    private val preferencesRepository: PreferencesRepository,
    private val logger: Logger
) : ContentRepository {

    var containerCache: Map<Long, BaseContainer> = emptyMap()
        private set

    private val _contentCache: MutableMap<Long, ContentModel> = mutableMapOf()
    override val contentCache: Map<Long, ContentModel> = _contentCache

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val appName by lazy { application.getString(R.string.app_name) }

    private val baseUrl: String by lazy {
        val localIpAddress = getLocalIpAddress(application, logger).hostAddress
        "$localIpAddress:$PORT"
    }

    private val _refreshState: MutableStateFlow<ContentUpdateState> = MutableStateFlow(ContentUpdateState.Ready)
    val refreshState: Flow<ContentUpdateState> = _refreshState

    init {
        scope.launch {
            preferencesRepository
                .updateFlow
                .debounce(2000)
                .onEach { refreshContent() }
                .collect()
        }
    }

    private val init by lazy {
        runBlocking { refreshInternal() }
    }

    override fun init() {
        init
    }

    override fun refreshContent() {
        scope.launch {
            _refreshState.value = ContentUpdateState.Loading
            refreshInternal()
            _refreshState.value = ContentUpdateState.Ready
        }
    }

    private suspend fun refreshInternal() {
        val result: MutableMap<Long, BaseContainer> = mutableMapOf()

        val rootContainer = DefaultContainer(
            ROOT_ID.toString(),
            ROOT_ID.toString(),
            appName,
            appName
        ).also { container -> result[container.rawId.toLong()] = container }

        val preferences = preferencesRepository.preferences.value

        fun MutableMap<Long, BaseContainer>.addContainer(container: BaseContainer) {
            this[container.rawId.toLong()] = container
        }

        coroutineScope {
            if (preferences.enableImages) {
                launch {
                    val imagesContainer = getRootImagesContainer()
                    rootContainer.addContainer(imagesContainer)
                    result.addContainer(imagesContainer)
                }
            }

            if (preferences.enableAudio) {
                launch {
                    val audioContainer = getRootAudioContainer()
                    rootContainer.addContainer(audioContainer)
                    result.addContainer(audioContainer)
                }
            }

            if (preferences.enableVideos) {
                launch {
                    val videoContainer = getRootVideoContainer()
                    rootContainer.addContainer(videoContainer)
                    result.addContainer(videoContainer)
                }
            }

            launch {
                val pairs = getUserSelectedContainer(rootContainer)

                pairs.forEach { (userSelectedContainer, userSelectedContainers) ->
                    result[userSelectedContainer.rawId.toLong()] = userSelectedContainer
                    rootContainer.addContainer(userSelectedContainer)
                    userSelectedContainers.forEach { container -> result[container.rawId.toLong()] = container }
                }
            }
        }

        containerCache = result
    }

    private fun getRootImagesContainer(): BaseContainer = AllImagesContainer(
        id = IMAGE_ID.toString(),
        parentID = ROOT_ID.toString(),
        title = application.getString(R.string.images),
        creator = appName,
        baseUrl = baseUrl,
        contentResolver = application.contentResolver
    )

    private fun getRootAudioContainer(): BaseContainer = AllAudioContainer(
        id = AUDIO_ID.toString(),
        parentID = ROOT_ID.toString(),
        title = application.getString(R.string.audio),
        creator = appName,
        baseUrl = baseUrl,
        contentResolver = application.contentResolver,
        albumId = null,
        artist = null
    )

    private fun getRootVideoContainer(): BaseContainer = AllVideoContainer(
        VIDEO_ID.toString(),
        parentID = ROOT_ID.toString(),
        title = application.getString(R.string.videos),
        creator = appName,
        baseUrl = baseUrl,
        contentResolver = application.contentResolver
    )

    private suspend fun getUserSelectedContainer(rootContainer: DefaultContainer) = coroutineScope {
        application
            .contentResolver
            .persistedUriPermissions
            .map { urlPermission ->
                async {
                    val displayName = DocumentFile.fromTreeUri(application, urlPermission.uri)?.name ?: ""
                    val documentId = DocumentsContract.getTreeDocumentId(urlPermission.uri)
                    val uri = DocumentsContract.buildChildDocumentsUriUsingTree(urlPermission.uri, documentId)

                    if (uri != null) {
                        queryUri(uri, rootContainer.rawId, displayName)
                    } else {
                        null
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    private suspend fun queryUri(
        uri: Uri,
        parentRawId: String,
        newContainerName: String,
    ): Pair<BaseContainer, List<BaseContainer>> {
        val newContainer = createContainer(randomId, parentId = parentRawId, newContainerName)
        var containers = listOf(newContainer)
        val resolver = application.contentResolver
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri))

        coroutineScope {
            resolver.query(
                childrenUri,
                mediaColumns,
                null,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndex(mediaColumns[0])
                val mimeColumn = cursor.getColumnIndex(mediaColumns[1])
                val displayNameColumn = cursor.getColumnIndex(mediaColumns[2])
                val sizeColumn = cursor.getColumnIndex(mediaColumns[3])
                // Skip artist, we don't use it here for now
                val albumArtistColumn = cursor.getColumnIndex(mediaColumns[5])
                val albumColumn = cursor.getColumnIndex(mediaColumns[6])

                val mutex = Mutex()

                while (cursor.moveToNext()) {
                    val id = idColumn.returnIfExists(cursor::getStringOrNull) ?: continue
                    val newDocumentUri = DocumentsContract.buildDocumentUriUsingTree(uri, id)
                    val mimeType = mimeColumn.returnIfExists(cursor::getStringOrNull) ?: continue
                    val displayName = displayNameColumn.returnIfExists(cursor::getStringOrNull) ?: "Unnamed"
                    val size = sizeColumn.returnIfExists(cursor::getLongOrNull) ?: 0L
                    val albumArtist = albumArtistColumn.returnIfExists(cursor::getStringOrNull)
                    val album = albumArtistColumn.returnIfExists(cursor::getStringOrNull)

                    when {
                        mimeType == DocumentsContract.Document.MIME_TYPE_DIR -> launch {
                            val (queryContainer, queryContainers) = queryUri(
                                newDocumentUri,
                                newContainer.rawId,
                                displayName
                            )

                            newContainer.addContainer(queryContainer)

                            mutex.withLock {
                                containers = containers + queryContainers
                            }
                        }

                        mimeType != DocumentsContract.Document.MIME_TYPE_DIR && mimeType.isNotBlank() -> {
                            addFile(
                                newContainer,
                                newDocumentUri,
                                displayName,
                                mimeType,
                                size,
                                null,
                                album,
                                albumArtist
                            )
                        }
                    }
                }
            }
        }

        return newContainer to containers
    }

    private fun addFile(
        parentContainer: BaseContainer,
        uri: Uri,
        displayName: String,
        mime: String,
        size: Long,
        duration: Long?,
        album: String?,
        creator: String?
    ) {
        val id = randomId
        val itemId = "${TREE_PREFIX}${id}"
        val item = when {
            mime.startsWith("image") -> parentContainer.addImageItem(
                baseUrl = baseUrl,
                id = itemId,
                name = displayName,
                mime = mime,
                width = 0,
                height = 0,
                size = size
            )

            mime.startsWith("audio") -> parentContainer.addAudioItem(
                baseUrl = baseUrl,
                id = itemId,
                name = displayName,
                mime = mime,
                width = 0,
                height = 0,
                size = size,
                duration = duration ?: 0L,
                album = album ?: "",
                creator = creator ?: ""
            )

            mime.startsWith("video") -> parentContainer.addVideoItem(
                baseUrl = baseUrl,
                id = itemId,
                name = displayName,
                mime = mime,
                width = 0,
                height = 0,
                size = size,
                duration = duration ?: 0L
            )

            else -> null
        }

        if (item != null) {
            _contentCache[id] = ContentModel(uri, mime, displayName, item)
        }
    }

    private fun createContainer(
        id: Long,
        parentId: String,
        name: String?,
    ): BaseContainer = DefaultContainer(id.toString(), parentId, "${USER_DEFINED_PREFIX}$name", null)

    companion object {
        const val USER_DEFINED_PREFIX = "USER_DEFINED_"
        const val SEPARATOR = '$'

        // Type
        const val ROOT_ID: Long = 0
        const val IMAGE_ID: Long = 1
        const val AUDIO_ID: Long = 2
        const val VIDEO_ID: Long = 3

        // Prefix item
        const val VIDEO_PREFIX = "v-"
        const val AUDIO_PREFIX = "a-"
        const val IMAGE_PREFIX = "i-"
        const val TREE_PREFIX = "t-"

        private val random = SecureRandom()
        private val randomId
            get() = abs(random.nextLong().coerceIn(Long.MIN_VALUE + 1 until Long.MAX_VALUE))

        private val mediaColumns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            MediaStore.MediaColumns.ARTIST,
            MediaStore.MediaColumns.ALBUM_ARTIST,
            MediaStore.MediaColumns.ALBUM
        )

        private inline fun <T> Int.returnIfExists(block: (Int) -> T): T? {
            if (this == -1)
                return null

            return block(this)
        }
    }
}
