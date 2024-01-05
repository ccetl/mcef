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
import ccetl.mcef.internal.MCEFDownloadListener
import ccetl.mcef.internal.MCEFDownloadListener.done
import ccetl.mcef.internal.MCEFDownloadListener.setTask
import com.google.common.hash.Hashing
import com.google.common.io.Files
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import java.io.*
import java.net.URL
import kotlin.math.max

/**
 * A downloader and extraction tool for java-cef builds.
 *
 *
 * Downloads for [CinemaMod java-cef](https://github.com/CinemaMod/java-cef) are provided by the CinemaMod Group unless changed
 * in the MCEFSettings properties file; see [MCEFSettings].
 * Email ds58@mailbox.org for any questions or concerns regarding the file hosting.
 */
@Suppress("unused")
class MCEFDownloader private constructor(
    @Suppress("MemberVisibilityCanBePrivate")
    val host: String?,
    private val javaCefCommitHash: String,
    private val platform: MCEFPlatform
) {
    @Suppress("unused")
    @Throws(IOException::class)
    fun requiresDownload(directory: File): Boolean {
        val platformDirectory = File(directory, MCEFPlatform.platform.normalizedName)
        val checksumFile = File(directory, platform.normalizedName + ".tar.gz.sha256")
        setupLibraryPath(directory, platformDirectory)

        // We always download the checksum for the java-cef build
        // We will compare this with mcef-libraries/<platform>.tar.gz.sha256
        // If the contents of the files differ (or it doesn't exist locally), we know we need to redownload JCEF
        val checksumMatches: Boolean = try {
            compareChecksum(checksumFile)
        } catch (e: IOException) {
            MCEFLogger.logger!!.error("Failed to compare checksum", e)

            // Assume checksum matches if we can't compare
            true
        }
        val platformDirectoryExists = platformDirectory.exists()
        MCEFLogger.logger!!.info("Checksum matches: $checksumMatches")
        MCEFLogger.logger!!.info("Platform directory exists: $platformDirectoryExists")
        return !checksumMatches || !platformDirectoryExists
    }

    @Suppress("unused")
    @Throws(IOException::class)
    fun downloadJcef(directory: File) {
        val platformDirectory = File(directory, MCEFPlatform.platform.normalizedName)
        try {
            MCEFLogger.logger!!.info("Downloading JCEF...")
            downloadJavaCefBuild(directory)

            if (platformDirectory.exists() && platformDirectory.delete()) {
                MCEFLogger.logger!!.info("Platform directory already present, deleting due to checksum mismatch")
            }

            if (MCEF.settings!!.isCheckDownloads) {
                MCEFLogger.logger!!.info("Verifying the downloaded files...")
                val tarGzArchive = File(directory, platform.normalizedName + ".tar.gz")
                if (!compareChecksum(tarGzArchive, MCEF.settings!!.checkSums[platform])) {
                    throw RuntimeException("The downloaded jcef files are corrupted.")
                }
                MCEFLogger.logger!!.info("The downloaded files are verified")
            }

            MCEFLogger.logger!!.info("Extracting JCEF...")
            extractJavaCefBuild(directory)
        } catch (e: Exception) {
            if (directory.exists() && directory.delete()) {
                MCEFLogger.logger!!.info("Failed to download JCEF, deleting directory due to exception")
            }
            throw e
        }
        done = true
    }

    @Suppress("MemberVisibilityCanBePrivate")
    val javaCefDownloadUrl: String
        get() = formatURL(JAVA_CEF_DOWNLOAD_URL)
    @Suppress("MemberVisibilityCanBePrivate")
    val javaCefChecksumDownloadUrl: String
        get() = formatURL(JAVA_CEF_CHECKSUM_DOWNLOAD_URL)

    private fun formatURL(url: String): String {
        return url
            .replace("\${host}", host!!)
            .replace("\${java-cef-commit}", javaCefCommitHash)
            .replace("\${platform}", platform.normalizedName)
    }

    @Throws(IOException::class)
    private fun downloadJavaCefBuild(mcefLibrariesPath: File) {
        setTask("Downloading JCEF")
        val tarGzArchive = File(mcefLibrariesPath, platform.normalizedName + ".tar.gz")
        if (tarGzArchive.exists() && tarGzArchive.delete()) {
            MCEFLogger.logger!!.info(".tar.gz archive already present, deleting due to checksum mismatch")
        }
        downloadFile(javaCefDownloadUrl, tarGzArchive)
    }

    /**
     * @return true if the jcef build checksum file matches the remote checksum file (for the [javaCefCommitHash]),
     * false if the jcef build checksum file did not exist or did not match; this means we should download JCEF.
     */
    @Throws(IOException::class)
    private fun compareChecksum(checksumFile: File): Boolean {
        // Create temporary checksum file with the same name as the real checksum file and .temp appended
        val tempChecksumFile = File(checksumFile.getCanonicalPath() + ".temp")
        setTask("Downloading Checksum")
        downloadFile(javaCefChecksumDownloadUrl, tempChecksumFile)
        if (checksumFile.exists()) {
            val sameContent = FileUtils.contentEquals(checksumFile, tempChecksumFile)
            if (sameContent) {
                tempChecksumFile.delete()
                return true
            }
        }
        tempChecksumFile.renameTo(checksumFile)
        return false
    }

    @Throws(IOException::class)
    private fun compareChecksum(file: File, checksum: String?): Boolean {
        val byteSource = Files.asByteSource(file)
        val hc = byteSource.hash(Hashing.sha256())
        return StringUtils.equalsIgnoreCase(checksum, hc.toString())
    }

    private fun extractJavaCefBuild(mcefLibrariesPath: File) {
        val tarGzArchive = File(mcefLibrariesPath, platform.normalizedName + ".tar.gz")
        extractTarGz(tarGzArchive, mcefLibrariesPath)
        if (tarGzArchive.exists()) {
            tarGzArchive.delete()
        }
    }

    companion object {
        // Magic numbers
        private const val BUFFER_SIZE = 2048
        private const val COMPRESSION_RATIO_DIVISOR = 2.6158204f // Roughly the compression ratio
        private const val FILE_COPY_BUFFER_SIZE = 4096
        private const val JAVA_CEF_DOWNLOAD_URL = "\${host}/java-cef-builds/\${java-cef-commit}/\${platform}.tar.gz"
        private const val JAVA_CEF_CHECKSUM_DOWNLOAD_URL = "\${host}/java-cef-builds/\${java-cef-commit}/\${platform}.tar.gz.sha256"

        @Suppress("unused")
        @Throws(IOException::class)
        fun createDownloader(): MCEFDownloader {
            val javaCefCommit: String = MCEF.javaCefCommit!!
            MCEFLogger.logger?.info("java-cef commit: $javaCefCommit")
            val settings = MCEF.settings
            return MCEFDownloader(settings!!.downloadMirror, javaCefCommit, platform)
        }

        @Throws(IOException::class)
        private fun setupLibraryPath(mcefLibrariesDir: File, jcefDirectory: File) {
            mcefLibrariesDir.mkdirs()
            System.setProperty("mcef.libraries.path", mcefLibrariesDir.getCanonicalPath())
            System.setProperty("jcef.path", jcefDirectory.getCanonicalPath())
        }

        @Throws(IOException::class)
        private fun downloadFile(urlString: String, outputFile: File) {
            MCEFLogger.logger!!.info(urlString + " -> " + outputFile.getCanonicalPath())
            val url = URL(urlString)
            val urlConnection = url.openConnection()
            val fileSize = urlConnection.getContentLength()
            val inputStream = BufferedInputStream(url.openStream())
            val outputStream = FileOutputStream(outputFile)
            var buffer = ByteArray(BUFFER_SIZE)
            var count: Int
            var readBytes = 0
            while (inputStream.read(buffer).also { count = it } != -1) {
                outputStream.write(buffer, 0, count)
                readBytes += count
                val percentComplete = readBytes.toFloat() / fileSize
                MCEFDownloadListener.percent = percentComplete
                buffer = ByteArray(max(BUFFER_SIZE, inputStream.available()))
            }
            inputStream.close()
            outputStream.close()
        }

        private fun extractTarGz(tarGzFile: File, outputDirectory: File) {
            setTask("Extracting")
            outputDirectory.mkdirs()
            val fileSize = tarGzFile.length()
            var totalBytesRead: Long = 0
            try {
                TarArchiveInputStream(GzipCompressorInputStream(FileInputStream(tarGzFile))).use { tarInput ->
                    var entry: TarArchiveEntry
                    while (tarInput.nextTarEntry.also { entry = it } != null) {
                        if (entry.isDirectory) {
                            continue
                        }
                        val outputFile = File(outputDirectory, entry.name)
                        outputFile.getParentFile().mkdirs()
                        FileOutputStream(outputFile).use { outputStream ->
                            var buffer = ByteArray(FILE_COPY_BUFFER_SIZE)
                            var bytesRead: Int
                            while (tarInput.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead.toLong()
                                val percentComplete = totalBytesRead.toFloat() / fileSize / COMPRESSION_RATIO_DIVISOR
                                MCEFDownloadListener.percent = percentComplete
                                buffer = ByteArray(max(FILE_COPY_BUFFER_SIZE, tarInput.available()))
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            MCEFDownloadListener.percent = 1f
        }
    }
}
