package com.m3sv.plainupnp.upnp.actions.avtransport

import kotlinx.coroutines.suspendCancellableCoroutine
import org.fourthline.cling.controlpoint.ControlPoint
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.support.avtransport.callback.GetTransportInfo
import org.fourthline.cling.support.model.TransportInfo
import javax.inject.Inject
import kotlin.coroutines.resume

class GetTransportInfoAction @Inject constructor(private val controlPoint: ControlPoint) {

    suspend fun getTransportInfo(service: Service<*, *>): TransportInfo? = suspendCancellableCoroutine { continuation ->
        val action = object : GetTransportInfo(service) {
            override fun received(
                invocation: ActionInvocation<out Service<*, *>>?,
                transportInfo: TransportInfo
            ) {
                if (continuation.isActive)
                    continuation.resume(transportInfo)
            }

            override fun failure(
                p0: ActionInvocation<out Service<*, *>>?,
                p1: UpnpResponse?,
                p2: String?
            ) {
                if (continuation.isActive)
                    continuation.resume(null)
            }
        }

        val future = controlPoint.execute(action)
        continuation.invokeOnCancellation { runCatching { future.cancel(true) } }
    }
}

