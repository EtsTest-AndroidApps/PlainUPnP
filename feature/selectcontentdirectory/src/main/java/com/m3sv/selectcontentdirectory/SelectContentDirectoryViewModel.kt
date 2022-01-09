package com.m3sv.selectcontentdirectory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3sv.plainupnp.common.util.pass
import com.m3sv.plainupnp.interfaces.LifecycleManager
import com.m3sv.plainupnp.interfaces.manageAppLifecycle
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
                Result.Error.Generic -> ViewState.NavigationResult.Error(R.string.content_directory_generic_error)
                Result.Error.AvServiceNotFound -> ViewState.NavigationResult.Error(R.string.content_directory_could_not_find_av_service)
                Result.Error.ContentDirectoryNotFound -> ViewState.NavigationResult.Error(R.string.content_directory_not_found)
                Result.Error.MissingStoragePermission -> ViewState.NavigationResult.MissingPermission
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

            object MissingPermission : NavigationResult

            @JvmInline
            value class Error(val message: Int) : NavigationResult
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
