// Built with BFB.AI
package com.bfbai.superhub

import kotlinx.serialization.Serializable

@Serializable
data class SyncMessage(
    val type: String = "clipboard",
    val content: String,
    val deviceId: String,
    val timestamp: Long = System.currentTimeMillis()
)
