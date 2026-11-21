// Built with BFB.AI
package com.bfbai.superhub.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.bfbai.superhub.service.ClipboardAccessibilityService
import com.bfbai.superhub.utils.HistoryRepository
import com.bfbai.superhub.utils.ImageStore

class ShareReceiverActivity : Activity() {

    private var pending = 0
    private var saved = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HistoryRepository.init(this)

        val i: Intent? = intent
        if (i == null) {
            finish()
            return
        }

        val src = sourceName()
        val uris = ArrayList<Uri>()
        var text: String? = null

        when (i.action) {
            Intent.ACTION_SEND -> {
                val u = streamUri(i)
                if (u != null && isImage(i.type, u)) {
                    uris.add(u)
                } else {
                    text = i.getStringExtra(Intent.EXTRA_TEXT)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> uris.addAll(streamUris(i))
        }

        val t = text
        if (!t.isNullOrBlank()) {
            HistoryRepository.add(t, src)
            saved++
        }

        // الصور بتتحفظ في خيط تاني، والـ Activity بتفضل عايشة لحد ما تخلص (عشان صلاحية القراءة)
        for (u in uris) {
            pending++
            Thread {
                val path = ImageStore.saveFromUri(this, u)
                runOnUiThread {
                    if (path != null) {
                        HistoryRepository.addImage(path, src)
                        saved++
                    }
                    pending--
                    if (pending == 0) finishUp()
                }
            }.start()
        }

        if (pending == 0) finishUp()
    }

    private fun finishUp() {
        if (saved > 0) {
            Toast.makeText(this, "اتحفظ في HubSync", Toast.LENGTH_SHORT).show()
            ClipboardAccessibilityService.instance?.popShelf()
        } else {
            Toast.makeText(this, "مقدرتش أحفظ المشاركة (الملف مش صورة أو مش مقروء)", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun isImage(type: String?, u: Uri): Boolean {
        if (type != null && type.startsWith("image/")) return true
        return try {
            contentResolver.getType(u)?.startsWith("image/") == true
        } catch (e: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun streamUri(i: Intent): Uri? {
        return try {
            i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun streamUris(i: Intent): List<Uri> {
        return try {
            i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun sourceName(): String {
        val pkg: String? = referrer?.host
        if (pkg.isNullOrBlank() || pkg == packageName) return "مشاركة"
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            "مشاركة"
        }
    }
}
