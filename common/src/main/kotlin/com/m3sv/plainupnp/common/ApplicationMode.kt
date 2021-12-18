package com.m3sv.plainupnp.common

import androidx.annotation.StringRes

enum class ApplicationMode(@StringRes val stringValue: Int) {
    Streaming(R.string.application_mode_streaming),
    Player(R.string.application_mode_player)
}
