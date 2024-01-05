package ccetl.mcef

object MIMEUtil {
    @JvmStatic
    fun mimeFromExtension(ext: String): String? {
        return when (ext) {
            "htm", "html" -> "text/html"
            "css" -> "text/css"
            "pdf" -> "application/pdf"
            "xz" -> "application/x-xz"
            "tar" -> "application/x-tar"
            "cpio" -> "application/x-cpio"
            "7z" -> "application/x-7z-compressed"
            "zip" -> "application/zip"
            "js" -> "text/javascript"
            "json" -> "application/json"
            "jsonml" -> "application/jsonml+json"
            "jar" -> "application/java-archive"
            "ser" -> "application/java-serialized-object"
            "class" -> "application/java-vm"
            "wad" -> "application/x-doom"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "xml" -> "text/xml"
            "txt" -> "text/plain"
            "oga", "ogg", "spx" -> "audio/ogg"
            "mp4", "mp4v", "mpg4" -> "video/mp4"
            "m4a", "mp4a" -> "audio/mp4"
            "mid", "midi", "kar", "rmi" -> "audio/midi"
            "mpga", "mp2", "mp2a", "mp3", "mp3a", "m2a" -> "audio/mpeg"
            "mpeg", "mpg", "mpe", "m1v", "m2v" -> "video/mpeg"
            "jpgv" -> "video/jpeg"
            "h264" -> "video/h264"
            "h261" -> "video/h261"
            "h263" -> "video/h263"
            "webm" -> "video/webm"
            "flv" -> "video/flv"
            "m4v" -> "video/m4v"
            "qt", "mov" -> "video/quicktime"
            "ogv" -> "video/ogg"
            else ->  null
        }
    }
}
