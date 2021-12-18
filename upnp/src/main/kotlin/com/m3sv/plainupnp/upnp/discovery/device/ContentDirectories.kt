package com.m3sv.plainupnp.upnp.discovery.device

import com.m3sv.plainupnp.data.upnp.UpnpDeviceEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class ContentDirectories @Inject constructor(private val contentDirectoryDiscovery: ContentDirectoryDiscovery) {
    operator fun invoke() = callbackFlow {
        contentDirectoryDiscovery.startObserving()

        val callback = object : DeviceDiscoveryObserver {
            override fun addedDevice(event: UpnpDeviceEvent) {
                sendEvent(event)
            }

            override fun removedDevice(event: UpnpDeviceEvent) {
                sendEvent(event)
            }

            private fun sendEvent(event: UpnpDeviceEvent) {
                if (!isClosedForSend) trySend(event)
            }
        }

        contentDirectoryDiscovery.addObserver(callback)
        awaitClose { contentDirectoryDiscovery.removeObserver(callback) }
    }
}
