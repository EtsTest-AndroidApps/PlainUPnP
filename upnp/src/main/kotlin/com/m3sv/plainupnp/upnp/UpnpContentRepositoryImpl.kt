package com.m3sv.plainupnp.upnp

import android.app.Application
import android.content.ContentResolver
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
import com.m3sv.plainupnp.upnp.mediacontainers.*
import com.m3sv.plainupnp.upnp.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import java.io.File
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
    private val allCache: MutableMap<Long, ContentModel> = mutableMapOf()
    override val contentCache: Map<Long, ContentModel> = allCache

    private val scope = CoroutineScope(Dispatchers.IO)

    private val appName by lazy { application.getString(R.string.app_name) }
    private val localIpAddress by lazy { getLocalIpAddress(application, logger).hostAddress }
    private val baseUrl: String by lazy { "$localIpAddress:$PORT" }

    private val _refreshState: MutableStateFlow<ContentUpdateState> =
        MutableStateFlow(ContentUpdateState.Ready)

    val refreshState: Flow<ContentUpdateState> = _refreshState

    init {
        scope.launch {
            preferencesRepository
                .updateFlow
                .debounce(2000)
                .collect { refreshContent() }
        }
    }

    override fun refreshContent() {
        scope.launch {
            _refreshState.value = ContentUpdateState.Loading
            refreshInternal()
            _refreshState.value = ContentUpdateState.Ready
        }
    }

    private val init by lazy {
        runBlocking {
            refreshInternal()
        }
    }

    override fun init() {
        init
    }

    private suspend fun refreshInternal() {
        val result: MutableMap<Long, BaseContainer> = mutableMapOf()
        val contentResolver = application.contentResolver

        val rootContainer = DefaultContainer(
            ROOT_ID.toString(),
            ROOT_ID.toString(),
            appName,
            appName
        ).also { container -> result[container.rawId.toLong()] = container }

        val preferences = preferencesRepository.preferences.value

        coroutineScope {
            if (preferences.enableImages) {
                launch {
                    val (imageContainer, containers) = getRootImagesContainer(contentResolver)
                    rootContainer.addContainer(imageContainer)
                    containers.forEach { container -> result[container.rawId.toLong()] = container }
                }
            }

            if (preferences.enableAudio) {
                launch {
                    val (audioContainer, containers) = getRootAudioContainer(contentResolver)
                    rootContainer.addContainer(audioContainer)
                    containers.forEach { container -> result[container.rawId.toLong()] = container }
                }
            }

            if (preferences.enableVideos) {
                launch {
                    val (videoContainer, containers) = getRootVideoContainer(contentResolver)
                    rootContainer.addContainer(videoContainer)
                    containers.forEach { container -> result[container.rawId.toLong()] = container }
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

    fun getAudioContainerForAlbum(
        albumId: String,
        parentId: String,
    ): BaseContainer = AllAudioContainer(
        id = albumId,
        parentID = parentId,
        title = "",
        creator = appName,
        baseUrl = baseUrl,
        contentResolver = application.contentResolver,
        albumId = albumId,
        artist = null
    )

    fun getAlbumContainerForArtist(
        artistId: String,
        parentId: String,
    ): AlbumContainer = AlbumContainer(
        id = artistId,
        parentID = parentId,
        title = "",
        creator = appName,
        logger = logger,
        baseUrl = baseUrl,
        contentResolver = application.contentResolver,
        artistId = artistId
    )

    private fun getRootImagesContainer(contentResolver: ContentResolver): Pair<DefaultContainer, List<BaseContainer>> {
        val rootImageContainer = DefaultContainer(
            IMAGE_ID.toString(),
            ROOT_ID.toString(),
            application.getString(R.string.images),
            appName
        )

        val containers = mutableListOf<BaseContainer>(rootImageContainer)

        val allImagesContainer = AllImagesContainer(
            id = ALL_IMAGE.toString(),
            parentID = IMAGE_ID.toString(),
            title = application.getString(R.string.all),
            creator = appName,
            baseUrl = baseUrl,
            contentResolver = application.contentResolver
        )
        containers.add(allImagesContainer)
        rootImageContainer.addContainer(allImagesContainer)

        val byFolderContainer = DefaultContainer(
            IMAGE_BY_FOLDER.toString(),
            rootImageContainer.id,
            application.getString(R.string.by_folder),
            appName
        ).also { byFolderContainer ->
            val column = ImageDirectoryContainer.IMAGE_DATA_PATH
            val externalContentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            generateContainerStructure(
                column = column,
                parentContainer = byFolderContainer,
                externalContentUri = externalContentUri,
                contentResolver = contentResolver,
                childContainerBuilder = { id, parentID, title, creator, baseUrl, contentDirectory ->
                    ImageDirectoryContainer(
                        id = id,
                        parentID = parentID,
                        title = title,
                        creator = creator,
                        baseUrl = baseUrl,
                        directory = contentDirectory,
                        contentResolver = contentResolver
                    )
                }, addToRegistry = containers::add
            )
        }

        containers.add(byFolderContainer)
        rootImageContainer.addContainer(byFolderContainer)

        return rootImageContainer to containers
    }

    private fun getRootAudioContainer(contentResolver: ContentResolver): Pair<DefaultContainer, List<BaseContainer>> {
        val rootAudioContainer = DefaultContainer(
            AUDIO_ID.toString(),
            ROOT_ID.toString(),
            application.getString(R.string.audio),
            appName
        )

        val containers = mutableListOf<BaseContainer>(rootAudioContainer)

        val allAudioContainer = AllAudioContainer(
            ALL_AUDIO.toString(),
            AUDIO_ID.toString(),
            application.getString(R.string.all),
            appName,
            baseUrl = baseUrl,
            contentResolver = application.contentResolver,
            albumId = null,
            artist = null
        )

        rootAudioContainer.addContainer(allAudioContainer)
        containers.add(allAudioContainer)

        val artistContainer = ArtistContainer(
            ALL_ARTISTS.toString(),
            AUDIO_ID.toString(),
            application.getString(R.string.artist),
            appName,
            logger,
            baseUrl,
            application.contentResolver
        )

        rootAudioContainer.addContainer(artistContainer)
        containers.add(artistContainer)

        val albumContainer = AlbumContainer(
            id = ALL_ALBUMS.toString(),
            parentID = AUDIO_ID.toString(),
            title = application.getString(R.string.album),
            creator = appName,
            logger = logger,
            baseUrl = baseUrl,
            contentResolver = application.contentResolver,
            artistId = null
        )

        rootAudioContainer.addContainer(albumContainer)
        containers.add(albumContainer)

        val byFolderContainer = DefaultContainer(
            AUDIO_BY_FOLDER.toString(),
            rootAudioContainer.id,
            application.getString(R.string.by_folder),
            appName
        ).also { container ->
            val column = AudioDirectoryContainer.AUDIO_DATA_PATH
            val externalContentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            generateContainerStructure(
                contentResolver,
                column,
                container,
                externalContentUri,
                childContainerBuilder = { id, parentID, title, creator, baseUrl, contentDirectory ->
                    AudioDirectoryContainer(
                        id = id,
                        parentID = parentID,
                        title = title,
                        creator = creator,
                        baseUrl = baseUrl,
                        directory = contentDirectory,
                        contentResolver = contentResolver
                    )
                },
                addToRegistry = containers::add
            )
        }

        rootAudioContainer.addContainer(byFolderContainer)
        containers.add(byFolderContainer)
        return rootAudioContainer to containers
    }

    private fun getRootVideoContainer(contentResolver: ContentResolver): Pair<BaseContainer, List<BaseContainer>> {
        val rootVideoContainer = DefaultContainer(
            VIDEO_ID.toString(),
            ROOT_ID.toString(),
            application.getString(R.string.videos),
            appName
        )

        val containers = mutableListOf<BaseContainer>(rootVideoContainer)
        val allVideoContainer = AllVideoContainer(
            ALL_VIDEO.toString(),
            VIDEO_ID.toString(),
            application.getString(R.string.all),
            appName,
            baseUrl,
            contentResolver = application.contentResolver
        )

        rootVideoContainer.addContainer(allVideoContainer)
        containers.add(allVideoContainer)

        val videoByFolder = DefaultContainer(
            VIDEO_BY_FOLDER.toString(),
            rootVideoContainer.id,
            application.getString(R.string.by_folder),
            appName
        ).also { container ->
            val column = VideoDirectoryContainer.VIDEO_DATA_PATH
            val externalContentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

            generateContainerStructure(
                contentResolver,
                column,
                container,
                externalContentUri,
                childContainerBuilder = { id, parentID, title, creator, baseUrl, contentDirectory ->
                    VideoDirectoryContainer(
                        id = id,
                        parentID = parentID,
                        title = title,
                        creator = creator,
                        baseUrl = baseUrl,
                        directory = contentDirectory,
                        contentResolver = contentResolver
                    )
                },
                addToRegistry = containers::add
            )
        }

        rootVideoContainer.addContainer(videoByFolder)
        containers.add(videoByFolder)
        return rootVideoContainer to containers
    }

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
    ): Pair<BaseContainer, List<BaseContainer>> = coroutineScope {
        val newContainer = createContainer(randomId, parentId = parentRawId, newContainerName)
        val containers = mutableListOf(newContainer)
        val resolver = application.contentResolver
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri))

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
                        containers.addAll(queryContainers)
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

        newContainer to containers
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
            allCache[id] = ContentModel(uri, mime, displayName, item)
        }
    }

    private fun createContainer(
        id: Long,
        parentId: String,
        name: String?,
    ): BaseContainer = DefaultContainer(id.toString(), parentId, "${USER_DEFINED_PREFIX}$name", null)

    private fun splitBySeparator(value: String): List<String> = value.split(File.separator)

    private fun generateContainerStructure(
        contentResolver: ContentResolver,
        column: String,
        parentContainer: BaseContainer,
        externalContentUri: Uri,
        childContainerBuilder: (
            id: String,
            parentID: String?,
            title: String,
            creator: String,
            baseUrl: String,
            contentDirectory: ContentDirectory,
        ) -> BaseContainer,
        addToRegistry: (BaseContainer) -> Unit
    ) {
        val folders: MutableMap<String, Map<String, Any>> = mutableMapOf()
        buildContentUriSet(contentResolver, externalContentUri, column)
            .map(::splitBySeparator)
            .map { paths -> paths.dropLast(1) }
            .forEach { paths ->
                var map: MutableMap<String, Map<String, Any>>

                paths.first().let { first ->
                    if (folders[first] == null)
                        folders[first] = mutableMapOf<String, Map<String, Any>>()
                    map = folders[first] as MutableMap<String, Map<String, Any>>
                }

                paths.drop(1).forEach { path ->
                    if (map[path] == null)
                        map[path] = mutableMapOf<String, Map<String, Any>>()

                    map = map[path] as MutableMap<String, Map<String, Any>>
                }
            }

        fun populateFromMap(rootContainer: BaseContainer, parentPath: String?, map: Map<String, Map<String, Any>>) {
            map.forEach { (key, value) ->
                val contentDirectoryPath = if (parentPath != null) "$parentPath/$key" else key
                val childContainer = childContainerBuilder(
                    randomId.toString(),
                    rootContainer.rawId,
                    key,
                    appName,
                    baseUrl,
                    ContentDirectory(contentDirectoryPath)
                ).apply(addToRegistry)

                rootContainer.addContainer(childContainer)
                populateFromMap(childContainer, contentDirectoryPath, value as Map<String, Map<String, Any>>)
            }
        }

        populateFromMap(parentContainer, null, folders)
    }

    private fun buildContentUriSet(
        contentResolver: ContentResolver,
        externalContentUri: Uri,
        column: String
    ): Set<String> = buildSet {
        contentResolver.query(
            externalContentUri,
            arrayOf(column),
            null,
            null,
            null
        )?.use { cursor ->
            val pathColumn = cursor.getColumnIndex(column)

            while (cursor.moveToNext()) {
                pathColumn
                    .returnIfExists(cursor::getString)
                    ?.let { path ->
                        when {
                            path.startsWith(File.separator) -> path.drop(1)
                            path.endsWith(File.separator) -> path.dropLast(1)
                            else -> path
                        }
                    }
                    ?.also(::add)
            }
        }
    }

    companion object {
        const val USER_DEFINED_PREFIX = "USER_DEFINED_"
        const val SEPARATOR = '$'

        // Type
        const val ROOT_ID: Long = 0
        const val IMAGE_ID: Long = 1
        const val AUDIO_ID: Long = 2
        const val VIDEO_ID: Long = 3

        // Type subfolder
        const val ALL_IMAGE: Long = 10
        const val ALL_VIDEO: Long = 20
        const val ALL_AUDIO: Long = 30

        const val IMAGE_BY_FOLDER: Long = 100
        const val VIDEO_BY_FOLDER: Long = 200
        const val AUDIO_BY_FOLDER: Long = 300

        const val ALL_ARTISTS: Long = 301
        const val ALL_ALBUMS: Long = 302

        // Prefix item
        const val VIDEO_PREFIX = "v-"
        const val AUDIO_PREFIX = "a-"
        const val IMAGE_PREFIX = "i-"
        const val TREE_PREFIX = "t-"

        private val random = SecureRandom()
        private val randomId
            get() = abs(random.nextLong())

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
