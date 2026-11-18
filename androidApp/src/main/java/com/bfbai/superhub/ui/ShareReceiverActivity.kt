// Built with BFB.AI
package com.bfbai.superhub.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bfbai.superhub.service.ClipboardAccessibilityService
import com.bfbai.superhub.service.ShelfItem
import com.bfbai.superhub.utils.HistoryManager
import java.io.File
import java.io.FileOutputStream

class ShareReceiverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // جعل النافذة شفافة تماماً حتى لا يلاحظ المستخدم أي وميض
        window.setBackgroundDrawableResource(android.R.color.transparent)
        handleIntent(intent)
        finish() // إغلاق النشاط فوراً بعد المعالجة
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if (type.startsWith("text/")) {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    addToShelf(text, "مشاركة نص")
                }
            } else if (type.startsWith("image/")) {
                val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (imageUri != null) {
                    saveImageAndAddToShelf(imageUri)
                }
            }
        }
    }

    private fun addToShelf(text: String, source: String) {
        val service = ClipboardAccessibilityService.instance
        if (service != null) {
            service.shelf?.add(text, source)
        } else {
            // إذا كانت الخدمة غير نشطة، نحفظها مباشرة في السجل
            val historyManager = HistoryManager(this)
            val items = historyManager.loadHistory().toMutableList()
            items.add(0, ShelfItem(text, source))
            historyManager.saveHistory(items)
            Toast.makeText(this, "تم الحفظ في HubSync", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageAndAddToShelf(uri: Uri) {
        try {
            val fileName = "IMG_${System.currentTimeMillis()}.jpg"
            val directory = File(getExternalFilesDir(null), "HubSyncImages")
            if (!directory.exists()) directory.mkdirs()
            
            val file = File(directory, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            addToShelf("[صورة محفوظة: $fileName]", "صورة مشاركة")
        } catch (e: Exception) {
            Toast.makeText(this, "فشل حفظ الصورة", Toast.LENGTH_SHORT).show()
        }
    }
}
