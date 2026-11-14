package com.bfbai.superhub
import kotlinx.serialization.Serializable

@Serializable
data class SyncMessage(
    val type: String,
    val content: String,
    val deviceId: String,
    val timestamp: Long
)
