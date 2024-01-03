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

package ccetl.mcef;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import ccetl.mcef.internal.MCEFDownloadListener;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

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
@SuppressWarnings("ResultOfMethodCallIgnored")
public class MCEFDownloader {
    // Magic numbers
    private static final int BUFFER_SIZE = 2048;
    private static final float COMPRESSION_RATIO_DIVISOR = 2.6158204f; // Roughly the compression ratio
    private static final int FILE_COPY_BUFFER_SIZE = 4096;

    private static final String JAVA_CEF_DOWNLOAD_URL = "${host}/java-cef-builds/${java-cef-commit}/${platform}.tar.gz";
    private static final String JAVA_CEF_CHECKSUM_DOWNLOAD_URL = "${host}/java-cef-builds/${java-cef-commit}/${platform}.tar.gz.sha256";

    private final String host;
    private final String javaCefCommitHash;
    private final MCEFPlatform platform;

    private MCEFDownloader(String host, String javaCefCommitHash, MCEFPlatform platform) {
        this.host = host;
        this.javaCefCommitHash = javaCefCommitHash;
        this.platform = platform;
    }

    public static MCEFDownloader createDownloader() throws IOException {
        String javaCefCommit = MCEF.getJavaCefCommit();
        MCEFLogger.getLogger().info("java-cef commit: " + javaCefCommit);
        MCEFSettings settings = MCEF.getSettings();

        return new MCEFDownloader(settings.getDownloadMirror(), javaCefCommit, MCEFPlatform.getPlatform());
    }

    public boolean requiresDownload(final File directory) throws IOException {
        File platformDirectory = new File(directory, MCEFPlatform.getPlatform().getNormalizedName());
        File checksumFile = new File(directory, platform.getNormalizedName() + ".tar.gz.sha256");

        setupLibraryPath(directory, platformDirectory);

        // We always download the checksum for the java-cef build
        // We will compare this with mcef-libraries/<platform>.tar.gz.sha256
        // If the contents of the files differ (or it doesn't exist locally), we know we need to redownload JCEF
        boolean checksumMatches;
        try {
            checksumMatches = compareChecksum(checksumFile);
        } catch (IOException e) {
            MCEFLogger.getLogger().error("Failed to compare checksum", e);

            // Assume checksum matches if we can't compare
            checksumMatches = true;
        }
        boolean platformDirectoryExists = platformDirectory.exists();

        MCEFLogger.getLogger().info("Checksum matches: " + checksumMatches);
        MCEFLogger.getLogger().info("Platform directory exists: " + platformDirectoryExists);

        return !checksumMatches || !platformDirectoryExists;
    }

    public void downloadJcef(final File directory) throws IOException {
        File platformDirectory = new File(directory, MCEFPlatform.getPlatform().getNormalizedName());

        try {
            MCEFLogger.getLogger().info("Downloading JCEF...");
            downloadJavaCefBuild(directory);

            if (platformDirectory.exists() && platformDirectory.delete()) {
                MCEFLogger.getLogger().info("Platform directory already present, deleting due to checksum mismatch");
            }

            if (MCEF.getSettings().isCheckDownloads()) {
                MCEFLogger.getLogger().info("Verifying the downloaded files...");
                File tarGzArchive = new File(directory, platform.getNormalizedName() + ".tar.gz");
                if (!compareChecksum(tarGzArchive, MCEF.getSettings().getCheckSums().get(platform))) {
                    throw new RuntimeException("The downloaded jcef files are corrupted.");
                }
                MCEFLogger.getLogger().info("The downloaded files are verified");
            }

            MCEFLogger.getLogger().info("Extracting JCEF...");
            extractJavaCefBuild(directory);
        } catch (final Exception e) {
            if (directory.exists() && directory.delete()) {
                MCEFLogger.getLogger().info("Failed to download JCEF, deleting directory due to exception");
            }
            throw e;
        }

        MCEFDownloadListener.INSTANCE.setDone(true);
    }

    private static void setupLibraryPath(final File mcefLibrariesDir, final File jcefDirectory) throws IOException {
        mcefLibrariesDir.mkdirs();

        System.setProperty("mcef.libraries.path", mcefLibrariesDir.getCanonicalPath());
        System.setProperty("jcef.path", jcefDirectory.getCanonicalPath());
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
        MCEFDownloadListener.INSTANCE.setTask("Downloading JCEF");
        File tarGzArchive = new File(mcefLibrariesPath, platform.getNormalizedName() + ".tar.gz");

        if (tarGzArchive.exists() && tarGzArchive.delete()) {
            MCEFLogger.getLogger().info(".tar.gz archive already present, deleting due to checksum mismatch");
        }

        downloadFile(getJavaCefDownloadUrl(), tarGzArchive);
    }

    /**
     * @return true if the jcef build checksum file matches the remote checksum file (for the {@link MCEFDownloader#javaCefCommitHash}),
     * false if the jcef build checksum file did not exist or did not match; this means we should redownload JCEF
     */
    private boolean compareChecksum(File checksumFile) throws IOException {
        // Create temporary checksum file with the same name as the real checksum file and .temp appended
        File tempChecksumFile = new File(checksumFile.getCanonicalPath() + ".temp");

        MCEFDownloadListener.INSTANCE.setTask("Downloading Checksum");
        downloadFile(getJavaCefChecksumDownloadUrl(), tempChecksumFile);

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

    private boolean compareChecksum(File file, String checksum) throws IOException {
        ByteSource byteSource = Files.asByteSource(file);
        HashCode hc = byteSource.hash(Hashing.sha256());
        return StringUtils.equalsIgnoreCase(checksum, hc.toString());
    }

    private void extractJavaCefBuild(File mcefLibrariesPath) {
        File tarGzArchive = new File(mcefLibrariesPath, platform.getNormalizedName() + ".tar.gz");

        extractTarGz(tarGzArchive, mcefLibrariesPath);
        if (tarGzArchive.exists()) {
            tarGzArchive.delete();
        }
    }

    private static void downloadFile(String urlString, File outputFile) throws IOException {
        MCEFLogger.getLogger().info(urlString + " -> " + outputFile.getCanonicalPath());

        URL url = new URL(urlString);
        URLConnection urlConnection = url.openConnection();
        int fileSize = urlConnection.getContentLength();

        BufferedInputStream inputStream = new BufferedInputStream(url.openStream());
        FileOutputStream outputStream = new FileOutputStream(outputFile);

        byte[] buffer = new byte[BUFFER_SIZE];
        int count;
        int readBytes = 0;
        while ((count = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, count);
            readBytes += count;
            float percentComplete = (float) readBytes / fileSize;
            MCEFDownloadListener.INSTANCE.setProgress(percentComplete);
            buffer = new byte[Math.max(BUFFER_SIZE, inputStream.available())];
        }

        inputStream.close();
        outputStream.close();
    }

    private static void extractTarGz(File tarGzFile, File outputDirectory) {
        MCEFDownloadListener.INSTANCE.setTask("Extracting");

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
                    byte[] buffer = new byte[FILE_COPY_BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = tarInput.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                        float percentComplete = (((float) totalBytesRead / fileSize) / COMPRESSION_RATIO_DIVISOR);
                        MCEFDownloadListener.INSTANCE.setProgress(percentComplete);
                        buffer = new byte[Math.max(FILE_COPY_BUFFER_SIZE, tarInput.available())];
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        MCEFDownloadListener.INSTANCE.setProgress(1.0f);
    }
}
