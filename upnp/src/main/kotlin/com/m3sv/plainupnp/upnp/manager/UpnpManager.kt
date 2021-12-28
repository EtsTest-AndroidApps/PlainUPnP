package com.m3sv.plainupnp.upnp.manager

import com.m3sv.plainupnp.data.upnp.UpnpDevice
import com.m3sv.plainupnp.data.upnp.UpnpRendererState
import com.m3sv.plainupnp.upnp.didl.ClingDIDLObject
import com.m3sv.plainupnp.upnp.folder.Folder
import com.m3sv.plainupnp.upnp.playback.PlaybackManager
import com.m3sv.plainupnp.upnp.volume.UpnpVolumeManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface UpnpManager : UpnpVolumeManager, PlaybackManager {
    val renderers: Flow<Collection<UpnpDevice>>
    val selectedRenderer: StateFlow<UpnpDevice?>
    val contentDirectories: Flow<Collection<UpnpDevice>>
    val upnpRendererState: Flow<UpnpRendererState>
    val navigationStack: Flow<List<Folder>>

    suspend fun navigateBack()
    suspend fun navigateTo(folder: Folder)
    suspend fun itemClick(id: String): Pair<Result, ClingDIDLObject?>
    suspend fun seekTo(progress: Int)
    suspend fun selectContentDirectory(id: String?): Result
    suspend fun selectRenderer(id: String?)
}
