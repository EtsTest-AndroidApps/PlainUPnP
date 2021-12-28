package com.m3sv.selectcontentdirectory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3sv.plainupnp.common.util.pass
import com.m3sv.plainupnp.interfaces.LifecycleManager
import com.m3sv.plainupnp.interfaces.manageAppLifecycle
import com.m3sv.plainupnp.logging.Logger
import com.m3sv.plainupnp.upnp.manager.Result
import com.m3sv.plainupnp.upnp.manager.UpnpManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SelectContentDirectoryViewModel @Inject constructor(
    application: Application,
    lifecycleManager: LifecycleManager,
    private val upnpManager: UpnpManager,
    private val logger: Logger
) : ViewModel() {

    private val _viewState: MutableStateFlow<ViewState> = MutableStateFlow(ViewState.empty())
    val viewState = _viewState.asStateFlow()

    init {
        when {
            lifecycleManager.isClosed || lifecycleManager.isFinishing -> pass
            else -> {
                ForegroundNotificationService.start(application)
                lifecycleManager.start()
                lifecycleManager.manageAppLifecycle()
            }
        }

        viewModelScope.launch {
            upnpManager
                .contentDirectories
                .map { devices ->
                    devices.map { device ->
                        ViewState.ContentDirectory(
                            id = device.identity,
                            title = device.friendlyName
                        )
                    }
                }
                .collect { contentDirectories ->
                    updateViewState { previousState ->
                        previousState.copy(contentDirectories = contentDirectories)
                    }
                }
        }
    }


    fun selectContentDirectory(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            updateViewState { previousState -> previousState.copy(loadingDeviceId = id) }

            val navigationResult = when (upnpManager.selectContentDirectory(id)) {
                Result.Error.GENERIC -> ViewState.NavigationResult.Error("Failed to connect to content directory")
                Result.Error.AV_SERVICE_NOT_FOUND -> ViewState.NavigationResult.Error("Failed to find AudioVideo service, try to restart content directory")
                Result.Error.CONTENT_DIRECTORY_NOT_FOUND -> ViewState.NavigationResult.Error("Content directory disappeared")
                Result.Success -> ViewState.NavigationResult.Success
            }

            updateViewState { previousState ->
                previousState.copy(
                    loadingDeviceId = null,
                    navigationResult = navigationResult
                )
            }
        }
    }

    fun consumeNavigationResult() {
        updateViewState { previousState ->
            previousState.copy(navigationResult = null)
        }
    }

    private inline fun updateViewState(block: (previousState: ViewState) -> ViewState) {
        _viewState.value = block(_viewState.value)
    }

    data class ViewState(
        val contentDirectories: List<ContentDirectory>,
        val loadingDeviceId: String?,
        val navigationResult: NavigationResult?
    ) {
        data class ContentDirectory(val id: String, val title: String)

        sealed interface NavigationResult {
            object Success : NavigationResult

            @JvmInline
            value class Error(val message: String) : NavigationResult
        }

        companion object {
            fun empty() = ViewState(
                contentDirectories = listOf(),
                loadingDeviceId = null,
                navigationResult = null
            )
        }
    }
}
