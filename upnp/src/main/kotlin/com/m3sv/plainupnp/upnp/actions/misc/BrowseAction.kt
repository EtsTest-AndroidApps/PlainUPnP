package com.m3sv.plainupnp.upnp.actions.misc

import com.m3sv.plainupnp.upnp.ContentDirectoryService
import com.m3sv.plainupnp.upnp.didl.ClingContainer
import com.m3sv.plainupnp.upnp.didl.ClingDIDLObject
import com.m3sv.plainupnp.upnp.didl.ClingMedia
import com.m3sv.plainupnp.upnp.didl.MiscItem
import org.fourthline.cling.controlpoint.ControlPoint
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.support.contentdirectory.callback.Browse
import org.fourthline.cling.support.model.BrowseFlag
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.item.AudioItem
import org.fourthline.cling.support.model.item.ImageItem
import org.fourthline.cling.support.model.item.VideoItem
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

sealed interface BrowseResult {
    @JvmInline
    value class Success(val contents: List<ClingDIDLObject>) : BrowseResult

    sealed interface Error : BrowseResult {
        object Generic : Error
        object MissingStoragePermission : Error
    }
}

class BrowseAction @Inject constructor(private val controlPoint: ControlPoint) {

    suspend operator fun invoke(
        service: Service<*, *>,
        id: String
    ): BrowseResult = suspendCoroutine { continuation ->
        controlPoint.execute(object : Browse(
            service,
            id,
            BrowseFlag.DIRECT_CHILDREN,
            "*",
            0,
            null
        ) {
            override fun received(actionInvocation: ActionInvocation<*>, didl: DIDLContent) {
                Timber.d("Received browse response")
                continuation.resume(BrowseResult.Success(buildContentList(didl)))
            }

            override fun updateStatus(status: Status) {
                Timber.d("Update browse status $status")
            }

            override fun failure(invocation: ActionInvocation<out Service<*, *>>?, operation: UpnpResponse?) {
                val result = if (isReadPermissionMissing(invocation))
                    BrowseResult.Error.MissingStoragePermission
                else
                    BrowseResult.Error.Generic

                continuation.resume(result)
            }

            private fun isReadPermissionMissing(invocation: ActionInvocation<out Service<*, *>>?): Boolean {
                val exception = invocation?.failure
                return exception?.message?.contains(ContentDirectoryService.READ_PERMISSION_IS_MISSING) == true
            }

            override fun failure(
                invocation: ActionInvocation<out Service<*, *>>?,
                operation: UpnpResponse?,
                defaultMsg: String?
            ) {
                continuation.resume(BrowseResult.Error.Generic)
            }
        })
    }

    private fun buildContentList(content: DIDLContent): List<ClingDIDLObject> = buildList {
        content.containers.forEach { container ->
            add(ClingContainer(container))
        }

        content.items.forEach { item ->
            val clingItem: ClingDIDLObject = when (item) {
                is VideoItem -> ClingMedia.Video(item)
                is AudioItem -> ClingMedia.Audio(item)
                is ImageItem -> ClingMedia.Image(item)
                else -> MiscItem(item)
            }

            add(clingItem)
        }
    }
}
