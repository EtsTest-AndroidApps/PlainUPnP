package com.m3sv.plainupnp.upnp.actions.avtransport

import kotlinx.coroutines.suspendCancellableCoroutine
import org.fourthline.cling.controlpoint.ControlPoint
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.support.avtransport.callback.Seek
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume

class SeekAction @Inject constructor(private val controlPoint: ControlPoint) {

    suspend fun seekTo(
        service: Service<*, *>,
        time: String,
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        val tag = "AV"
        Timber.tag(tag).d("Seek to $time")
        val action = object : Seek(service, time) {
            override fun success(invocation: ActionInvocation<*>?) {
                Timber.tag(tag).v("Seek to $time success")
                if (continuation.isActive)
                    continuation.resume(Unit)
            }

            override fun failure(
                arg0: ActionInvocation<*>,
                arg1: UpnpResponse,
                arg2: String,
            ) {
                Timber.tag(tag).e("Seek to $time failed")
                if (continuation.isActive)
                    continuation.resume(Unit)
            }
        }

        val future = controlPoint.execute(action)
        continuation.invokeOnCancellation { runCatching { future.cancel(true) } }
    }
}
