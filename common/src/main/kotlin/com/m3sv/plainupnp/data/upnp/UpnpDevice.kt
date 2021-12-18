package com.m3sv.plainupnp.data.upnp

import org.fourthline.cling.model.meta.Device
import org.fourthline.cling.model.types.UDAServiceType
import timber.log.Timber

data class UpnpDevice(
    val device: Device<*, *, *>,
    val displayString: String = device.displayString,
    val friendlyName: String = device.details?.friendlyName ?: displayString,
    val isFullyHydrated: Boolean = device.isFullyHydrated,
    val identity: String = device.identity.udn.identifierString,
    val fullIdentity: String = "${identity}:${displayString}:${friendlyName}"
) {
    fun printService() {
        device.findServices()?.forEach { service ->
            Timber.i("\t Service : $service")
            for (a in service.actions) {
                Timber.i("\t\t Action : $a")
            }
        }
    }

    fun asService(service: String): Boolean = device.findService(UDAServiceType(service)) != null
}

