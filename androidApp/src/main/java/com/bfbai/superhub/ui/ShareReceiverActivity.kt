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

// رسائل التشخيص المؤقتة
private const val SHARE_DEBUG = true

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
                var u = streamUri(i)
                if (u == null) {
                    val cd = i.clipData
                    if (cd != null && cd.itemCount > 0) u = cd.getItemAt(0).uri
                }
                if (u != null && isImage(i.type, u)) {
                    uris.add(u)
                } else {
                    text = i.getStringExtra(Intent.EXTRA_TEXT)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                uris.addAll(streamUris(i))
                if (uris.isEmpty()) {
                    val cd = i.clipData
                    if (cd != null) {
                        for (k in 0 until cd.itemCount) {
                            val u = cd.getItemAt(k).uri
                            if (u != null) uris.add(u)
                        }
                    }
                }
            }
        }

        if (SHARE_DEBUG) {
            Toast.makeText(
                this,
                "HubSync: استلمت مشاركة | ${i.action?.substringAfterLast('.')} | ${i.type} | صور=${uris.size}",
                Toast.LENGTH_LONG
            ).show()
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
            val why = ImageStore.lastError ?: "مفيش نص ولا صورة في المشاركة"
            Toast.makeText(this, "مقدرتش أحفظ المشاركة: $why", Toast.LENGTH_LONG).show()
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
