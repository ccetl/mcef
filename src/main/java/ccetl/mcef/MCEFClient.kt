/*
 *     MCEF (Minecraft Chromium Embedded Framework)
 *     Copyright (C) 2023 CinemaMod Group
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 */

@file:Suppress("unused")

package ccetl.mcef

import org.cef.CefClient
import org.cef.CefSettings.LogSeverity
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefMenuModel
import org.cef.handler.CefContextMenuHandler
import org.cef.handler.CefDisplayHandler
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandler.ErrorCode
import org.cef.network.CefRequest.TransitionType

/**
 * A wrapper around [CefClient]
 */
class MCEFClient(@JvmField val handle: CefClient) : CefLoadHandler, CefContextMenuHandler, CefDisplayHandler {
    private val loadHandlers: MutableList<CefLoadHandler> = ArrayList()
    private val contextMenuHandlers: MutableList<CefContextMenuHandler> = ArrayList()
    private val displayHandlers: MutableList<CefDisplayHandler> = ArrayList()

    init {
        handle.addLoadHandler(this)
        handle.addContextMenuHandler(this)
        handle.addDisplayHandler(this)
    }

    fun addLoadHandler(handler: CefLoadHandler) {
        loadHandlers.add(handler)
    }

    override fun onLoadingStateChange(
        browser: CefBrowser,
        isLoading: Boolean,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        for (loadHandler in loadHandlers) loadHandler.onLoadingStateChange(browser, isLoading, canGoBack, canGoForward)
    }

    override fun onLoadStart(browser: CefBrowser, frame: CefFrame, transitionType: TransitionType) {
        for (loadHandler in loadHandlers) loadHandler.onLoadStart(browser, frame, transitionType)
    }

    override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        for (loadHandler in loadHandlers) loadHandler.onLoadEnd(browser, frame, httpStatusCode)
    }

    override fun onLoadError(
        browser: CefBrowser,
        frame: CefFrame,
        errorCode: ErrorCode,
        errorText: String,
        failedUrl: String
    ) {
        for (loadHandler in loadHandlers) loadHandler.onLoadError(browser, frame, errorCode, errorText, failedUrl)
    }

    fun addContextMenuHandler(handler: CefContextMenuHandler) {
        contextMenuHandlers.add(handler)
    }

    override fun onBeforeContextMenu(
        browser: CefBrowser,
        frame: CefFrame,
        params: CefContextMenuParams,
        model: CefMenuModel
    ) {
        for (contextMenuHandler in contextMenuHandlers) contextMenuHandler.onBeforeContextMenu(
            browser,
            frame,
            params,
            model
        )
    }

    override fun onContextMenuCommand(
        browser: CefBrowser,
        frame: CefFrame,
        params: CefContextMenuParams,
        commandId: Int,
        eventFlags: Int
    ): Boolean {
        for (contextMenuHandler in contextMenuHandlers) if (contextMenuHandler.onContextMenuCommand(
                browser,
                frame,
                params,
                commandId,
                eventFlags
            )
        ) return true
        return false
    }

    override fun onContextMenuDismissed(browser: CefBrowser, frame: CefFrame) {
        for (contextMenuHandler in contextMenuHandlers) contextMenuHandler.onContextMenuDismissed(browser, frame)
    }

    fun addDisplayHandler(handler: CefDisplayHandler) {
        displayHandlers.add(handler)
    }

    override fun onAddressChange(browser: CefBrowser, frame: CefFrame, url: String) {
        for (displayHandler in displayHandlers) displayHandler.onAddressChange(browser, frame, url)
    }

    override fun onTitleChange(browser: CefBrowser, title: String) {
        for (displayHandler in displayHandlers) displayHandler.onTitleChange(browser, title)
    }

    override fun onTooltip(browser: CefBrowser, text: String): Boolean {
        for (displayHandler in displayHandlers) if (displayHandler.onTooltip(browser, text)) return true
        return false
    }

    override fun onStatusMessage(browser: CefBrowser, value: String) {
        for (displayHandler in displayHandlers) displayHandler.onStatusMessage(browser, value)
    }

    override fun onConsoleMessage(
        browser: CefBrowser,
        level: LogSeverity,
        message: String,
        source: String,
        line: Int
    ): Boolean {
        for (displayHandler in displayHandlers) if (displayHandler.onConsoleMessage(
                browser,
                level,
                message,
                source,
                line
            )
        ) return true
        return false
    }

    override fun onCursorChange(browser: CefBrowser, cursorType: Int): Boolean {
        for (displayHandler in displayHandlers) if (displayHandler.onCursorChange(browser, cursorType)) return true
        return false
    }
}
