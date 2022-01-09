package com.m3sv.plainupnp.upnp.usecase

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.m3sv.plainupnp.logging.Logger
import com.m3sv.plainupnp.upnp.didl.ClingDIDLObject
import com.m3sv.plainupnp.upnp.didl.ClingMedia
import javax.inject.Inject

class LaunchLocallyUseCase @Inject constructor(
    private val application: Application,
    private val logger: Logger
) {
    operator fun invoke(didlItem: ClingDIDLObject) {
        val uri = didlItem.uri
        if (uri != null) {
            val contentType = when (didlItem) {
                is ClingMedia.Audio -> "audio/*"
                is ClingMedia.Image -> "image/*"
                is ClingMedia.Video -> "video/*"
                else -> null
            }

            if (contentType != null) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                        setDataAndType(Uri.parse(uri), contentType)
                        flags += Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (intent.resolveActivity(application.packageManager) != null) {
                        application.startActivity(intent)
                    }
                } catch (e: Exception) {
                    logger.e(e, "Failed to launch locally")
                }
            }
        }
    }

}
