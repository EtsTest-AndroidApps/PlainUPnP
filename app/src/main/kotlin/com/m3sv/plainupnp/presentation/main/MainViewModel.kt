package com.m3sv.plainupnp.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3sv.plainupnp.R
import com.m3sv.plainupnp.common.preferences.PreferencesRepository
import com.m3sv.plainupnp.common.util.pass
import com.m3sv.plainupnp.data.upnp.PlaybackState
import com.m3sv.plainupnp.upnp.UpnpContentRepositoryImpl
import com.m3sv.plainupnp.upnp.actions.renderingcontrol.volume.Volume
import com.m3sv.plainupnp.upnp.folder.Folder
import com.m3sv.plainupnp.upnp.manager.Result
import com.m3sv.plainupnp.upnp.manager.UpnpManager
import com.m3sv.plainupnp.upnp.playback.PlaybackItem
import com.m3sv.plainupnp.upnp.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface VolumeUpdate {
    val volume: Volume

    data class Show(override val volume: Volume) : VolumeUpdate
    data class Hide(override val volume: Volume) : VolumeUpdate
}

@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
    private val upnpManager: UpnpManager,
    private val playbackManager: PlaybackManager,
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
            initialValue = VolumeUpdate.Hide(Volume(-1))
        )

    private val filterText: MutableStateFlow<String> = MutableStateFlow("")
    private val orderBy: MutableStateFlow<ViewState.OrderBy> = MutableStateFlow(ViewState.OrderBy.Default)
    private val order: MutableStateFlow<ViewState.SortOrder> = MutableStateFlow(ViewState.SortOrder.Ascending)

    val navigation: StateFlow<List<Folder>> = upnpManager
        .navigationStack
        .filterNot { it.isEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = listOf()
        )

    val upnpState: StateFlow<PlaybackState> = playbackManager
        .playbackState
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackState.Empty)

    private val itemClicks: MutableSharedFlow<ItemViewModel> = MutableSharedFlow()
    private val isSelectRendererButtonExpanded: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val isSelectRendererDialogExpanded = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            combine(
                upnpManager.renderers.map { renderers ->
                    renderers.map { device ->
                        ViewState.RenderersState.RendererModel(
                            device.identity,
                            device.friendlyName
                        )
                    }
                },
                upnpManager.selectedRenderer
            ) { renderers, selectedRenderer ->
                updateState { previousState ->
                    val selectedRenderer = if (selectedRenderer != null) {
                        ViewState.RenderersState.RendererModel(selectedRenderer.identity, selectedRenderer.friendlyName)
                    } else {
                        null
                    }

                    val newRenderersState = previousState.renderersState.copy(
                        renderers = renderers
                            .filter { it != selectedRenderer }
                            .toList(),
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

        viewModelScope.launch {
            itemClicks.onEach { item ->
                updateState { it.copy(isLoading = true) }
                var playingNowId: String? = null

                when (item.type) {
                    ItemType.CONTAINER -> upnpManager.itemClick(item.id)
                    ItemType.AUDIO,
                    ItemType.VIDEO,
                    ItemType.IMAGE -> with(playbackManager) {
                        stopPlayback()
                        val result = play(PlaybackItem(item.id, item.title))
                        if (result is Result.Success) {
                            playingNowId = item.id
                        }
                    }
                    ItemType.MISC -> pass
                }

                updateState { it.copy(isLoading = false, lastPlayed = playingNowId ?: it.lastPlayed) }
            }.collect()
        }

        viewModelScope.launch {
            combine(
                navigation.filterNot { it.isEmpty() },
                filterText,
                orderBy,
                order
            ) { folders, filterText, orderBy, order ->
                var newContents = folders
                    .last()
                    .folderModel
                    .contents
                    .map { clingObject ->
                        val type = clingObject.toItemType()

                        val title = if (type == ItemType.CONTAINER) {
                            clingObject.title.replace(UpnpContentRepositoryImpl.USER_DEFINED_PREFIX, "")
                        } else {
                            clingObject.title
                        }

                        ItemViewModel(
                            id = clingObject.id,
                            title = title,
                            type = type,
                            uri = clingObject.uri
                        )
                    }
                    .filter { it.title.contains(filterText, ignoreCase = true) }

                newContents = when (orderBy) {
                    ViewState.OrderBy.Alphabetically -> when (order) {
                        ViewState.SortOrder.Ascending -> newContents.sortedBy { it.title.lowercase() }
                        ViewState.SortOrder.Descending -> newContents.sortedByDescending { it.title.lowercase() }
                    }

                    ViewState.OrderBy.Default -> newContents
                }
                val folderContents = FolderContents.Contents(newContents)

                updateState { previousState ->
                    previousState.copy(
                        folderContents = folderContents,
                        filterText = filterText,
                        sortModel = previousState.sortModel.copy(orderBy = orderBy, order = order)
                    )
                }
            }.flowOn(Dispatchers.Default).collect()
        }
    }

    private inline fun updateState(block: (previousState: ViewState) -> ViewState) {
        val previousState = viewState.value
        _viewState.value = block(previousState)
    }

    val finishActivityFlow: Flow<Unit> = upnpManager
        .navigationStack
        .filter { it.isEmpty() }
        .onEach {
            playbackManager.stopPlayback()
            upnpManager.selectRenderer(null)
        }
        .map { }

    val showThumbnails: StateFlow<Boolean> = preferencesRepository
        .preferences
        .map { it.enableThumbnails }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = false
        )

    fun itemClick(item: ItemViewModel) {
        viewModelScope.launch {
            itemClicks.emit(item)
        }
    }

    fun setSelectRendererButtonState(expanded: Boolean) {
        isSelectRendererButtonExpanded.value = expanded
    }

    fun setSelectRendererDialogState(expanded: Boolean) {
        isSelectRendererDialogExpanded.value = expanded
    }

    fun setSettingsDialogState(expanded: Boolean) {
        updateState { it.copy(isSettingsDialogExpanded = expanded) }
    }

    fun moveTo(progress: Int) {
        viewModelScope.launch {
            playbackManager.seekTo(progress)
        }
    }

    fun selectRenderer(identity: String?) {
        viewModelScope.launch {
            setSelectRendererButtonState(expanded = false)
            setSelectRendererDialogState(expanded = false)
            delay(250)
            playbackManager.stopPlayback()
            upnpManager.selectRenderer(identity)
        }
    }

    fun playerButtonClick(button: PlayerButton) {
        viewModelScope.launch {
            when (button) {
                PlayerButton.PLAY -> playbackManager.resumePlayback()
                PlayerButton.PAUSE -> playbackManager.pausePlayback()
                PlayerButton.PREVIOUS -> playbackManager.playPrevious()
                PlayerButton.NEXT -> playbackManager.playNext()
                PlayerButton.RAISE_VOLUME -> volumeManager.raiseVolume()
                PlayerButton.LOWER_VOLUME -> volumeManager.lowerVolume()
                PlayerButton.STOP -> playbackManager.stopPlayback()
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
        viewModelScope.launch { filterText.emit(text) }
    }

    fun clearFilterText() {
        filterInput("")
    }

    fun setSortByDialogVisible(expanded: Boolean) {
        updateState {
            it.copy(sortModel = it.sortModel.copy(isSortByDialogExpanded = expanded))
        }
    }

    fun setOrderBy(value: ViewState.OrderBy) {
        orderBy.value = value
    }

    fun flipAscensionOrder() {
        order.value = when (order.value) {
            ViewState.SortOrder.Ascending -> ViewState.SortOrder.Descending
            ViewState.SortOrder.Descending -> ViewState.SortOrder.Ascending
        }
    }

    fun unselectRenderer() {
        selectRenderer(null)
    }

    data class ViewState(
        val isSettingsDialogExpanded: Boolean,
        val selectRendererState: SelectRendererState,
        val isLoading: Boolean,
        val lastPlayed: String?,
        val renderersState: RenderersState,
        val folderContents: FolderContents,
        val sortModel: SortModel,
        val filterText: String,
    ) {
        data class RenderersState(
            val renderers: List<RendererModel>,
            val selectedRenderer: RendererModel?,
        ) {
            data class RendererModel(val id: String, val title: String)
        }

        data class SelectRendererState(
            val isSelectRendererButtonExpanded: Boolean,
            val isSelectRendererDialogExpanded: Boolean,
        )

        data class SortModel(
            val orderBy: OrderBy,
            val order: SortOrder,
            val isSortByDialogExpanded: Boolean,
        )

        enum class OrderBy(val text: Int) {
            Alphabetically(R.string.sort_by_alphabeticaly),

            // TODO support later
//            Size(R.string.sort_by_size),
//            Date(R.string.sort_by_date),
            Default(R.string.sort_by_default)
        }

        enum class SortOrder {
            Ascending, Descending
        }

        companion object {
            fun empty() = ViewState(
                isSettingsDialogExpanded = false,
                isLoading = false,
                selectRendererState = SelectRendererState(
                    isSelectRendererButtonExpanded = false,
                    isSelectRendererDialogExpanded = false
                ),
                renderersState = RenderersState(listOf(), null),
                lastPlayed = null,
                sortModel = SortModel(
                    orderBy = OrderBy.Default,
                    order = SortOrder.Ascending,
                    isSortByDialogExpanded = false,
                ),
                folderContents = FolderContents.Empty,
                filterText = "",
            )
        }
    }

    companion object {
        private const val HIDE_VOLUME_INDICATOR_DELAY: Long = 2500
    }
}
