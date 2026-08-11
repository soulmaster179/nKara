package com.ntech.nkara.core.model

import java.util.UUID

data class Song(
    val queueId: String = UUID.randomUUID().toString(),
    val videoId: String,
    val title: String = "YouTube: $videoId",
)
