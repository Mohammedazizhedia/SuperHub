// Built with BFB.AI
package com.bfbai.superhub

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("shelf", text)
                clipboard.setPrimaryClip(clip)
                
                // إرسال أمر للخدمة عشان تفتح الرف وتاخد النص
                val serviceIntent = Intent(this, com.bfbai.superhub.service.ClipboardAccessibilityService::class.java)
                serviceIntent.action = "ADD_FROM_PROCESS_TEXT"
                startService(serviceIntent)
                
                Toast.makeText(this, "تم النسخ إلى SuperHub", Toast.LENGTH_SHORT).show()
            }
        }
        
        // نقفل الشاشة فوراً ونرجعنا للتطبيق الأصلي
        finish()
    }
}
