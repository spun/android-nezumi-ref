package com.spundev.nezumi.model

import kotlinx.serialization.Serializable

@Serializable
data class VideoDetails(
    val videoId: String,
    val title: String,
    val lengthSeconds: String,
    val author: String
)

@Serializable
data class Format(val itag: Int, val url: String, val quality: String) {
    val description: String
        get() = formats[itag] ?: "Unknown ($itag)"
}

@Serializable
data class StreamingData(val formats: List<Format>, val adaptiveFormats: List<Format>)

@Serializable
data class PlayerResponse(val streamingData: StreamingData, val videoDetails: VideoDetails)