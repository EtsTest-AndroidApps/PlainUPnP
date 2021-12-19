package com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume

import com.m3sv.plainupnp.upnp.actions.Action
import kotlinx.coroutines.suspendCancellableCoroutine
import org.fourthline.cling.controlpoint.ControlPoint
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.support.renderingcontrol.callback.GetVolume
import javax.inject.Inject
import kotlin.coroutines.resume

class GetVolumeAction @Inject constructor(controlPoint: ControlPoint) :
    Action<Unit, Volume?>(controlPoint) {

    override suspend fun invoke(
        service: Service<*, *>,
        vararg arguments: Unit
    ): Volume? = suspendCancellableCoroutine { continuation ->
        val action = object : GetVolume(service) {
            override fun received(
                actionInvocation: ActionInvocation<out Service<*, *>>?,
                currentVolume: Int
            ) {
                if (continuation.isActive)
                    continuation.resume(Volume(currentVolume))
            }

            override fun failure(
                invocation: ActionInvocation<out Service<*, *>>?,
                operation: UpnpResponse?,
                defaultMsg: String?
            ) {
                if (continuation.isActive)
                    continuation.resume(null)
            }

        }

        val future = controlPoint.execute(action)
        continuation.invokeOnCancellation { runCatching { future.cancel(true) } }
    }
}
