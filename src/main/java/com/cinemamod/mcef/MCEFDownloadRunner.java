package com.cinemamod.mcef;

import com.cinemamod.mcef.internal.MCEFDownloadListener;

import java.io.File;
import java.io.IOException;

public class MCEFDownloadRunner implements Runnable {

    @Override
    public void run() {
        try {
            setupLibraryPath();
        } catch (IOException e) {
            e.printStackTrace();
        }

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
    }

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

}
