// Built with BFB.AI
package com.bfbai.superhub.utils

import android.content.Context
import com.bfbai.superhub.service.ShelfItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class HistoryManager(private val context: Context) {
    private val gson = Gson()
    private val fileName = "hubsync_history.json"

    // حفظ القائمة في الخلفية (IO Dispatcher)
    suspend fun saveHistory(items: List<ShelfItem>) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, fileName)
                val json = gson.toJson(items)
                file.writeText(json)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // تحميل القائمة من الخلفية وإرجاعها
    suspend fun loadHistory(): List<ShelfItem> {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, fileName)
                if (file.exists()) {
                    val json = file.readThreadSafe() // أو file.readText()
                    val type = object : TypeToken<List<ShelfItem>>() {}.type
                    gson.fromJson<List<ShelfItem>>(json, type) ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    // مسح السجل في الخلفية
    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, fileName)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

// مساعدة لقراءة الملف بشكل آمن
private fun File.readThreadSafe(): String = this.readText()
 
