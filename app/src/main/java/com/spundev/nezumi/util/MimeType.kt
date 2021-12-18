package com.spundev.nezumi.util

object MimeType {
    /**
     * Files with the same MimeType don't always have the same extension, but for out purpose this
     * will return the most common extension for a supported MimeType
     */
    fun getExtensionFromMimeType(mimeType: String) : String {
        return when(mimeType) {
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "audio/mp4" -> "m4a"
            "audio/webm" -> "weba"
            else -> ""
        }
    }
}