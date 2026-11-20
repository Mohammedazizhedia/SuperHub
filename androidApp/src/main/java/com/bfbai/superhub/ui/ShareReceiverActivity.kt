// Built with BFB.AI
package com.bfbai.superhub.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.bfbai.superhub.utils.HistoryRepository

class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HistoryRepository.init(this)

        val i: Intent? = intent
        val text: String? =
            if (i?.action == Intent.ACTION_SEND && i.type?.startsWith("text/") == true) {
                i.getStringExtra(Intent.EXTRA_TEXT)
            } else {
                null
            }

        if (text.isNullOrBlank()) {
            Toast.makeText(this, "مفيش نص أحفظه (الصور لسه مش مدعومة)", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        HistoryRepository.add(text, sourceName())
        Toast.makeText(this, "اتحفظ في HubSync", Toast.LENGTH_SHORT).show()

        // تأخير بسيط عشان الحفظ في الملف يلحق يخلص قبل ما العملية تتقفل
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 600)
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
