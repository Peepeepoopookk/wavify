package com.example.model

import com.squareup.moshi.Json

data class Artist(
    val name: String,
    val track_count: Int,
    val cover_image: String? = null
)
