package com.m3sv.plainupnp.presentation.main

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.glide.rememberGlidePainter
import com.m3sv.plainupnp.R
import com.m3sv.plainupnp.common.ThemeManager
import com.m3sv.plainupnp.common.util.finishApp
import com.m3sv.plainupnp.compose.AppTheme
import com.m3sv.plainupnp.compose.LifecycleIndicator
import com.m3sv.plainupnp.compose.OneToolbar
import com.m3sv.plainupnp.compose.util.isDarkTheme
import com.m3sv.plainupnp.data.upnp.PlaybackState
import com.m3sv.plainupnp.interfaces.LifecycleManager
import com.m3sv.plainupnp.presentation.main.MainViewModel.ViewState
import com.m3sv.plainupnp.presentation.settings.SettingsActivity
import com.m3sv.plainupnp.upnp.folder.Folder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.fourthline.cling.support.model.TransportState
import javax.inject.Inject

typealias ModifierComposableFactory = @Composable (Modifier) -> Unit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeManager: ThemeManager

    @Inject
    lateinit var lifecycleManager: LifecycleManager

    private val viewModel: MainViewModel by viewModels()

    private var isConnectedToRenderer: Boolean = false

    private val progressIndicatorSize = 4.dp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewState: ViewState by viewModel.viewState.collectAsState()
            var showFilter by rememberSaveable { mutableStateOf(false) }
            val volume by viewModel.volume.collectAsState()
            val navigationBarState: List<Folder> by viewModel.navigation.collectAsState()
            val upnpState by viewModel.upnpState.collectAsState()
            val showThumbnails by viewModel.showThumbnails.collectAsState()
            val currentTheme by themeManager.theme.collectAsState()
            val showControls = upnpState !is PlaybackState.Empty
            val configuration = LocalConfiguration.current

            val floatingActionButton: @Composable (Modifier) -> Unit = { modifier ->
                RendererFloatingActionButton(
                    selectRendererState = viewState.selectRendererState,
                    renderers = viewState.renderersState.renderers,
                    selectedItem = viewState.renderersState.selectedRenderer,
                    onDismissDialog = {
                        viewModel.setSelectRendererButtonState(expanded = false)
                        viewModel.setSelectRendererDialogState(expanded = false)
                    },
                    onExpandButton = { viewModel.setSelectRendererButtonState(expanded = true) },
                    onExpandDialog = { viewModel.setSelectRendererDialogState(expanded = true) },
                    onCancelClick = viewModel::unselectRenderer,
                    modifier = modifier
                )
            }

            val filter: @Composable () -> Unit = {
                AnimatedVisibility(visible = showFilter) {
                    Filter(
                        initialValue = viewState.filterText,
                        onValueChange = { viewModel.filterInput(it) },
                    ) {
                        showFilter = false
                        viewModel.clearFilterText()
                    }
                }
            }

            val folderContents: ModifierComposableFactory = { modifier ->
                Folders(
                    contents = viewState.folderContents,
                    showThumbnails = showThumbnails,
                    selectedId = viewState.lastPlayed,
                    modifier = modifier
                )
            }

            val navigationBar: ModifierComposableFactory = { modifier ->
                NavigationBar(
                    folders = navigationBarState,
                    modifier = modifier
                )
            }

            val settings: @Composable RowScope.() -> Unit = {
                SettingsMenu(
                    isExpanded = viewState.isSettingsDialogExpanded,
                    onExpandDialog = { viewModel.setSettingsDialogState(expanded = true) },
                    onDismissDialog = { viewModel.setSettingsDialogState(expanded = false) },
                    onSettingsClick = { openSettings() },
                    onFilterClick = {
                        showFilter = !showFilter

                        if (!showFilter) {
                            viewModel.clearFilterText()
                        }
                    })
            }

            val loadingIndicator: @Composable () -> Unit = {
                LoadingIndicator(viewState.isLoading)
            }

            val sortingRow: @Composable (Modifier) -> Unit = { modifier ->
                SortingRow(
                    viewState.sortModel,
                    modifier,
                    onShowDialog = { viewModel.setSortByDialogVisible(true) },
                    onDismissDialog = { viewModel.setSortByDialogVisible(false) },
                    onChangeAscensionOrder = {
                        viewModel.flipAscensionOrder()
                    },
                    onOrderByOptionClick = { option ->
                        viewModel.setOrderBy(option)
                    }
                )
            }

            AppTheme(currentTheme.isDarkTheme()) {
                Scaffold(topBar = {
                    Box {
                        OneToolbar(onBackClick = { onBackPressed() }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                    navigationBar(Modifier.weight(1f))
                                    sortingRow(Modifier)
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                                settings()
                            }
                        }
                    }
                }) {
                    Box {
                        when (configuration.orientation) {
                            Configuration.ORIENTATION_LANDSCAPE -> {
                                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
                                Landscape(
                                    upnpState = upnpState,
                                    showControls = showControls,
                                    floatingActionButton = floatingActionButton,
                                    filter = filter,
                                    folderContents = folderContents,
                                    loadingIndicator = loadingIndicator,
                                )
                            }
                            else -> {
                                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                                Portrait(
                                    upnpState = upnpState,
                                    folderContents = folderContents,
                                    loadingIndicator = loadingIndicator,
                                    showControls = showControls,
                                    floatingActionButton = floatingActionButton,
                                    filter = filter,
                                    navigationBar = navigationBar,
                                    sortingRow = sortingRow,
                                )
                            }
                        }

                        Volume(
                            volumeUpdate = volume,
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                    }

                    val lifecycleState by lifecycleManager.lifecycleState.collectAsState()
                    LifecycleIndicator(lifecycleState = lifecycleState, ::finishApp)
                }
            }
        }

        lifecycleScope.launchWhenCreated {
            viewModel.finishActivityFlow.collect { finish() }
        }

        lifecycleScope.launchWhenCreated {
            viewModel.isConnectedToRenderer.collect { isConnectedToRenderer = it }
        }
    }

    @Composable
    private fun SortingRow(
        sortModel: ViewState.SortModel,
        modifier: Modifier = Modifier,
        onOrderByOptionClick: (ViewState.OrderBy) -> Unit,
        onShowDialog: () -> Unit,
        onChangeAscensionOrder: () -> Unit,
        onDismissDialog: () -> Unit,
    ) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.End) {
            Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onShowDialog, shape = RoundedCornerShape(16.dp)) {
                        Row(modifier = Modifier.width(72.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_sort),
                                contentDescription = null,
                                tint = MaterialTheme.colors.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                stringResource(sortModel.orderBy.text),
                                color = MaterialTheme.colors.onSurface,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    Spacer(modifier = Modifier.padding(2.dp))

                    Divider(
                        color = MaterialTheme.colors.onSurface,
                        modifier = Modifier
                            .height(16.dp)
                            .width(1.15.dp)
                    )

                    Spacer(modifier = Modifier.padding(2.dp))

                    IconButton(
                        onClick = onChangeAscensionOrder,
                        modifier = Modifier.requiredSize(24.dp)
                    ) {
                        Crossfade(sortModel.order) { order ->
                            when (order) {
                                ViewState.SortOrder.Ascending ->
                                    Icon(
                                        painter = painterResource(R.drawable.ic_arrow_up),
                                        contentDescription = null
                                    )

                                ViewState.SortOrder.Descending -> Icon(
                                    painter = painterResource(R.drawable.ic_arrow_down),
                                    contentDescription = null
                                )
                            }
                        }
                    }
                }

                DropdownMenu(
                    expanded = sortModel.isSortByDialogExpanded,
                    onDismissRequest = onDismissDialog,
                ) {
                    for (option in ViewState.OrderBy.values()) {
                        DropdownMenuItem(onClick = {
                            onOrderByOptionClick(option)
                        }) {
                            Text(
                                text = stringResource(option.text),
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                                overflow = TextOverflow.Ellipsis
                            )

                            if (option == sortModel.orderBy) {
                                Icon(painterResource(R.drawable.ic_check), null)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Volume(volumeUpdate: VolumeUpdate, modifier: Modifier = Modifier) {
        AnimatedVisibility(
            modifier = modifier,
            visible = volumeUpdate is VolumeUpdate.Show
        ) {
            Card(
                shape = RoundedCornerShape(
                    topEnd = 32.dp,
                    bottomEnd = 32.dp
                ),
                elevation = 8.dp
            ) {
                Surface(modifier = Modifier.padding(16.dp)) {
                    Row {
                        val volume = volumeUpdate.volume.value
                        val icon = when {
                            volume < 5 -> R.drawable.ic_volume_mute
                            volume < 35 -> R.drawable.ic_volume_down
                            volume >= 35 -> R.drawable.ic_volume_up
                            else -> R.drawable.ic_volume_up
                        }

                        Icon(
                            painter = painterResource(id = icon),
                            contentDescription = null
                        )

                        Text("Volume", Modifier.padding(horizontal = 8.dp))
                        Text("$volume", modifier = Modifier.defaultMinSize(minWidth = 24.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun Portrait(
        upnpState: PlaybackState,
        showControls: Boolean,
        navigationBar: ModifierComposableFactory,
        folderContents: ModifierComposableFactory,
        filter: @Composable () -> Unit,
        loadingIndicator: @Composable () -> Unit,
        floatingActionButton: @Composable (Modifier) -> Unit,
        sortingRow: @Composable (Modifier) -> Unit,
    ) {
        Column {
            navigationBar(Modifier.padding(start = 16.dp))
            loadingIndicator()
            sortingRow(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp)
            )

            Row(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    folderContents(Modifier)

                    androidx.compose.animation.AnimatedVisibility(
                        visible = !showControls,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        floatingActionButton(Modifier)
                    }
                }
            }

            filter()

            AnimatedVisibility(visible = showControls) {
                Surface(elevation = 8.dp, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
                    Controls(upnpState, modifier = Modifier.padding(24.dp))
                }
            }
        }
    }

    @Composable
    private fun Landscape(
        upnpState: PlaybackState,
        showControls: Boolean,
        folderContents: ModifierComposableFactory,
        floatingActionButton: @Composable (Modifier) -> Unit,
        filter: @Composable () -> Unit,
        loadingIndicator: @Composable () -> Unit,
    ) {
        Column {
            loadingIndicator()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Row {
                    Column(modifier = Modifier.weight(1f)) {
                        folderContents(Modifier)
                    }

                    Row(
                        modifier = Modifier
                            .animateContentSize(animationSpec = tween())
                            .padding(top = 8.dp)
                            .then(
                                if (showControls) {
                                    Modifier.weight(1f)
                                } else {
                                    Modifier.width(0.dp)
                                }
                            )
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .requiredWidth(2.dp)
                                .background(color = MaterialTheme.colors.primary)
                        )

                        Controls(
                            upnpState = upnpState,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    !showControls,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    floatingActionButton(Modifier)
                }
            }

            filter()
        }
    }

    @Composable
    private fun LoadingIndicator(loading: Boolean) {
        Box(modifier = Modifier.height(progressIndicatorSize)) {
            AnimatedVisibility(visible = loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .height(progressIndicatorSize)
                        .fillMaxWidth()
                )
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun Folders(
        contents: FolderContents,
        showThumbnails: Boolean,
        selectedId: String?,
        modifier: Modifier = Modifier
    ) {
        when (contents) {
            is FolderContents.Contents -> {
                if (contents.items.isEmpty())
                    Text(
                        text = "Oops, nothing to see here", modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp)
                            .padding(top = 8.dp)
                    )

                LazyColumn(modifier = modifier) {
                    items(contents.items, key = { it.id }) { item ->
                        val color = animateColorAsState(
                            targetValue = if (selectedId == item.id) {
                                MaterialTheme.colors.primary.copy(alpha = 0.35f)
                            } else {
                                Color.Unspecified
                            }
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.itemClick(item) }
                                .let {
                                    if (selectedId == item.id)
                                        it.background(color.value)
                                    else
                                        it
                                }
                                .animateItemPlacement(),
                        ) {
                            Spacer(modifier = Modifier.padding(8.dp))
                            val imageModifier = Modifier.size(32.dp)
                            when (item.type) {
                                ItemType.CONTAINER -> {
                                    Image(
                                        painterResource(id = R.drawable.ic_folder_24dp),
                                        contentDescription = null,
                                        modifier = imageModifier
                                    )
                                }
                                ItemType.AUDIO -> Image(
                                    painterResource(id = R.drawable.ic_music),
                                    contentDescription = null,
                                    imageModifier
                                )
                                ItemType.IMAGE -> Image(
                                    if (showThumbnails) {
                                        rememberGlidePainter(item.uri)
                                    } else {
                                        painterResource(id = R.drawable.ic_image)
                                    },
                                    contentDescription = null,
                                    imageModifier
                                )
                                ItemType.VIDEO -> Image(
                                    if (showThumbnails) {
                                        rememberGlidePainter(item.uri)
                                    } else {
                                        painterResource(id = R.drawable.ic_video)
                                    },
                                    contentDescription = null,
                                    imageModifier
                                )
                                ItemType.MISC -> {
                                    // TODO Handle misc type
                                }
                            }

                            Text(
                                text = item.title,
                                maxLines = 1,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.subtitle1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            is FolderContents.Empty -> {
            }
        }
    }

    @Composable
    private fun NavigationBar(folders: List<Folder>, modifier: Modifier = Modifier) {
        LazyRow(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            itemsIndexed(folders) { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        viewModel.navigateTo(item)
                    }
                ) {
                    val labelColor: Color
                    val arrowColor: Color

                    if (index == folders.size - 1) {
                        labelColor = MaterialTheme.colors.primary
                        arrowColor = MaterialTheme.colors.primary
                    } else {
                        labelColor = Color.Unspecified
                        arrowColor = MaterialTheme.colors.onSurface
                    }

                    if (index == 0) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_folder_home),
                            contentDescription = null,
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                        )
                    } else {
                        Image(
                            painterResource(id = R.drawable.ic_next_folder),
                            null,
                            colorFilter = ColorFilter.tint(arrowColor)
                        )
                    }

                    Box {
                        Text(
                            text = item.folderModel.title,
                            style = MaterialTheme.typography.caption,
                            fontWeight = FontWeight.Bold,
                            color = labelColor,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Controls(upnpState: PlaybackState, modifier: Modifier = Modifier) {
        val defaultState: PlaybackState.Default? = upnpState as? PlaybackState.Default

        Column(modifier = modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(defaultState?.title ?: "")
            }

            Slider(
                value = (defaultState?.elapsedPercent ?: 0).toFloat() / 100,
                onValueChange = {
                    viewModel.moveTo((it * 100).toInt())
                })

            Row(horizontalArrangement = Arrangement.Center) {
                Text(defaultState?.position ?: "00:00")
                Text("/")
                Text(defaultState?.duration ?: "00:00")
            }

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            ) {
                val sizeModifier = Modifier.size(42.dp)

                IconButton(onClick = { viewModel.playerButtonClick(PlayerButton.PREVIOUS) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip_previous),
                        contentDescription = null,
                        modifier = sizeModifier
                    )
                }

                IconButton(onClick = {
                    val button = if (defaultState?.state == TransportState.PAUSED_PLAYBACK)
                        PlayerButton.PLAY
                    else
                        PlayerButton.PAUSE

                    viewModel.playerButtonClick(button)
                }) {
                    Icon(
                        painter = painterResource(id = defaultState?.icon ?: R.drawable.ic_play),
                        contentDescription = null,
                        modifier = sizeModifier
                    )
                }

                IconButton(onClick = { viewModel.playerButtonClick(PlayerButton.STOP) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_stop),
                        contentDescription = null,
                        modifier = sizeModifier
                    )
                }

                IconButton(onClick = { viewModel.playerButtonClick(PlayerButton.NEXT) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip_next),
                        contentDescription = null,
                        modifier = sizeModifier
                    )
                }
            }
        }
    }

    @Composable
    private fun RendererFloatingActionButton(
        selectRendererState: ViewState.SelectRendererState,
        renderers: List<ViewState.RenderersState.RendererModel>,
        selectedItem: ViewState.RenderersState.RendererModel?,
        onDismissDialog: () -> Unit,
        onExpandButton: () -> Unit,
        onExpandDialog: () -> Unit,
        onCancelClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        LaunchedEffect(selectRendererState) {
            delay(5000)
            onDismissDialog()
        }

        Box(modifier = modifier) {
            FloatingActionButton(onClick = {
                if (selectRendererState.isSelectRendererButtonExpanded)
                    onExpandDialog()
                else {
                    onExpandButton()
                }
            }, modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painterResource(id = R.drawable.ic_cast), null)
                    // Toggle the visibility of the content with animation.
                    AnimatedVisibility(visible = selectRendererState.isSelectRendererButtonExpanded) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.wrapContentWidth()) {
                            Text(
                                text = selectedItem?.title ?: "Stream to",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp)
                            )

                            AnimatedVisibility(selectedItem != null) {
                                IconButton(onClick = onCancelClick) {
                                    Icon(
                                        painterResource(id = R.drawable.ic_close),
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = selectRendererState.isSelectRendererDialogExpanded,
                onDismissRequest = onDismissDialog,
            ) {
                renderers.forEach { item ->
                    DropdownMenuItem(onClick = { viewModel.selectRenderer(item.id) }) {
                        Text(
                            text = item.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsMenu(
        isExpanded: Boolean,
        onExpandDialog: () -> Unit,
        onDismissDialog: () -> Unit,
        onSettingsClick: () -> Unit,
        onFilterClick: () -> Unit
    ) {
        IconButton(onClick = onExpandDialog) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                tint = LocalContentColor.current.copy(alpha = 0.75f)
            )
        }

        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = onDismissDialog,
            offset = DpOffset((-100).dp, 0.dp),
        ) {
            DropdownMenuItem(
                onClick = {
                    onDismissDialog()
                    onFilterClick()
                }
            ) {
                Text(stringResource(id = R.string.search))
            }

            DropdownMenuItem(
                onClick = {
                    onDismissDialog()
                    onSettingsClick()
                }
            ) {
                Text(stringResource(R.string.title_feature_settings))
            }
        }
    }

    @Composable
    private fun Filter(
        initialValue: String,
        onValueChange: (String) -> Unit,
        onCloseClick: () -> Unit
    ) {
        OutlinedTextField(
            value = initialValue,
            onValueChange = onValueChange,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
            trailingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            onCloseClick()
                        }
                )
            }
        )
    }

    private fun openSettings() {
        lifecycleScope.launch(Dispatchers.IO) {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (isConnectedToRenderer) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    viewModel.playerButtonClick(PlayerButton.RAISE_VOLUME)
                    true
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    viewModel.playerButtonClick(PlayerButton.LOWER_VOLUME)
                    true
                }
                else -> super.onKeyDown(keyCode, event)
            }
        } else super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        viewModel.navigateBack()
    }

    private val PlaybackState.Default.icon: Int
        inline get() = when (state) {
            TransportState.PLAYING -> R.drawable.ic_pause
            TransportState.STOPPED,
            TransportState.TRANSITIONING,
            TransportState.PAUSED_PLAYBACK,
            TransportState.PAUSED_RECORDING,
            TransportState.RECORDING,
            TransportState.NO_MEDIA_PRESENT,
            TransportState.CUSTOM,
            -> R.drawable.ic_play
        }
}
