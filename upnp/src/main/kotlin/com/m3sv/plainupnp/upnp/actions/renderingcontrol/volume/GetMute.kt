package com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume

import com.m3sv.plainupnp.upnp.actions.Action
import kotlinx.coroutines.suspendCancellableCoroutine
import org.fourthline.cling.controlpoint.ControlPoint
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.support.renderingcontrol.callback.GetMute
import javax.inject.Inject
import kotlin.coroutines.resume

class GetMute @Inject constructor(controlPoint: ControlPoint) :
    Action<Unit, Boolean>(controlPoint) {

    override suspend fun invoke(
        service: Service<*, *>,
        vararg arguments: Unit
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val action = object : GetMute(service) {
            override fun received(
                actionInvocation: ActionInvocation<out Service<*, *>>?,
                currentMute: Boolean
            ) {
                if (continuation.isActive)
                    continuation.resume(currentMute)
            }

            override fun failure(
                invocation: ActionInvocation<out Service<*, *>>?,
                operation: UpnpResponse?,
                defaultMsg: String?
            ) {
                if (continuation.isActive)
                    continuation.resume(false)
            }
        }

        val future = controlPoint.execute(action)
        continuation.invokeOnCancellation { runCatching { future.cancel(true) } }
    }
}
