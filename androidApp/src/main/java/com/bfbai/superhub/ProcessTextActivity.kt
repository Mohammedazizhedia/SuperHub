// Built with BFB.AI
package com.bfbai.superhub

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            if (!text.isNullOrEmpty()) {
                // نسخ النص للحافظة عشان الرف يلتقطه
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("shelf", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "تم النسخ إلى SuperHub", Toast.LENGTH_SHORT).show()
            }
        }
        
        // نقفل الشاشة فوراً ونرجعنا للتطبيق الأصلي
        finish()
    }
}
