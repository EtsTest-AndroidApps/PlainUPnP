package com.m3sv.plainupnp.data.upnp


sealed interface UpnpDeviceEvent {
    val device: UpnpDevice

    data class Added(override val device: UpnpDevice) : UpnpDeviceEvent
    data class Removed(override val device: UpnpDevice) : UpnpDeviceEvent
}
