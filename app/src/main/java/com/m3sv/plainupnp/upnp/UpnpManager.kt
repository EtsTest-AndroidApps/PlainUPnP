package com.m3sv.plainupnp.upnp

import android.arch.lifecycle.LiveData
import com.m3sv.plainupnp.data.upnp.*
import com.m3sv.plainupnp.presentation.main.data.Item
import com.m3sv.plainupnp.upnp.observables.ContentDirectoryDiscoveryObservable
import com.m3sv.plainupnp.upnp.observables.RendererDiscoveryObservable
import io.reactivex.Observable

interface UpnpManager {
    val rendererDiscovery: RendererDiscoveryObservable

    val contentDirectoryDiscovery: ContentDirectoryDiscoveryObservable

    val selectedDirectoryObservable: Observable<Directory>

    val rendererState: LiveData<RendererState>

    val renderedItem: LiveData<RenderedItem>

    val contentData: LiveData<ContentState>

    val launchLocally: Observable<LaunchLocally>

    val currentContentDirectory: UpnpDevice?

    fun renderItem(item: RenderItem)

    fun addObservers()

    fun removeObservers()

    fun selectContentDirectory(contentDirectory: UpnpDevice?)

    fun selectRenderer(renderer: UpnpDevice?)

    fun resumeUpnpController()

    fun pauseUpnpController()

    fun resumeRendererUpdate()

    fun pauseRendererUpdate()

    fun pausePlayback(): Unit?

    fun stopPlayback()

    fun resumePlayback()

    fun playNext()

    fun playPrevious()

    fun browseHome()

    fun browseTo(model: BrowseToModel)

    fun browsePrevious()

    fun moveTo(progress: Int, max: Int)
}

data class RenderItem(val item: DIDLItem, val position: Int)

data class LaunchLocally(val uri: String, val contentType: String)

/**
 * Seed is a workaround for distinct in Relay, just set it to random number when going home
 */
data class BrowseToModel(
    val id: String,
    val directoryName: String,
    val parentId: String?,
    val addToStructure: Boolean = true
)

sealed class ContentState {
    object Loading : ContentState()
    data class Success(val folderName: String, val content: List<Item>) :
        ContentState()
}