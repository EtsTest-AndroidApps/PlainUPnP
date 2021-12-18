package com.m3sv.plainupnp.upnp.localdevice

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.m3sv.plainupnp.common.preferences.PreferencesRepository
import com.m3sv.plainupnp.logging.Logger
import com.m3sv.plainupnp.upnp.ContentDirectoryService
import com.m3sv.plainupnp.upnp.R
import com.m3sv.plainupnp.upnp.UpnpContentRepositoryImpl
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder
import org.fourthline.cling.model.DefaultServiceManager
import org.fourthline.cling.model.meta.*
import org.fourthline.cling.model.types.UDADeviceType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDeviceProvider @Inject constructor(
    private val application: Application,
    private val logger: Logger,
    private val preferencesRepository: PreferencesRepository,
    private val contentRepository: UpnpContentRepositoryImpl
) {
    private val appName: String = application.getString(R.string.app_name)
    private val appUrl: String = application.getString(R.string.app_url)
    private val settingContentDirectoryName: String = android.os.Build.MODEL

    // TODO have different model number for different flavors
    private val modelNumber = application.getString(R.string.model_number)
    private val appVersion: String
        get() {
            var result = "1.0"
            try {
                result = application.packageManager.getPackageInfo(application.packageName, 0).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                logger.e("Couldn't find application version")
            }
            return result
        }

    val localDevice by lazy { getLocalDevice(application) }

    private fun getLocalDevice(context: Context): LocalDevice {
        val details = DeviceDetails(
            settingContentDirectoryName,
            ManufacturerDetails(
                appName,
                appUrl
            ),
            ModelDetails(
                appName,
                appUrl,
                modelNumber,
                appUrl
            ),
            appVersion,
            appVersion
        )

        val validationErrors = details.validate()

        for (error in validationErrors) {
            logger.e("Validation pb for property ${error.propertyName}, error is ${error.message}")
        }

        val type = UDADeviceType("MediaServer", 1)

        val iconInputStream = context.resources.openRawResource(R.raw.ic_launcher_round)

        val icon = Icon(
            "image/png",
            128,
            128,
            32,
            "plainupnp-icon",
            iconInputStream
        )

        return LocalDevice(
            DeviceIdentity(preferencesRepository.getUdn()),
            type,
            details,
            icon,
            getLocalService(contentRepository)
        )
    }

    private fun getLocalService(contentRepository: UpnpContentRepositoryImpl): LocalService<ContentDirectoryService> {
        val serviceBinder = AnnotationLocalServiceBinder()
        val contentDirectoryService =
            serviceBinder.read(ContentDirectoryService::class.java) as LocalService<ContentDirectoryService>

        contentDirectoryService.manager = DefaultServiceManager(
            contentDirectoryService,
            ContentDirectoryService::class.java
        ).also { serviceManager ->
            (serviceManager.implementation as ContentDirectoryService).let { service ->
                service.contentRepository = contentRepository
                service.logger = logger
            }
        }

        return contentDirectoryService
    }
}
