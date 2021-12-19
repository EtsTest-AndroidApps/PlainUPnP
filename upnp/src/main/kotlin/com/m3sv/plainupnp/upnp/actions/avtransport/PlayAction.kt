package com.m3sv.plainupnp.upnp.actions.avtransport

import kotlinx.coroutines.suspendCancellableCoroutine
import org.fourthline.cling.controlpoint.ControlPoint
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.support.avtransport.callback.Play
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume

class PlayAction @Inject constructor(private val controlPoint: ControlPoint) {

    suspend fun play(service: Service<*, *>): Boolean = suspendCancellableCoroutine { continuation ->
        val tag = "AV"
        Timber.tag(tag).d("Play called")

        val action = object : Play(service) {
            override fun success(invocation: ActionInvocation<out Service<*, *>>?) {
                Timber.tag(tag).d("Play success")
                if (continuation.isActive)
                    continuation.resume(true)
            }

            override fun failure(
                p0: ActionInvocation<out Service<*, *>>?,
                p1: UpnpResponse?,
                p2: String?,
            ) {
                Timber.tag(tag).d("Play failed")
                if (continuation.isActive)
                    continuation.resume(false)
            }
        }

        val future = controlPoint.execute(action)
        continuation.invokeOnCancellation { runCatching { future.cancel(true) } }
    }
}
