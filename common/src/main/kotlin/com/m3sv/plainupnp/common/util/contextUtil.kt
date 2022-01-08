package com.m3sv.plainupnp.common.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

fun Context.isPermissionGranted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        permission
    ) == PackageManager.PERMISSION_GRANTED
}

fun Context.isStoragePermissionGranted(): Boolean {
    return this.isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
}
