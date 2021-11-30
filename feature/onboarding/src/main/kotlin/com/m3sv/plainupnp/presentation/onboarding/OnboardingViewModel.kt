package com.m3sv.plainupnp.presentation.onboarding

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3sv.plainupnp.common.ApplicationMode
import com.m3sv.plainupnp.common.ThemeManager
import com.m3sv.plainupnp.common.ThemeOption
import com.m3sv.plainupnp.common.preferences.PreferencesRepository
import com.m3sv.plainupnp.common.util.asApplicationMode
import com.m3sv.plainupnp.common.util.pass
import com.m3sv.plainupnp.data.upnp.UriWrapper
import com.m3sv.plainupnp.presentation.onboarding.screen.ContainerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class Direction {
    FORWARD, BACKWARD
}

enum class ActivityNotFoundIndicatorState {
    SHOW, DISMISS
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val application: Application,
    private val themeManager: ThemeManager,
    private val preferences: PreferencesRepository,
) : ViewModel() {

    private val _containerState: MutableStateFlow<ContainerState> = MutableStateFlow(ContainerState.empty())
    val containerState: StateFlow<ContainerState> = _containerState

    val contentUris: StateFlow<List<UriWrapper>> = preferences
        .persistedUrisFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, preferences.getUris())

    private val _activityNotFound: MutableStateFlow<ActivityNotFoundIndicatorState> =
        MutableStateFlow(ActivityNotFoundIndicatorState.DISMISS)

    val activityNotFound = _activityNotFound.asStateFlow()

    private val _currentScreen: MutableSharedFlow<Direction> = MutableSharedFlow()

    val currentScreen: StateFlow<OnboardingScreen> =
        _currentScreen.scan(OnboardingScreen.Greeting) { currentScreen, direction ->
            if (direction == Direction.FORWARD) {
                when (currentScreen) {
                    OnboardingScreen.SelectPreconfiguredContainers -> {
                        with(preferences) {
                            val (imagesEnabled, videosEnabled, audioEnabled) = _containerState.value
                            setShareImages(imagesEnabled)
                            setShareVideos(videosEnabled)
                            setShareAudio(audioEnabled)
                        }
                    }
                    else -> pass
                }
            }

            when (direction) {
                Direction.FORWARD -> currentScreen.forward()
                Direction.BACKWARD -> currentScreen.backward()
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, OnboardingScreen.Greeting)

    fun onSelectTheme(themeOption: ThemeOption) {
        themeManager.setTheme(themeOption)
    }

    fun onSelectMode(mode: ApplicationMode) {
        viewModelScope.launch {
            preferences.setApplicationMode(mode)
        }
    }

    fun onNavigateNext() {
        viewModelScope.launch {
            _currentScreen.emit(Direction.FORWARD)
        }
    }

    fun onNavigateBack() {
        viewModelScope.launch {
            _currentScreen.emit(Direction.BACKWARD)
        }
    }

    fun saveUri() {
        preferences.updateUris()
    }

    fun releaseUri(uriWrapper: UriWrapper) {
        preferences.releaseUri(uriWrapper)
    }

    fun onActivityNotFound() {
        viewModelScope.launch {
            _activityNotFound.emit(ActivityNotFoundIndicatorState.SHOW)
        }
    }

    fun dismissActivityNotFound() {
        viewModelScope.launch {
            _activityNotFound.emit(ActivityNotFoundIndicatorState.DISMISS)
        }
    }

    private fun OnboardingScreen.forward(): OnboardingScreen = when (this) {
        OnboardingScreen.Greeting -> OnboardingScreen.SelectTheme
        OnboardingScreen.SelectTheme -> OnboardingScreen.SelectMode
        OnboardingScreen.SelectMode -> when (getApplicationMode()) {
            ApplicationMode.Streaming -> if (hasStoragePermission()) OnboardingScreen.SelectPreconfiguredContainers else OnboardingScreen.StoragePermission
            ApplicationMode.Player -> OnboardingScreen.Finish
        }
        OnboardingScreen.StoragePermission -> OnboardingScreen.SelectPreconfiguredContainers
        OnboardingScreen.SelectPreconfiguredContainers -> OnboardingScreen.SelectDirectories
        OnboardingScreen.SelectDirectories -> OnboardingScreen.Finish
        OnboardingScreen.Finish -> error("Can't navigate from finish screen")
    }

    private fun getApplicationMode(): ApplicationMode =
        requireNotNull(
            preferences
                .preferences
                .value
                .applicationMode
                ?.asApplicationMode()
        )

    private fun OnboardingScreen.backward(): OnboardingScreen = when (this) {
        OnboardingScreen.Greeting -> OnboardingScreen.Greeting
        OnboardingScreen.SelectTheme -> OnboardingScreen.Greeting
        OnboardingScreen.SelectMode -> OnboardingScreen.SelectTheme
        OnboardingScreen.StoragePermission -> OnboardingScreen.SelectMode
        OnboardingScreen.SelectPreconfiguredContainers -> OnboardingScreen.SelectMode
        OnboardingScreen.SelectDirectories -> OnboardingScreen.SelectPreconfiguredContainers
        OnboardingScreen.Finish -> error("Can't navigate from finish screen")
    }

    fun hasStoragePermission(): Boolean = ContextCompat.checkSelfPermission(
        application,
        STORAGE_PERMISSION
    ) == PackageManager.PERMISSION_GRANTED

    fun onImageSwitch() {
        updateContainersState { previousState ->
            previousState.copy(imagesEnabled = !previousState.imagesEnabled)
        }
    }

    fun onVideoSwitch() {
        updateContainersState { previousState ->
            previousState.copy(videoEnabled = !previousState.videoEnabled)
        }
    }

    fun onAudioSwitch() {
        updateContainersState { previousState ->
            previousState.copy(audioEnabled = !previousState.audioEnabled)
        }
    }

    private fun updateContainersState(block: (previousState: ContainerState) -> ContainerState) {
        _containerState.value = block(_containerState.value)
    }

    companion object {
        const val STORAGE_PERMISSION = READ_EXTERNAL_STORAGE
    }
}
