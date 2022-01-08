package com.m3sv.plainupnp.upnp

import com.m3sv.plainupnp.logging.Logger
import com.m3sv.plainupnp.upnp.UpnpContentRepositoryImpl.Companion.SEPARATOR
import com.m3sv.plainupnp.upnp.mediacontainers.BaseContainer
import org.fourthline.cling.support.contentdirectory.AbstractContentDirectoryService
import org.fourthline.cling.support.contentdirectory.ContentDirectoryErrorCode
import org.fourthline.cling.support.contentdirectory.ContentDirectoryException
import org.fourthline.cling.support.contentdirectory.DIDLParser
import org.fourthline.cling.support.model.BrowseFlag
import org.fourthline.cling.support.model.BrowseResult
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.SortCriterion
import timber.log.Timber

class ContentDirectoryService : AbstractContentDirectoryService() {
    lateinit var contentRepository: UpnpContentRepositoryImpl
    lateinit var logger: Logger

    override fun browse(
        objectID: String,
        browseFlag: BrowseFlag,
        filter: String,
        firstResult: Long,
        maxResults: Long,
        orderby: Array<SortCriterion>,
    ): BrowseResult {
        try {
            val initialized = contentRepository.init()

            if (initialized.not()) {
                throw ContentDirectoryException(
                    ContentDirectoryErrorCode.CANNOT_PROCESS,
                    READ_PERMISSION_IS_MISSING
                )
            }

            val url = objectID
                .split(SEPARATOR)
                .map(String::toLong)
                .takeIf { it.isNotEmpty() }
                ?: throw ContentDirectoryException(
                    ContentDirectoryErrorCode.CANNOT_PROCESS,
                    "Invalid type!"
                )

            val end = url.last()

            Timber.d("Browsing type $objectID")

            val container: BaseContainer? = contentRepository.containerCache[end]

            return getBrowseResult(container ?: throw noSuchObject)
        } catch (ex: Exception) {
            logger.e(ex, "Couldn't browse $objectID")
            throw ContentDirectoryException(
                ContentDirectoryErrorCode.CANNOT_PROCESS,
                ex.toString()
            )
        }
    }

    private fun getBrowseResult(container: BaseContainer): BrowseResult {
        val didl = DIDLContent().apply {
            listOf(
                LinkedHashSet(container.containers),
                LinkedHashSet(container.items)
            ).flatten().forEach { addObject(it) }
        }

        val count = didl.count
        val answer: String

        try {
            answer = DIDLParser().generate(didl)
        } catch (ex: Exception) {
            logger.e(ex, "getBrowseResult failed")
            throw ContentDirectoryException(ContentDirectoryErrorCode.CANNOT_PROCESS, ex.toString())
        }

        return BrowseResult(answer, count, count)
    }

    companion object {
        private val noSuchObject
            get() = ContentDirectoryException(ContentDirectoryErrorCode.NO_SUCH_OBJECT)

        const val READ_PERMISSION_IS_MISSING = "READ_EXTERNAL_STORAGE permission is missing!"
    }
}
