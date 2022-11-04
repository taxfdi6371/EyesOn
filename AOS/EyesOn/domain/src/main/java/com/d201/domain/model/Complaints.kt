package com.d201.domain.model

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime

data class Complaints(
    val seq: Long,
    val address: String?,
    val content: String?,
    val image: String?,
    val regTime: String?,
    val resultContent: String?,
    var returnContent: String?,
    val state: String?,
    val title: String?
)