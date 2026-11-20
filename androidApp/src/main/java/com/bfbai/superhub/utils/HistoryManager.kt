// Built with BFB.AI
package com.bfbai.superhub.utils

import android.content.Context
import com.bfbai.superhub.service.ShelfItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class HistoryManager(context: Context) {

    private val dir: File = context.applicationContext.filesDir
    private val file: File = File(dir, FILE_NAME)
    private val gson = Gson()

    companion object {
        private const val FILE_NAME = "hubsync_history.json"
        private const val CORRUPT_NAME = "hubsync_history.corrupt.json"

        // قفل واحد لكل النسخ، عشان مفيش حفظتين يتداخلوا
        private val lock = Mutex()
    }

    // الحفظ بيتم في ملف مؤقت وبعدين بيتبدل، فلو الكتابة اتقطعت الملف الأصلي سليم
    suspend fun saveHistory(items: List<ShelfItem>) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                try {
                    val tmp = File(dir, "$FILE_NAME.tmp")
                    tmp.writeText(gson.toJson(items))
                    if (!tmp.renameTo(file)) {
                        file.delete()
                        tmp.renameTo(file)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun loadHistory(): List<ShelfItem> {
        return withContext(Dispatchers.IO) {
            lock.withLock {
                if (!file.exists()) {
                    emptyList()
                } else {
                    try {
                        val type = object : TypeToken<List<ShelfItem>>() {}.type
                        val list: List<ShelfItem>? = gson.fromJson(file.readText(), type)
                        list ?: emptyList()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // الملف تالف: نحتفظ بنسخة منه قبل ما أي حفظ جديد يكتب فوقه
                        try {
                            file.copyTo(File(dir, CORRUPT_NAME), overwrite = true)
                        } catch (_: Exception) {
                        }
                        emptyList()
                    }
                }
            }
        }
    }

    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            lock.withLock {
                try {
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
