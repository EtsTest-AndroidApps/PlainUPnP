package com.m3sv.plainupnp.upnp.discovery.device


import android.app.Application
import com.m3sv.plainupnp.common.R
import com.m3sv.plainupnp.data.upnp.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject

class RendererDiscoveryObservable @Inject constructor(
    application: Application,
    private val rendererDiscovery: RendererDiscovery,
) {
    private val localDevice = LocalDevice(application.getString(R.string.play_locally))

    private val _selectedRenderer: MutableStateFlow<UpnpDevice?> = MutableStateFlow(localDevice)

    private val renderers =
        LinkedHashSet<DeviceDisplay>(listOf(DeviceDisplay(localDevice)))

    val currentRenderers: Set<DeviceDisplay>
        get() = renderers

    val selectedRenderer: StateFlow<UpnpDevice?> = _selectedRenderer

    val isConnectedToRenderer: Boolean
        get() = selectedRenderer.value != null

    fun selectRenderer(upnpDevice: UpnpDevice?) {
        _selectedRenderer.value = upnpDevice
    }

    operator fun invoke() = callbackFlow {
        rendererDiscovery.startObserving()

        val callback = object : DeviceDiscoveryObserver {
            override fun addedDevice(event: UpnpDeviceEvent) {
                handleEvent(event)
                sendRenderers()
            }

            override fun removedDevice(event: UpnpDeviceEvent) {
                handleEvent(event)
                sendRenderers()
            }

            private fun handleEvent(event: UpnpDeviceEvent) {
                when (event) {
                    is UpnpDeviceEvent.Added -> {
                        Timber.d("Renderer added: ${event.upnpDevice.displayString}")
                        renderers += DeviceDisplay(
                            event.upnpDevice,
                            false,
                            DeviceType.RENDERER
                        )
                    }

                    is UpnpDeviceEvent.Removed -> {
                        Timber.d("Renderer removed: ${event.upnpDevice.displayString}")
                        val device = DeviceDisplay(
                            event.upnpDevice,
                            false,
                            DeviceType.RENDERER
                        )

                        if (renderers.contains(device))
                            renderers -= device

                        if (event.upnpDevice == selectedRenderer.value)
                            _selectedRenderer.value = null
                    }
                }
            }

            private fun sendRenderers() {
                if (!isClosedForSend) trySendBlocking(currentRenderers)
            }
        }

        rendererDiscovery.addObserver(callback)
        send(currentRenderers)
        awaitClose { rendererDiscovery.removeObserver(callback) }
    }
}
