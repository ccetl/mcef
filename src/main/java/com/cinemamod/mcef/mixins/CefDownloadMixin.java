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

package com.cinemamod.mcef.mixins;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFDownloader;
import com.cinemamod.mcef.MCEFPlatform;
import com.cinemamod.mcef.MCEFSettings;
import com.cinemamod.mcef.internal.MCEFDownloadListener;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.IOException;

/**
 * <p>
 * mcef.libraries.path is where MCEF will store any required binaries. By default,
 * /path/to/.minecraft/mods/mcef-libraries.
 * <p>
 * jcef.path is the location of the standard java-cef bundle. By default,
 * /path/to/mcef-libraries/<normalized platform name> where normalized platform name comes from
 * {@link MCEFPlatform#getNormalizedName()}. This is what java-cef uses internally to find the
 * installation. Also see {@link org.cef.CefApp}.
 */
@Mixin(MinecraftClient.class)
public class CefDownloadMixin {
    @Unique
    private static void setupLibraryPath() throws IOException {
        final File mcefLibrariesDir;

        // Check for development environment
        // TODO: handle eclipse/others
        // i.e. mcef-repo/forge/build
        File buildDir = new File("../build");
        if (buildDir.exists() && buildDir.isDirectory()) {
            mcefLibrariesDir = new File(buildDir, "mcef-libraries/");
        } else {
            mcefLibrariesDir = new File("mods/mcef-libraries/");
        }

        mcefLibrariesDir.mkdirs();

        System.setProperty("mcef.libraries.path", mcefLibrariesDir.getCanonicalPath());
        System.setProperty("jcef.path", new File(mcefLibrariesDir, MCEFPlatform.getPlatform().getNormalizedName()).getCanonicalPath());
    }

    @Inject(at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;runDirectory:Ljava/io/File;", shift = At.Shift.AFTER), method = "<init>")
    public void initLibrary(CallbackInfo callbackInfo) {
        try {
            setupLibraryPath();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Thread downloadThread = new Thread(() -> {
            String javaCefCommit;

            try {
                javaCefCommit = MCEF.getJavaCefCommit();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            MCEF.getLogger().info("java-cef commit: " + javaCefCommit);

            MCEFSettings settings = MCEF.getSettings();
            MCEFDownloader downloader = new MCEFDownloader(settings.getDownloadMirror(), javaCefCommit, MCEFPlatform.getPlatform());

            boolean downloadJcefBuild;

            // We always download the checksum for the java-cef build
            // We will compare this with mcef-libraries/<platform>.tar.gz.sha256
            // If the contents of the files differ (or it doesn't exist locally), we know we need to redownload JCEF
            try {
                downloadJcefBuild = !downloader.downloadJavaCefChecksum(MCEFDownloadListener.INSTANCE);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            if (downloadJcefBuild && !settings.isSkipDownload()) {
                try {
                    downloader.downloadJavaCefBuild(MCEFDownloadListener.INSTANCE);
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }

                downloader.extractJavaCefBuild(true, MCEFDownloadListener.INSTANCE);
            }

            MCEFDownloadListener.INSTANCE.setDone(true);
        });
        downloadThread.start();
    }
}
