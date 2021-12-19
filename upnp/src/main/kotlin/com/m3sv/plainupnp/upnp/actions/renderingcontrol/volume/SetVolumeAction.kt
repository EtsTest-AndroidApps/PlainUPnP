package com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume

import com.m3sv.plainupnp.logging.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import org.fourthline.cling.controlpoint.ControlPoint
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.support.renderingcontrol.callback.SetVolume
import javax.inject.Inject
import kotlin.coroutines.resume

class SetVolumeAction @Inject constructor(
    private val controlPoint: ControlPoint,
    private val logger: Logger
) {
    suspend operator fun invoke(service: Service<*, *>, volume: Volume): Volume? =
        suspendCancellableCoroutine { continuation ->
            val action = object : SetVolume(service, volume.value.toLong()) {
                override fun success(invocation: ActionInvocation<out Service<*, *>>?) {
                    super.success(invocation)
                    if (continuation.isActive)
                        continuation.resume(volume)
                }

                override fun failure(
                    invocation: ActionInvocation<out Service<*, *>>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    logger.e("Failed to raise volume")
                    if (continuation.isActive)
                        continuation.resume(null)
                }
            }

            val future = controlPoint.execute(action)
            continuation.invokeOnCancellation { runCatching { future.cancel(true) } }
        }
}
