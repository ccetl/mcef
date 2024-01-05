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

import org.cef.callback.CefCallback
import org.cef.handler.CefResourceHandler
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.io.IOException
import java.io.InputStream
import java.util.*

// https://github.com/CinemaMod/mcef/blob/master-1.19.2/src/main/java/net/montoyo/mcef/example/ModScheme.java
class ModScheme(private var url: String): CefResourceHandler {

    private var contentType: String? = null
    private var inputStream: InputStream? = null

    @Override
    override fun processRequest(cefRequest: CefRequest, cefCallback: CefCallback): Boolean {
        val url2: String = this.url.substring("mod://".length)

        var pos = url.indexOf('/')
        if (pos < 0) {
            cefCallback.cancel()
            return false
        }

        val mod = removeSlashes(url2.substring(0, pos))
        val loc = removeSlashes(url2.substring(pos + 1))

        if (mod.isEmpty() || loc.isEmpty() || mod[0] == '.' || loc[0] == '.') {
            MCEFLogger.logger?.warn("Invalid URL $url2")
            cefCallback.cancel()
            return false
        }

        inputStream = ModScheme::class.java.getResourceAsStream("/assets/" + mod.lowercase(Locale.ROOT) + "/html/" + loc.lowercase(Locale.ROOT))
        if (inputStream == null) {
            MCEFLogger.logger?.warn("Resource $url2 NOT found!")
            cefCallback.cancel()
            return false // TODO: 404?
        }

        contentType = null
        pos = loc.lastIndexOf('.')
        if (pos >= 0 && pos < loc.length - 2) {
            contentType = MIMEUtil.mimeFromExtension(loc.substring(pos + 1))
        }

        cefCallback.Continue()
        return true
    }

    private fun removeSlashes(loc: String): String {
        var i = 0
        while (i < loc.length && loc[i] == '/')
            i++

        return loc.substring(i)
    }

    @Override
    override fun getResponseHeaders(cefResponse: CefResponse, contentLength: IntRef, redir: StringRef) {
        if (contentType != null) {
            cefResponse.setMimeType(contentType)
        }

        cefResponse.setStatus(200)
        cefResponse.setStatusText("OK")
        contentLength.set(0)
    }

    @Override
    override fun readResponse(vararg output: Byte, bytesToRead: Int, bytesRead: IntRef, cefCallback: CefCallback): Boolean {
        try {
            val ret = inputStream!!.read(output, 0, bytesToRead)
            if (ret <= 0) {
                inputStream!!.close()
                // 0 bytes read indicates to CEF/JCEF that there is no more data to read
                bytesRead.set(0)
                return false
            }

            // tell CEF/JCEF how many bytes were read
            bytesRead.set(ret)
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            // attempt to close the stream if possible
            try {
                inputStream?.close()
            } catch (ignored: Throwable) {
            }

            return false
        }
    }

    override fun cancel() {
        // attempt to free resources, just incase
        try {
            inputStream?.close()
        } catch (ignored: Throwable) {
        }
    }
}
