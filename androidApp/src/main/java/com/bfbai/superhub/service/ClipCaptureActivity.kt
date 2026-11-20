// Built with BFB.AI
package com.bfbai.superhub.service

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * شفافة وبتعيش أجزاء من الثانية: بتاخد التركيز عشان النظام يسمح بقراءة الحافظة،
 * وبتسلّم النص للخدمة وتقفل نفسها.
 */
class ClipCaptureActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // لو التركيز ما جاش، نحاول نقرا بعد ثانية ونقفل
        handler.postDelayed({ deliver() }, 1000)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) handler.postDelayed({ deliver() }, 40)
    }

    private fun deliver() {
        if (handled) return
        handled = true

        var text: String? = null
        var sensitive = false
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                sensitive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
                text = clip.getItemAt(0).coerceToText(this)?.toString()
            }
        } catch (e: Exception) {
        }

        ClipboardAccessibilityService.instance?.handleCapturedFromActivity(text, sensitive)
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
