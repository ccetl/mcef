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

import ccetl.mcef.MCEFPlatform.Companion.platform
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.misc.CefCursorType
import org.cef.network.CefRequest
import org.lwjgl.glfw.GLFW
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.*
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * An API to create Chromium web browsers in Minecraft. Uses
 * a modified version of java-cef (Java Chromium Embedded Framework).
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
object MCEF {
    /**
     * Supplies the lwjgl functions with the needed window.
     */
    private var windowSupplier: Supplier<Long>? = null

    /**
     * Submits a [Runnable] for execution.
     */
    private var submitter: Consumer<Runnable>? = null
    var settings: MCEFSettings? = null
        /**
         * Get access to various settings for MCEF.
         * @return Returns the existing [MCEFSettings] or creates a new [MCEFSettings] and loads from disk (blocking)
         */
        get() {
            if (field == null) {
                field = MCEFSettings()
            }
            return field
        }
        private set
    private var app: MCEFApp? = null
    private var client: MCEFClient? = null
    private val awaitingInit = ArrayList<Consumer<Boolean>>()


    fun scheduleForInit(task: Consumer<Boolean>) {
        awaitingInit.add(task)
    }

    fun initialize(windowSupplier: Supplier<Long>, submitter: Consumer<Runnable>, exit: Runnable?): Boolean {
        MCEFLogger.logger!!.info("Initializing CEF on " + platform.normalizedName + "...")
        if (CefUtil.init()) {
            MCEF.windowSupplier = windowSupplier
            MCEF.submitter = submitter
            app = MCEFApp(CefUtil.getCefApp())
            client = MCEFClient(CefUtil.getCefClient())
            awaitingInit.forEach(Consumer { t: Consumer<Boolean> -> t.accept(true) })
            awaitingInit.clear()
            MCEFLogger.logger!!.info("Chromium Embedded Framework initialized")
            app!!.handle.registerSchemeHandlerFactory(
                "mod", ""
            ) { _: CefBrowser?, _: CefFrame?, _: String?, request: CefRequest -> ModScheme(request.getURL()) }

            // Handle shutdown events, macOS is special
            // These are important; the jcef process will linger around if not done
            val platform = platform
            if (platform.isLinux || platform.isWindows) {
                Runtime.getRuntime().addShutdownHook(Thread({ shutdown() }, "MCEF-Shutdown"))
            } else if (platform.isMacOS) {
                CefUtil.getCefApp().macOSTerminationRequestRunnable = Runnable {
                    shutdown()
                    exit?.run()
                }
            }
            return true
        }
        awaitingInit.forEach(Consumer { t: Consumer<Boolean> -> t.accept(false) })
        awaitingInit.clear()
        MCEFLogger.logger!!.info("Could not initialize Chromium Embedded Framework")
        shutdown()
        return false
    }

    /**
     * Will assert that MCEF has been initialized; throws a [RuntimeException] if not.
     * @return the [MCEFApp] instance
     */
    fun getApp(): MCEFApp? {
        assertInitialized()
        return app
    }

    /**
     * Will assert that MCEF has been initialized; throws a [RuntimeException] if not.
     * @return the [MCEFClient] instance
     */
    fun getClient(): MCEFClient? {
        assertInitialized()
        return client
    }

    /**
     * Will assert that MCEF has been initialized; throws a [RuntimeException] if not.
     * Creates a new Chromium web browser with some starting URL. Can set it to be transparent rendering.
     * @return the [MCEFBrowser] web browser instance
     */
    fun createBrowser(renderer: MCEFRenderer, url: String): MCEFBrowser {
        assertInitialized()
        val browser = MCEFBrowser(client!!, renderer, windowSupplier!!, submitter!!, url)
        browser.setCloseAllowed()
        browser.createImmediately()
        return browser
    }

    /**
     * Will assert that MCEF has been initialized; throws a [RuntimeException] if not.
     * Creates a new Chromium web browser with some starting URL, width, and height.
     * Can set it to be transparent rendering.
     * @return the [MCEFBrowser] web browser instance
     */
    fun createBrowser(renderer: MCEFRenderer, url: String, width: Int, height: Int): MCEFBrowser {
        assertInitialized()
        val browser = MCEFBrowser(client!!, renderer, windowSupplier!!, submitter!!, url)
        browser.setCloseAllowed()
        browser.createImmediately()
        browser.resize(width, height)
        return browser
    }

    val isInitialized: Boolean
        /**
         * Check if MCEF is initialized.
         * @return true if MCEF is initialized correctly, false if not
         */
        get() = client != null

    /**
     * Request a shutdown of MCEF/CEF. Nothing will happen if not initialized.
     */
    fun shutdown() {
        if (isInitialized) {
            CefUtil.shutdown()
            client = null
            app = null
        }
    }

    /**
     * Check if MCEF has been initialized, throws a [RuntimeException] if not.
     */
    private fun assertInitialized() {
        if (!isInitialized) {
            throw RuntimeException("Chromium Embedded Framework was never initialized.")
        }
    }

    @get:Throws(IOException::class)
    val javaCefCommit: String?
        /**
         * Get the git commit hash of the java-cef code (either from MANIFEST.MF or from the git repo on-disk if in a
         * development environment). Used for downloading the java-cef release.
         * @return The git commit hash of java-cef
         */
        get() {
            // First check system property
            if (System.getProperty("mcef.java.cef.commit") != null) {
                return System.getProperty("mcef.java.cef.commit")
            }

            // Find jcef.commit file in the JAR root
            val commitResource = MCEF::class.java.getClassLoader().getResource("jcef.commit")
            if (commitResource != null) {
                return BufferedReader(InputStreamReader(commitResource.openStream())).readLine()
            }

            // Try to get from resources (if loading from a jar)
            val resources = MCEF::class.java.getClassLoader().getResources("META-INF/MANIFEST.MF")
            val commits: MutableMap<String, String> = HashMap(1)
            resources.asIterator().forEachRemaining { resource: URL ->
                val properties = Properties()
                try {
                    properties.load(resource.openStream())
                    if (properties.containsKey("java-cef-commit")) {
                        commits[resource.file] = properties.getProperty("java-cef-commit")
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            if (commits.isNotEmpty()) {
                return commits[commits.keys.stream().toList()[0]]
            }

            // Try to get from the git submodule (if loading from development environment)
            val processBuilder = ProcessBuilder("git", "submodule", "status", "common/java-cef")
            processBuilder.directory(File("../../"))
            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String
            while (reader.readLine().also { line = it } != null) {
                val parts = line.trim { it <= ' ' }.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                return parts[0].replace("+", "")
            }
            return null
        }

    /**
     * Helper method to get a GLFW cursor handle for the given [CefCursorType] cursor type
     */
    fun getGLFWCursorHandle(cursorType: CefCursorType): Long {
        if (CEF_TO_GLFW_CURSORS.containsKey(cursorType)) {
            return CEF_TO_GLFW_CURSORS[cursorType]!!
        }
        val glfwCursorHandle = GLFW.glfwCreateStandardCursor(cursorType.glfwId)
        CEF_TO_GLFW_CURSORS[cursorType] = glfwCursorHandle
        return glfwCursorHandle
    }

    private val CEF_TO_GLFW_CURSORS = HashMap<CefCursorType, Long>()
}
