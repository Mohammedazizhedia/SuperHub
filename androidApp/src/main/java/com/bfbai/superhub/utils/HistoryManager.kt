// Built with BFB.AI
package com.bfbai.superhub.utils

import android.content.Context
import com.bfbai.superhub.service.ShelfItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class HistoryManager(private val context: Context) {
    private val gson = Gson()
    private val fileName = "hubsync_history.json"

    fun saveHistory(items: List<ShelfItem>) {
        try {
            val file = File(context.filesDir, fileName)
            val json = gson.toJson(items)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadHistory(): List<ShelfItem> {
        return try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<List<ShelfItem>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun clearHistory() {
        try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
