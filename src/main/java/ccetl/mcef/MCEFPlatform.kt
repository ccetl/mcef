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

import org.apache.commons.exec.OS

enum class MCEFPlatform {
    LINUX_AMD64,
    LINUX_ARM64,
    WINDOWS_AMD64,
    WINDOWS_ARM64,
    MACOS_AMD64,
    MACOS_ARM64;

    val normalizedName: String
        get() = name.lowercase()
    val isLinux: Boolean
        get() = this == LINUX_AMD64 || this == LINUX_ARM64
    val isWindows: Boolean
        get() = this == WINDOWS_AMD64 || this == WINDOWS_ARM64
    val isMacOS: Boolean
        get() = this == MACOS_AMD64 || this == MACOS_ARM64

    companion object {
        @JvmStatic
        val platform: MCEFPlatform
            get() {
                if (OS.isFamilyWindows()) {
                    if (OS.isArch("amd64")) {
                        return WINDOWS_AMD64
                    } else if (OS.isArch("aarch64")) {
                        return WINDOWS_ARM64
                    }
                } else if (OS.isFamilyMac()) {
                    if (OS.isArch("x86_64")) {
                        return MACOS_AMD64
                    } else if (OS.isArch("aarch64")) {
                        return MACOS_ARM64
                    }
                } else if (OS.isFamilyUnix()) {
                    if (OS.isArch("amd64")) {
                        return LINUX_AMD64
                    } else if (OS.isArch("aarch64")) {
                        return LINUX_ARM64
                    }
                }
                val os = System.getProperty("os.name").lowercase()
                val arch = System.getProperty("os.arch").lowercase()
                throw RuntimeException("Unsupported platform: $os $arch")
            }
    }
}
