package com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume

@JvmInline
value class Volume(val value: Int) {
    infix operator fun plus(otherVolume: Volume): Volume = Volume(value + otherVolume.value)
    infix operator fun minus(otherVolume: Volume): Volume = Volume(value - otherVolume.value)
}
