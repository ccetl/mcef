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
package ccetl.mcef

import org.cef.callback.CefDragData
import org.cef.misc.CefCursorType

class MCEFDragContext {
    /**
     * Gets the [CefDragData] of the current drag operation
     *
     * @return the current drag operation's data
     */
    var dragData: CefDragData? = null
        private set

    /**
     * Gets the allowed operation mask for this drag event
     *
     * @return -1 for any, 0 for none, 1 for copy (TODO: others)
     */
    var mask = 0
        private set
    private var cursorOverride = -1

    /**
     * Gets the browser-set cursor
     *
     * @return the cursor that has been set by the browser, disregarding drag operations
     */
    var actualCursor = -1
        private set

    /**
     * Used to prevent re-selecting stuff while dragging
     * If the user is dragging, emulate having no buttons pressed
     *
     * @param btnMask the actual mask
     * @return a mask modified based on if the user is dragging
     */
    fun getVirtualModifiers(btnMask: Int): Int {
        return if (dragData != null) 0 else btnMask
    }

    /**
     * When the user is dragging, the browser-set cursor shouldn't be used
     * Instead the cursor should change based on what action would be performed when they release at the given location
     * However, the browser-set cursor also needs to be tracked, so this handles that as well
     *
     * @param cursorType the actual cursor type (should be the result of [MCEFDragContext.actualCursor] if you're just trying to see the current cursor)
     * @return the drag operation modified cursor if dragging, or the actual cursor if not
     */
    fun getVirtualCursor(cursorType: Int): Int {
        var cursorType1 = cursorType
        actualCursor = cursorType
        if (cursorOverride != -1) cursorType1 = cursorOverride
        return cursorType1
    }

    val isDragging: Boolean
        /**
         * Checks if a drag operation is currently happening
         *
         * @return true if the user is dragging, elsewise false
         */
        get() = dragData != null

    fun startDragging(dragData: CefDragData, mask: Int) {
        this.dragData = dragData
        this.mask = mask
    }

    fun stopDragging() {
        dragData!!.dispose()
        dragData = null
        mask = 0
        cursorOverride = -1
    }

    fun updateCursor(operation: Int): Boolean {
        if (dragData == null) return false
        val currentOverride = cursorOverride
        cursorOverride = when (operation) {
            0 -> CefCursorType.NO_DROP.ordinal
            1 -> CefCursorType.COPY.ordinal
            16 -> CefCursorType.MOVE.ordinal
            else -> -1
        }
        return currentOverride != cursorOverride && cursorOverride != -1
    }
}
