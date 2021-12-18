package com.m3sv.plainupnp.upnp.discovery.device


import com.m3sv.plainupnp.data.upnp.UpnpDeviceEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class Renderers @Inject constructor(private val rendererDiscovery: RendererDiscovery) {
    operator fun invoke() = callbackFlow {
        rendererDiscovery.startObserving()

        val callback = object : DeviceDiscoveryObserver {
            override fun addedDevice(event: UpnpDeviceEvent) {
                handleEvent(event)
            }

            override fun removedDevice(event: UpnpDeviceEvent) {
                handleEvent(event)
            }

            private fun handleEvent(event: UpnpDeviceEvent) {
                if (!isClosedForSend) trySend(event)
            }
        }

        rendererDiscovery.addObserver(callback)
        awaitClose { rendererDiscovery.removeObserver(callback) }
    }
}
