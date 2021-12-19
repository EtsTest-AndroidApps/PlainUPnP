package com.m3sv.plainupnp.upnp.actions.avtransport

import kotlinx.coroutines.suspendCancellableCoroutine
import org.fourthline.cling.controlpoint.ControlPoint
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.support.avtransport.callback.Stop
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume

class StopAction @Inject constructor(private val controlPoint: ControlPoint) {

    suspend fun stop(service: Service<*, *>): Boolean = suspendCancellableCoroutine { continuation ->
        val tag = "AV"
        Timber.tag(tag).d("Stop called")
        val action = object : Stop(service) {
            override fun success(invocation: ActionInvocation<out Service<*, *>>?) {
                Timber.tag(tag).d("Stop success")
                if (continuation.isActive)
                    continuation.resume(true)
            }

            override fun failure(
                p0: ActionInvocation<out Service<*, *>>?,
                p1: UpnpResponse?,
                p2: String?,
            ) {
                Timber.tag(tag).e("Stop failed")
                if (continuation.isActive)
                    continuation.resume(false)
            }
        }

        val future = controlPoint.execute(action)
        continuation.invokeOnCancellation { runCatching { future.cancel(true) } }
    }
}
