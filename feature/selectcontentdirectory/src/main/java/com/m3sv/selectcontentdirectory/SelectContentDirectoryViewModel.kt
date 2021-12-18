package com.m3sv.selectcontentdirectory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3sv.plainupnp.common.util.pass
import com.m3sv.plainupnp.data.upnp.UpnpDevice
import com.m3sv.plainupnp.interfaces.LifecycleManager
import com.m3sv.plainupnp.interfaces.manageAppLifecycle
import com.m3sv.plainupnp.logging.Logger
import com.m3sv.plainupnp.upnp.manager.Result
import com.m3sv.plainupnp.upnp.manager.UpnpManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.collections.toList

@HiltViewModel
class SelectContentDirectoryViewModel @Inject constructor(
    application: Application,
    lifecycleManager: LifecycleManager,
    private val upnpManager: UpnpManager,
    private val logger: Logger
) : ViewModel() {

    init {
        when {
            lifecycleManager.isClosed || lifecycleManager.isFinishing -> pass
            else -> {
                ForegroundNotificationService.start(application)
                lifecycleManager.start()
                lifecycleManager.manageAppLifecycle()
            }
        }
    }

    val contentDirectories: StateFlow<List<UpnpDevice>> = upnpManager
        .contentDirectories
        .map(Iterable<UpnpDevice>::toList)
        .catch {
            logger.e(it, "Failed to transform content directories")
            emit(listOf())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf())

    suspend fun selectContentDirectory(upnpDevice: UpnpDevice): Result = upnpManager.selectContentDirectory(upnpDevice)
}
