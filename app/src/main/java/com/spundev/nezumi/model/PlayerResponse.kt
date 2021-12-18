package com.spundev.nezumi.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoDetails(
    val videoId: String,
    val title: String,
    val lengthSeconds: String,
    val author: String
)

@Serializable
data class Format(
    val itag: Int,
    val url: String,
    val quality: String,
    @SerialName("mimeType") val mimeTypeRaw: String
) {
    val description: String
        get() = formats[itag] ?: "Unknown ($itag)"

    val mimeType: String?
        get() = mimeTypeRaw.split(";").let { splits ->
            if (splits.isNotEmpty()) splits[0] else null
        }
}

@Serializable
data class StreamingData(val formats: List<Format>, val adaptiveFormats: List<Format>)

@Serializable
data class PlayerResponse(val streamingData: StreamingData, val videoDetails: VideoDetails)