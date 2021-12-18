package com.m3sv.plainupnp.server

import com.m3sv.plainupnp.common.ApplicationMode
import com.m3sv.plainupnp.common.preferences.Preferences
import com.m3sv.plainupnp.common.preferences.PreferencesRepository
import com.m3sv.plainupnp.common.util.asApplicationMode
import com.m3sv.plainupnp.interfaces.LifecycleManager
import com.m3sv.plainupnp.logging.Logger
import com.m3sv.plainupnp.upnp.android.AndroidUpnpServiceImpl
import com.m3sv.plainupnp.upnp.localdevice.LocalDeviceProvider
import com.m3sv.plainupnp.upnp.server.MediaServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.fourthline.cling.UpnpService
import org.fourthline.cling.controlpoint.ControlPoint
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerManagerImpl @Inject constructor(
    private val upnpService: UpnpService,
    private val mediaServer: MediaServer,
    private val preferencesRepository: PreferencesRepository,
    private val lifecycleManager: LifecycleManager,
    private val controlPoint: ControlPoint,
    private val logger: Logger,
    private val localDeviceProvider: LocalDeviceProvider
) : ServerManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isServerOn: MutableStateFlow<Boolean> = MutableStateFlow(false)

    init {
        scope.launch {
            lifecycleManager.doOnStart { start() }
        }

        scope.launch {
            lifecycleManager.doOnResume { if (preferencesRepository.isStreaming) resume() }
        }

        scope.launch {
            lifecycleManager.doOnFinish { shutdown() }
        }

        scope.launch {
            preferencesRepository
                .preferences
                .collect { preferences ->
                    when (preferences.applicationMode) {
                        Preferences.ApplicationMode.STREAMING -> resume()
                        else -> pause()
                    }
                }
        }

        scope.launch {
            isServerOn.onEach { isOn ->
                if (isOn) {
                    scope.launch {
                        addLocalDevice()
                        runCatching { mediaServer.start() }.onFailure {
                            logger.e(it, "Failed to start media server!")
                        }
                    }
                } else {
                    scope.launch {
                        removeLocalDevice()
                        runCatching { mediaServer.stop() }.onFailure {
                            logger.e(it, "Failed to stop media server!")
                        }
                    }
                }
            }
                .onEach { controlPoint.search() }
                .collect()
        }

        scope.launch {
            preferencesRepository
                .preferences
                .filterNotNull()
                .map { it.applicationMode.asApplicationMode() }
                .collect { applicationMode ->
                    when (applicationMode) {
                        ApplicationMode.Streaming -> addLocalDevice()
                        ApplicationMode.Player -> removeLocalDevice()
                    }
                }
        }
    }

    override suspend fun start() {
        (upnpService as AndroidUpnpServiceImpl).start()
    }

    override suspend fun resume() {
        isServerOn.value = true
    }

    override suspend fun pause() {
        isServerOn.value = false
    }

    override suspend fun shutdown() {
        upnpService.shutdown()
        mediaServer.stop()
        lifecycleManager.close()
    }

    private fun addLocalDevice() {
        runCatching {
            if (preferencesRepository.isStreaming) {
                upnpService.registry.addDevice(localDeviceProvider.localDevice)
            }
        }.onFailure(logger::e)
    }

    private fun removeLocalDevice() {
        runCatching {
            upnpService.registry.removeDevice(localDeviceProvider.localDevice)
        }.onFailure(logger::e)
    }
}

