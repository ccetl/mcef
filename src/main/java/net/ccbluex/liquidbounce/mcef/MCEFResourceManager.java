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

package net.ccbluex.liquidbounce.mcef;

import net.ccbluex.liquidbounce.mcef.internal.MCEFDownloadListener;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;

/**
 * A downloader and extraction tool for java-cef builds.
 * <p>
 * Downloads for <a href="https://github.com/CinemaMod/java-cef">CinemaMod java-cef</a> are provided by the CinemaMod Group unless changed
 * in the MCEFSettings properties file; see {@link MCEFSettings}.
 * Email ds58@mailbox.org for any questions or concerns regarding the file hosting.
 */
public class MCEFResourceManager {

    private static final String JAVA_CEF_DOWNLOAD_URL =
            "${host}/java-cef-builds/${java-cef-commit}/${platform}.tar.gz";
    private static final String JAVA_CEF_CHECKSUM_DOWNLOAD_URL =
            "${host}/java-cef-builds/${java-cef-commit}/${platform}.tar.gz.sha256";

    private final String host;
    private final String javaCefCommitHash;
    private final MCEFPlatform platform;
    private final MCEFDownloadListener percentCompleteConsumer = MCEFDownloadListener.INSTANCE;
    public static File platformDirectory;

    private MCEFResourceManager(String host, String javaCefCommitHash, MCEFPlatform platform) {
        this.host = host;
        this.javaCefCommitHash = javaCefCommitHash;
        this.platform = platform;
    }

    public static MCEFResourceManager newResourceManager() throws IOException {
        var javaCefCommit = MCEF.getJavaCefCommit();
        MCEF.getLogger().info("java-cef commit: " + javaCefCommit);
        var settings = MCEF.getSettings();

        return new MCEFResourceManager(settings.getDownloadMirror(), javaCefCommit,
                MCEFPlatform.getPlatform());
    }

    public boolean requiresDownload(final File directory) throws IOException {
        if (!directory.exists()) {
            directory.mkdirs();
        }

        platformDirectory = new File(directory, MCEFPlatform.getPlatform().getNormalizedName());
        var checksumFile = new File(directory, platform.getNormalizedName() + ".tar.gz.sha256");
        // TODO: Pass File to CEF directly
        System.setProperty("jcef.path", platformDirectory.getCanonicalPath());

        // We always download the checksum for the java-cef build
        // We will compare this with mcef-libraries/<platform>.tar.gz.sha256
        // If the contents of the files differ (or it doesn't exist locally), we know we need to redownload JCEF
        boolean checksumMatches;
        try {
            checksumMatches = compareChecksum(checksumFile);
        } catch (IOException e) {
            MCEF.getLogger().error("Failed to compare checksum", e);

            // Assume checksum matches if we can't compare
            checksumMatches = true;
        }
        var platformDirectoryExists = platformDirectory.exists();

        MCEF.getLogger().info("Checksum matches: " + checksumMatches);
        MCEF.getLogger().info("Platform directory exists: " + platformDirectoryExists);

        return !checksumMatches || !platformDirectoryExists;
    }

    public void downloadJcef(final File directory) throws IOException {
        var platformDirectory = new File(directory, MCEFPlatform.getPlatform().getNormalizedName());

        try {
            MCEF.getLogger().info("Downloading JCEF...");
            downloadJavaCefBuild(directory);

            if (platformDirectory.exists() && platformDirectory.delete()) {
                MCEF.getLogger().info("Platform directory already present, deleting due to checksum mismatch");
            }

            MCEF.getLogger().info("Extracting JCEF...");
            extractJavaCefBuild(directory);
        } catch (final Exception e) {
            if (directory.exists() && directory.delete()) {
                MCEF.getLogger().info("Failed to download JCEF, deleting directory due to exception");
            }
            throw e;
        }

        MCEFDownloadListener.INSTANCE.setDone(true);
    }

    public String getHost() {
        return host;
    }

    public String getJavaCefDownloadUrl() {
        return formatURL(JAVA_CEF_DOWNLOAD_URL);
    }

    public String getJavaCefChecksumDownloadUrl() {
        return formatURL(JAVA_CEF_CHECKSUM_DOWNLOAD_URL);
    }

    private String formatURL(String url) {
        return url
                .replace("${host}", host)
                .replace("${java-cef-commit}", javaCefCommitHash)
                .replace("${platform}", platform.getNormalizedName());
    }

    private void downloadJavaCefBuild(File mcefLibrariesPath) throws IOException {
        percentCompleteConsumer.setTask("Downloading JCEF");
        var tarGzArchive = new File(mcefLibrariesPath, platform.getNormalizedName() + ".tar.gz");

        if (tarGzArchive.exists() && tarGzArchive.delete()) {
            MCEF.getLogger().info(".tar.gz archive already present, deleting due to checksum mismatch");
        }

        downloadFile(getJavaCefDownloadUrl(), tarGzArchive, percentCompleteConsumer);
    }

    /**
     * @return true if the jcef build checksum file matches the remote checksum file (for the {@link MCEFResourceManager#javaCefCommitHash}),
     * false if the jcef build checksum file did not exist or did not match; this means we should redownload JCEF
     * @throws IOException
     */
    private boolean compareChecksum(File checksumFile) throws IOException {
        // Create temporary checksum file with the same name as the real checksum file and .temp appended
        var tempChecksumFile = new File(checksumFile.getCanonicalPath() + ".temp");

        percentCompleteConsumer.setTask("Downloading Checksum");
        downloadFile(getJavaCefChecksumDownloadUrl(), tempChecksumFile, percentCompleteConsumer);

        if (checksumFile.exists()) {
            boolean sameContent = FileUtils.contentEquals(checksumFile, tempChecksumFile);

            if (sameContent) {
                tempChecksumFile.delete();
                return true;
            }
        }

        tempChecksumFile.renameTo(checksumFile);
        return false;
    }

    private void extractJavaCefBuild(File mcefLibrariesPath) {
        var tarGzArchive = new File(mcefLibrariesPath, platform.getNormalizedName() + ".tar.gz");

        extractTarGz(tarGzArchive, mcefLibrariesPath, percentCompleteConsumer);
        if (tarGzArchive.exists()) {
            tarGzArchive.delete();
        }
    }

    private static void downloadFile(String urlString, File outputFile, MCEFDownloadListener percentCompleteConsumer) throws IOException {
        MCEF.getLogger().info(urlString + " -> " + outputFile.getCanonicalPath());

        URL url = new URL(urlString);
        URLConnection urlConnection = url.openConnection();
        int fileSize = urlConnection.getContentLength();

        BufferedInputStream inputStream = new BufferedInputStream(url.openStream());
        FileOutputStream outputStream = new FileOutputStream(outputFile);

        byte[] buffer = new byte[2048];
        int count;
        int readBytes = 0;
        while ((count = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, count);
            readBytes += count;
            float percentComplete = (float) readBytes / fileSize;
            percentCompleteConsumer.setProgress(percentComplete);
            buffer = new byte[Math.max(2048, inputStream.available())];
        }

        inputStream.close();
        outputStream.close();
    }

    private static void extractTarGz(File tarGzFile, File outputDirectory, MCEFDownloadListener percentCompleteConsumer) {
        percentCompleteConsumer.setTask("Extracting");
        outputDirectory.mkdirs();

        long fileSize = tarGzFile.length();
        long totalBytesRead = 0;

        try (TarArchiveInputStream tarInput = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(tarGzFile)))) {
            TarArchiveEntry entry;
            while ((entry = tarInput.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                File outputFile = new File(outputDirectory, entry.getName());
                outputFile.getParentFile().mkdirs();

                try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = tarInput.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                        float percentComplete = (((float) totalBytesRead / fileSize) / 2.6158204f); // Roughly the compression ratio
                        percentCompleteConsumer.setProgress(percentComplete);
                        buffer = new byte[Math.max(4096, tarInput.available())];
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract tar.gz", e);
        }

        percentCompleteConsumer.setProgress(1.0f);
    }
}
