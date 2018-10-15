/**
 * Copyright (C) 2013 Aurélien Chabot <aurelien></aurelien>@chabot.fr>
 *
 *
 * This file is part of DroidUPNP.
 *
 *
 * DroidUPNP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *
 * DroidUPNP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *
 * You should have received a copy of the GNU General Public License
 * along with DroidUPNP.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

package org.droidupnp.controller.cling

import android.content.Context
import com.m3sv.droidupnp.data.UpnpRendererState
import org.droidupnp.controller.upnp.UpnpServiceController
import org.droidupnp.model.upnp.AUpnpRendererState
import org.droidupnp.model.upnp.Factory
import org.droidupnp.model.upnp.IContentDirectoryCommand
import org.droidupnp.model.upnp.IRendererCommand
import javax.inject.Inject

class UPnPFactory @Inject constructor(private val controller: UpnpServiceController) : Factory {

    override fun createContentDirectoryCommand(): IContentDirectoryCommand? {
        val aus = (controller.serviceListener as ServiceListener).getUpnpService()
        return aus?.controlPoint?.let { ContentDirectoryCommand(it, controller) }
    }

    override fun createRendererCommand(rs: UpnpRendererState): IRendererCommand? {
        val aus = (controller.serviceListener as ServiceListener).getUpnpService()
        return aus?.controlPoint?.let {
            RendererCommand(controller, it, rs as org.droidupnp.model.cling.UpnpRendererState)
        }
    }

    override fun createUpnpServiceController(ctx: Context): UpnpServiceController {
        return controller
    }

    override fun createRendererState(): AUpnpRendererState {
        return org.droidupnp.model.cling.UpnpRendererState()
    }
}
