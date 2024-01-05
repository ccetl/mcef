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

@file:Suppress("MemberVisibilityCanBePrivate")

package ccetl.mcef

import ccetl.mcef.MCEFPlatform.Companion.platform
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import org.cef.browser.CefBrowser
import org.cef.browser.CefBrowserOsr
import org.cef.callback.CefDragData
import org.cef.event.CefKeyEvent
import org.cef.event.CefMouseEvent
import org.cef.event.CefMouseWheelEvent
import org.cef.misc.CefCursorType
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import java.awt.Point
import java.awt.Rectangle
import java.nio.ByteBuffer
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.math.ceil
import kotlin.math.floor

/**
 * An instance of an "Off-screen rendered" Chromium web browser.
 * Complete with a renderer, keyboard and mouse inputs, optional
 * browser control shortcuts, cursor handling, drag & drop support.
 */
class MCEFBrowser(
    client: MCEFClient,
    /**
     * The renderer for the browser.
     */
    private var renderer: MCEFRenderer,
    private val windowSupplier: Supplier<Long>,
    private val submitter: Consumer<Runnable>,
    url: String?
) : CefBrowserOsr(client.handle, url, renderer.isTransparent(), null) {
    /**
     * Stores information about drag & drop.
     */
    val dragContext = MCEFDragContext()

    /**
     * A listener that defines that happens when a cursor changes in the browser.
     * E.g., when you've hovered over a button, an input box, are selecting text, etc...
     * A default listener is created in the constructor that sets the cursor type to
     * the appropriate cursor based on the event.
     */
    var cursorChangeListener: Consumer<Int>

    /**
     * Whether MCEF should mimic the controls of a typical web browser.
     * E.g., CTRL+R for reload, CTRL+Left for back, CTRL+Right for forward, etc...
     */
    private var browserControls = true

    /**
     * Used to track when a full repaint should occur.
     */
    private var lastWidth = 0
    private var lastHeight = 0

    /**
     * A bitset representing what mouse buttons are currently pressed.
     * CEF is a bit odd and implements mouse buttons as a part of modifier flags.
     */
    private var btnMask = 0

    // Data relating to popups and graphics
    // Marked as protected in-case a mod wants to extend MCEFBrowser and override the repaint logic
    var graphics: ByteBuffer? = null
    var popupGraphics: ByteBuffer? = null
    var popupSize: Rectangle? = null
    var showPopup = false
    var popupDrawn = false

    init {
        cursorChangeListener = Consumer { cefCursorID: Int -> setCursor(CefCursorType.fromId(cefCursorID)) }
        submitter.accept(Runnable { renderer.initialize() })
    }

    @Suppress("unused")
    fun usingBrowserControls(): Boolean {
        return browserControls
    }

    /**
     * Enabling browser controls tells MCEF to mimic the behavior of an actual browser.
     * CTRL+R for reload, CTRL+Left for back, CTRL+Right for forward, etc...
     *
     * @param browserControls whether browser controls should be enabled
     * @return the browser instance
     */
    @Suppress("unused")
    fun useBrowserControls(browserControls: Boolean): MCEFBrowser {
        this.browserControls = browserControls
        return this
    }

    // Popups
    override fun onPopupShow(browser: CefBrowser, show: Boolean) {
        super.onPopupShow(browser, show)
        showPopup = show
        if (show) {
            return
        }

        submitter.accept(Runnable {
            onPaint(
                browser,
                false,
                arrayOf(popupSize!!),
                graphics!!,
                lastWidth,
                lastHeight
            )
        })
        popupSize = null
        popupDrawn = false
        popupGraphics = null
    }

    override fun onPopupSize(browser: CefBrowser, size: Rectangle) {
        super.onPopupSize(browser, size)
        popupSize = size
        popupGraphics = ByteBuffer.allocateDirect(
            size.width * size.height * 4
        )
    }

    /**
     * Draws any existing popup menu to the browser's graphics
     */
    fun drawPopup() {
        if (showPopup && popupSize != null && popupDrawn) {
            RenderSystem.bindTexture(renderer.getTextureId())
            if (renderer.isTransparent()) {
                RenderSystem.enableBlend()
            }
            RenderSystem.pixelStore(GL11.GL_UNPACK_ROW_LENGTH, popupSize!!.width)
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0)
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0)
            renderer.onPaint(popupGraphics!!, popupSize!!.x, popupSize!!.y, popupSize!!.width, popupSize!!.height)
        }
    }

    // Graphics
    override fun onPaint(
        browser: CefBrowser,
        popup: Boolean,
        dirtyRects: Array<Rectangle>,
        buffer: ByteBuffer,
        width: Int,
        height: Int
    ) {
        if (!popup && (width != lastWidth || height != lastHeight)) {
            // Copy buffer
            val graphics = ByteBuffer.allocateDirect(buffer.capacity())
            graphics.position(0).limit(graphics.capacity())
            graphics.put(buffer)
            graphics.position(0)
            buffer.position(0)

            // Draw
            renderer.onPaint(buffer, width, height)
            lastWidth = width
            lastHeight = height
        } else {
            // Don't update graphics if the renderer is not initialized
            if (renderer.getTextureId() == 0) return

            // Update sub-rects
            if (!popup) {
                // Graphics will be updated later if it's a popup
                RenderSystem.bindTexture(renderer.getTextureId())
                if (renderer.isTransparent()) RenderSystem.enableBlend()
                RenderSystem.pixelStore(GL11.GL_UNPACK_ROW_LENGTH, width)
            } else popupDrawn = true
            for (dirtyRect in dirtyRects) {
                // Check that the popup isn't being cleared from the image
                if (buffer !== graphics) // Due to how CEF handles popups, the graphics of the popup and the graphics of the browser itself need to be stored separately
                    store(buffer, if (popup) popupGraphics else graphics, dirtyRect, width, height)

                // Graphics will be updated later if it's a popup
                if (!popup) {
                    // Upload to the GPU
                    GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, dirtyRect.x)
                    GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, dirtyRect.y)
                    renderer.onPaint(buffer, dirtyRect.x, dirtyRect.y, dirtyRect.width, dirtyRect.height)
                }
            }
        }

        // Upload popup to GPU, must be fully drawn every time paint is called
        drawPopup()
    }

    fun resize(width: Int, height: Int) {
        browser_rect_.setBounds(0, 0, width, height)
        wasResized(width, height)
    }

    // Inputs
    @Suppress("unused")
    fun sendKeyPress(keyCode: Int, scanCode: Long, modifiers: Int) {
        if (browserControls) {
            if (modifiers == GLFW.GLFW_MOD_CONTROL) {
                when (keyCode) {
                    GLFW.GLFW_KEY_R -> {
                        reload()
                        return
                    }
                    GLFW.GLFW_KEY_EQUAL -> {
                        if (getZoomLevel() < 9) {
                            zoomLevel = getZoomLevel() + 1
                        }
                        return
                    }
                    GLFW.GLFW_KEY_MINUS -> {
                        if (getZoomLevel() > -9) {
                            zoomLevel = getZoomLevel() - 1
                        }
                        return
                    }
                    GLFW.GLFW_KEY_0 -> {
                        zoomLevel = 0.0
                        return
                    }
                }
            } else if (modifiers == GLFW.GLFW_MOD_ALT) {
                if (keyCode == GLFW.GLFW_KEY_LEFT && canGoBack()) {
                    goBack()
                    return
                } else if (keyCode == GLFW.GLFW_KEY_RIGHT && canGoForward()) {
                    goForward()
                    return
                }
            }
        }
        val e = CefKeyEvent(CefKeyEvent.KEY_PRESS, keyCode, keyCode.toChar(), modifiers)
        e.scancode = scanCode
        sendKeyEvent(e)
    }

    @Suppress("unused")
    fun sendKeyRelease(keyCode: Int, scanCode: Long, modifiers: Int) {
        if (browserControls) {
            if (modifiers == GLFW.GLFW_MOD_CONTROL) {
                if (keyCode == GLFW.GLFW_KEY_R) return else if (keyCode == GLFW.GLFW_KEY_EQUAL) return else if (keyCode == GLFW.GLFW_KEY_MINUS) return else if (keyCode == GLFW.GLFW_KEY_0) return
            } else if (modifiers == GLFW.GLFW_MOD_ALT) {
                if (keyCode == GLFW.GLFW_KEY_LEFT && canGoBack()) return else if (keyCode == GLFW.GLFW_KEY_RIGHT && canGoForward()) return
            }
        }
        val e = CefKeyEvent(CefKeyEvent.KEY_RELEASE, keyCode, keyCode.toChar(), modifiers)
        e.scancode = scanCode
        sendKeyEvent(e)
    }

    @Suppress("unused")
    fun sendKeyTyped(c: Char, modifiers: Int) {
        if (browserControls) {
            if (modifiers == GLFW.GLFW_MOD_CONTROL) {
                if (c.code == GLFW.GLFW_KEY_R) return else if (c.code == GLFW.GLFW_KEY_EQUAL) return else if (c.code == GLFW.GLFW_KEY_MINUS) return else if (c.code == GLFW.GLFW_KEY_0) return
            } else if (modifiers == GLFW.GLFW_MOD_ALT) {
                if (c.code == GLFW.GLFW_KEY_LEFT && canGoBack()) return else if (c.code == GLFW.GLFW_KEY_RIGHT && canGoForward()) return
            }
        }
        val e = CefKeyEvent(CefKeyEvent.KEY_TYPE, c.code, c, modifiers)
        sendKeyEvent(e)
    }

    @Suppress("unused")
    fun sendMouseMove(mouseX: Int, mouseY: Int) {
        val e = CefMouseEvent(CefMouseEvent.MOUSE_MOVED, mouseX, mouseY, 0, 0, dragContext.getVirtualModifiers(btnMask))
        sendMouseEvent(e)
        if (dragContext.isDragging) dragTargetDragOver(Point(mouseX, mouseY), 0, dragContext.mask)
    }

    // TODO: it may be necessary to add modifiers here
    @Suppress("unused")
    fun sendMousePress(mouseX: Int, mouseY: Int, button: Int) {
        // for some reason, middle and right are swapped in MC
        var button1 = button
        if (button1 == 1) {
            button1 = 2
        } else if (button1 == 2) {
            button1 = 1
        }
        when (button1) {
            0 ->  btnMask = btnMask or CefMouseEvent.BUTTON1_MASK
            1 ->  btnMask = btnMask or CefMouseEvent.BUTTON2_MASK
            2 ->  btnMask = btnMask or CefMouseEvent.BUTTON3_MASK
        }
        val e = CefMouseEvent(GLFW.GLFW_PRESS, mouseX, mouseY, 1, button1, btnMask)
        sendMouseEvent(e)
    }

    // TODO: it may be necessary to add modifiers here
    @Suppress("unused")
    fun sendMouseRelease(mouseX: Int, mouseY: Int, button: Int) {
        // For some reason, middle and right are swapped in MC
        var button1 = button
        if (button1 == 1) button1 = 2 else if (button1 == 2) button1 = 1
        if (button1 == 0 && btnMask and CefMouseEvent.BUTTON1_MASK != 0) btnMask =
            btnMask xor CefMouseEvent.BUTTON1_MASK else if (button1 == 1 && btnMask and CefMouseEvent.BUTTON2_MASK != 0) btnMask =
            btnMask xor CefMouseEvent.BUTTON2_MASK else if (button1 == 2 && btnMask and CefMouseEvent.BUTTON3_MASK != 0) btnMask =
            btnMask xor CefMouseEvent.BUTTON3_MASK
        val e = CefMouseEvent(GLFW.GLFW_RELEASE, mouseX, mouseY, 1, button1, btnMask)
        sendMouseEvent(e)

        // drag&drop
        if (dragContext.isDragging) {
            if (button1 == 0) {
                finishDragging(mouseX, mouseY)
            }
        }
    }

    // TODO: smooth scrolling
    @Suppress("unused")
    fun sendMouseWheel(mouseX: Int, mouseY: Int, amount: Double, modifiers: Int) {
        var amount1 = amount
        if (browserControls) {
            if (modifiers and GLFW.GLFW_MOD_CONTROL != 0) {
                if (amount1 > 0) {
                    if (getZoomLevel() < 9) zoomLevel = getZoomLevel() + 1
                } else if (getZoomLevel() > -9) zoomLevel = getZoomLevel() - 1
                return
            }
        }

        // macOS generally has a slow scroll speed that feels more natural with their magic mice / trackpads
        if (!platform.isMacOS) {
            // This removes the feeling of "smooth scroll"
            amount1 = if (amount1 < 0) {
                floor(amount1)
            } else {
                ceil(amount1)
            }

            // This feels about equivalent to chromium with smooth scrolling disabled -ds58
            amount1 *= 3
        }
        val e = CefMouseWheelEvent(CefMouseWheelEvent.WHEEL_UNIT_SCROLL, mouseX, mouseY, amount1, modifiers)
        sendMouseWheelEvent(e)
    }

    // Drag & drop
    override fun startDragging(browser: CefBrowser, dragData: CefDragData, mask: Int, x: Int, y: Int): Boolean {
        dragContext.startDragging(dragData, mask)
        dragTargetDragEnter(dragContext.dragData, Point(x, y), btnMask, dragContext.mask)
        // Indicates to CEF to not handle the drag event natively
        // reason: native drag handling doesn't work with off-screen rendering
        return false
    }

    override fun updateDragCursor(browser: CefBrowser, operation: Int) {
        if (dragContext.updateCursor(operation)) // If the cursor to display for the drag event changes, then update the cursor
            onCursorChange(this, dragContext.getVirtualCursor(dragContext.actualCursor))
        super.updateDragCursor(browser, operation)
    }

    // Expose drag & drop functions
    @Suppress("unused", "MemberVisibilityCanBePrivate")
    fun startDragging(
        dragData: CefDragData?,
        mask: Int,
        x: Int,
        y: Int
    ) { // Overload since the JCEF method requires a browser, which then goes unused
        startDragging(dragData, mask, x, y)
    }

    @Suppress("unused", "MemberVisibilityCanBePrivate")
    fun finishDragging(x: Int, y: Int) {
        dragTargetDrop(Point(x, y), btnMask)
        dragTargetDragLeave()
        dragContext.stopDragging()
        onCursorChange(this, dragContext.actualCursor)
    }

    @Suppress("unused")
    fun cancelDrag() {
        dragTargetDragLeave()
        dragContext.stopDragging()
        onCursorChange(this, dragContext.actualCursor)
    }

    // Closing
    @Suppress("unused")
    fun close() {
        renderer.cleanup()
        cursorChangeListener.accept(0)
        super.close(true)
    }

    @Throws(Throwable::class)
    override fun finalize() {
        submitter.accept(Runnable { renderer.cleanup() })
        super.finalize()
    }

    // Cursor handling
    override fun onCursorChange(browser: CefBrowser, cursorType: Int): Boolean {
        var cursorType1 = cursorType
        cursorType1 = dragContext.getVirtualCursor(cursorType1)
        cursorChangeListener.accept(cursorType1)
        return super.onCursorChange(browser, cursorType1)
    }

    fun setCursor(cursorType: CefCursorType) {
        if (cursorType == CefCursorType.NONE) {
            GLFW.glfwSetInputMode(windowSupplier.get(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN)
        } else {
            GLFW.glfwSetInputMode(windowSupplier.get(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL)
            GLFW.glfwSetCursor(windowSupplier.get(), MCEF.getGLFWCursorHandle(cursorType))
        }
    }

    companion object {
        /**
         * Copies data within a rectangle from one buffer to another
         * Used by repaint logic
         *
         * @param srcBuffer the buffer to copy from
         * @param dstBuffer the buffer to copy to
         * @param dirty     the rectangle that needs to be updated
         * @param width     the width of the browser
         * @param height    the height of the browser
         */
        fun store(srcBuffer: ByteBuffer, dstBuffer: ByteBuffer?, dirty: Rectangle, width: Int, @Suppress("UNUSED_PARAMETER") height: Int) {
            for (y in dirty.y until dirty.height + dirty.y) {
                dstBuffer!!.position((y * width + dirty.x) * 4)
                srcBuffer.position((y * width + dirty.x) * 4)
                srcBuffer.limit(dirty.width * 4 + (y * width + dirty.x) * 4)
                dstBuffer.put(srcBuffer)
                srcBuffer.position(0).limit(srcBuffer.capacity())
            }
            dstBuffer!!.position(0).limit(dstBuffer.capacity())
        }
    }
}
