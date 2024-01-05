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

import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * This class mostly just interacts with org.cef.* for internal use in {@link MCEF}
 */
object CefUtil {
    private var initialized: Boolean = false
    private lateinit var cefAppInstance: CefApp
    private lateinit var cefClientInstance: CefClient

    private fun setUnixExecutable(file: File) {
        val perms = HashSet<PosixFilePermission>()
        perms.add(PosixFilePermission.OWNER_READ)
        perms.add(PosixFilePermission.OWNER_WRITE)
        perms.add(PosixFilePermission.OWNER_EXECUTE)

        try {
            Files.setPosixFilePermissions(file.toPath(), perms)
        } catch(e: IOException) {
            e.printStackTrace()
        }
    }

    fun init(): Boolean {
        val platform = MCEFPlatform.platform

        // Ensure binaries are executable
        if (platform.isLinux) {
            val jcefHelperFile = File(System.getProperty("mcef.libraries.path"), platform.normalizedName + "/jcef_helper")
            setUnixExecutable(jcefHelperFile)
        } else if (platform.isMacOS) {
            val mcefLibrariesPath = File(System.getProperty("mcef.libraries.path"))
            val jcefHelperFile = File(mcefLibrariesPath, platform.normalizedName + "/jcef_app.app/Contents/Frameworks/jcef Helper.app/Contents/MacOS/jcef Helper")
            val jcefHelperGPUFile = File(mcefLibrariesPath, platform.normalizedName + "/jcef_app.app/Contents/Frameworks/jcef Helper (GPU).app/Contents/MacOS/jcef Helper (GPU)")
            val jcefHelperPluginFile = File(mcefLibrariesPath, platform.normalizedName + "/jcef_app.app/Contents/Frameworks/jcef Helper (Plugin).app/Contents/MacOS/jcef Helper (Plugin)")
            val jcefHelperRendererFile = File(mcefLibrariesPath, platform.normalizedName + "/jcef_app.app/Contents/Frameworks/jcef Helper (Renderer).app/Contents/MacOS/jcef Helper (Renderer)")
            setUnixExecutable(jcefHelperFile)
            setUnixExecutable(jcefHelperGPUFile)
            setUnixExecutable(jcefHelperPluginFile)
            setUnixExecutable(jcefHelperRendererFile)
        }

        val cefSwitches = arrayOf(
                "--autoplay-policy=no-user-gesture-required",
                "--disable-web-security",
                "--enable-widevine-cdm", // https://canary.discord.com/channels/985588552735809696/992495232035868682/1151704612924039218
                "--off-screen-rendering-enabled",
                "--off-screen-frame-rate=60",
                //"--disable-gpu"

                // TODO: should probably make this configurable
                //       based off this page: https://magpcss.org/ceforum/viewtopic.php?f=6&t=11672
                //       it seems the solution to the white screen is to add the "--disable-gpu" switch
                //       but that shouldn't be done on all devices, so either we need to figure out a pattern and setup code to add the switch based off that, or add it as a config, if that is the case
        )

        if (!CefApp.startup(cefSwitches)) {
            return false
        }

        val settings = MCEF.settings

        val cefSettings = CefSettings()
        cefSettings.windowless_rendering_enabled = true
        cefSettings.background_color = cefSettings.ColorType(0, 255, 255, 255)
        // Set the user agent if there's one defined in MCEFSettings
        if (settings!!.userAgent != null) {
            cefSettings.user_agent = settings.userAgent
        } else {
            // If there is no custom defined user agent, set a user agent product.
            // Work around for Google sign-in "This browser or app may not be secure."
            cefSettings.user_agent_product = "MCEF/2"
        }

        cefAppInstance = CefApp.getInstance(cefSwitches, cefSettings)
        cefClientInstance = cefAppInstance.createClient()

        initialized = true
        return true
    }

    fun shutdown() {
        if (initialized) {
            initialized = false
            cefClientInstance.dispose()
            cefAppInstance.dispose()
        }
    }

    @Suppress("unused")
    fun isInitialized(): Boolean {
        return initialized
    }

    fun getCefApp(): CefApp {
        return cefAppInstance
    }

    fun getCefClient(): CefClient {
        return cefClientInstance
    }
}
