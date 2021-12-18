package com.m3sv.plainupnp.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3sv.plainupnp.common.preferences.PreferencesRepository
import com.m3sv.plainupnp.common.util.pass
import com.m3sv.plainupnp.data.upnp.UpnpDevice
import com.m3sv.plainupnp.data.upnp.UpnpRendererState
import com.m3sv.plainupnp.upnp.folder.Folder
import com.m3sv.plainupnp.upnp.manager.Result
import com.m3sv.plainupnp.upnp.manager.UpnpManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class VolumeUpdate(val volume: Int) {
    class Show(volume: Int) : VolumeUpdate(volume)
    class Hide(volume: Int) : VolumeUpdate(volume)
}

@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    private val upnpManager: UpnpManager,
    private val volumeManager: BufferedVolumeManager,
) : ViewModel() {

    private val _viewState: MutableStateFlow<ViewState> = MutableStateFlow(ViewState.empty())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    val isConnectedToRenderer: Flow<Boolean> = upnpManager.selectedRenderer.map { it != null }

    val volume: StateFlow<VolumeUpdate> = volumeManager
        .volumeFlow
        .map { volume -> VolumeUpdate.Show(volume) }
        .transformLatest { volumeUpdate ->
            emit(volumeUpdate)
            delay(HIDE_VOLUME_INDICATOR_DELAY)
            emit(VolumeUpdate.Hide(volumeUpdate.volume))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = VolumeUpdate.Hide(-1)
        )

    private val _filterText: MutableStateFlow<String> = MutableStateFlow("")

    val filterText: StateFlow<String> = _filterText

    val navigation: StateFlow<List<Folder>> = upnpManager
        .navigationStack
        .filterNot { it.isEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = listOf()
        )

    val folderContents: StateFlow<FolderContents> = combine(
        navigation.filterNot { it.isEmpty() },
        filterText
    ) { folders, filterText ->
        val newContents = folders
            .last()
            .folderModel
            .contents
            .filter { it.title.contains(filterText, ignoreCase = true) }
            .map { clingObject ->
                ItemViewModel(
                    id = clingObject.id,
                    title = clingObject.title,
                    type = clingObject.toItemType(),
                    uri = clingObject.uri
                )
            }

        FolderContents.Contents(newContents)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            FolderContents.Empty
        )

    val upnpState: StateFlow<UpnpRendererState> = upnpManager
        .upnpRendererState
        .stateIn(viewModelScope, SharingStarted.Eagerly, UpnpRendererState.Empty)

    private val isSelectRendererButtonExpanded: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val isSelectRendererDialogExpanded = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            combine(upnpManager.renderers, upnpManager.selectedRenderer) { renderers, selectedRenderer ->
                updateState { previousState ->
                    val newRenderersState = previousState.renderersState.copy(
                        renderers = renderers.toList(),
                        selectedRenderer = selectedRenderer
                    )
                    previousState.copy(renderersState = newRenderersState)
                }
            }.collect()
        }

        viewModelScope.launch {
            combine(
                isSelectRendererButtonExpanded,
                isSelectRendererDialogExpanded
            ) { isButtonExpanded, isDialogExpanded ->
                updateState { previousState ->
                    val newRendererState = previousState.selectRendererState.copy(
                        isSelectRendererButtonExpanded = isButtonExpanded,
                        isSelectRendererDialogExpanded = isDialogExpanded
                    )
                    previousState.copy(selectRendererState = newRendererState)
                }
            }.collect()
        }
    }

    private inline fun updateState(block: (previousState: ViewState) -> ViewState) {
        val previousState = viewState.value
        _viewState.value = block(previousState)
    }

    val finishActivityFlow: Flow<Unit> = upnpManager
        .navigationStack
        .filter { it.isEmpty() }
        .map { }

    val showThumbnails: StateFlow<Boolean> = preferencesRepository
        .preferences
        .map { it.enableThumbnails }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = false
        )

    fun itemClick(id: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }

            when (upnpManager.itemClick(id)) {
                Result.Error.RENDERER_NOT_SELECTED -> expandSelectRendererButton()
                Result.Success,
                Result.Error.AV_SERVICE_NOT_FOUND,
                Result.Error.GENERIC -> pass
            }

            updateState { it.copy(isLoading = false) }
        }
    }

    fun expandSelectRendererButton() {
        isSelectRendererButtonExpanded.value = true
    }

    fun collapseSelectRendererButton() {
        isSelectRendererButtonExpanded.value = false
    }

    fun expandSelectRendererDialog() {
        isSelectRendererDialogExpanded.value = true
    }

    fun collapseSelectRendererDialog() {
        isSelectRendererDialogExpanded.value = false
    }

    fun expandSettingsDialog() {
        updateState { it.copy(isSettingsDialogExpanded = true) }
    }

    fun collapseSettingsDialog() {
        updateState { it.copy(isSettingsDialogExpanded = false) }
    }

    fun moveTo(progress: Int) {
        viewModelScope.launch {
            upnpManager.seekTo(progress)
        }
    }

    fun selectRenderer(device: UpnpDevice) {
        viewModelScope.launch { upnpManager.selectRenderer(device) }
    }

    fun playerButtonClick(button: PlayerButton) {
        viewModelScope.launch {
            when (button) {
                PlayerButton.PLAY -> upnpManager.togglePlayback()
                PlayerButton.PREVIOUS -> upnpManager.playPrevious()
                PlayerButton.NEXT -> upnpManager.playNext()
                PlayerButton.RAISE_VOLUME -> volumeManager.raiseVolume()
                PlayerButton.LOWER_VOLUME -> volumeManager.lowerVolume()
                PlayerButton.STOP -> upnpManager.stopPlayback()
            }
        }
    }

    fun navigateBack() {
        viewModelScope.launch { upnpManager.navigateBack() }
    }

    fun navigateTo(folder: Folder) {
        viewModelScope.launch { upnpManager.navigateTo(folder) }
    }

    fun filterInput(text: String) {
        viewModelScope.launch {
            _filterText.emit(text)
        }
    }

    fun clearFilterText() {
        filterInput("")
    }

    data class ViewState(
        val isSettingsDialogExpanded: Boolean,
        val selectRendererState: SelectRendererState,
        val isLoading: Boolean,
        val renderersState: RenderersState,
    ) {
        data class RenderersState(
            val renderers: List<UpnpDevice>,
            val selectedRenderer: UpnpDevice?,
        )

        data class SelectRendererState(
            val isSelectRendererButtonExpanded: Boolean,
            val isSelectRendererDialogExpanded: Boolean,
        )

        companion object {
            fun empty() = ViewState(
                isSettingsDialogExpanded = false,
                isLoading = false,
                selectRendererState = SelectRendererState(false, false),
                renderersState = RenderersState(listOf(), null)
            )
        }
    }

    companion object {
        private const val HIDE_VOLUME_INDICATOR_DELAY: Long = 2500
    }
}
