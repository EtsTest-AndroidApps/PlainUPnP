package com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume

import com.m3sv.plainupnp.logging.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import org.fourthline.cling.controlpoint.ControlPoint
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.support.renderingcontrol.callback.SetMute
import javax.inject.Inject
import kotlin.coroutines.resume

class MuteVolumeAction @Inject constructor(
    private val controlPoint: ControlPoint,
    private val logger: Logger
) {
    suspend operator fun invoke(
        service: Service<*, *>,
        mute: Boolean
    ) = suspendCancellableCoroutine<Boolean> { continuation ->
        val action = object : SetMute(service, mute) {
            override fun failure(
                invocation: ActionInvocation<out Service<*, *>>?,
                operation: UpnpResponse?,
                defaultMsg: String?
            ) {
                logger.e("Failed to mute volume")
                if (continuation.isActive)
                    continuation.resume(false)
            }

            override fun success(invocation: ActionInvocation<out Service<*, *>>?) {
                if (continuation.isActive)
                    continuation.resume(true)
            }
        }

        val future = controlPoint.execute(action)
        continuation.invokeOnCancellation { runCatching { future.cancel(true) } }
    }
}
