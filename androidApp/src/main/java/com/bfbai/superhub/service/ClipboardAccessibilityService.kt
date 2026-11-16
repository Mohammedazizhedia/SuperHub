// Built with BFB.AI
package com.bfbai.superhub.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.bfbai.superhub.sync.SyncManager

class ClipboardAccessibilityService : AccessibilityService() {

    private lateinit var clipboardManager: ClipboardManager
    private var lastText: String? = null
    private var shelf: ShelfController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        // تشغيل الرف الذكي
        shelf = ShelfController(this) { text ->
            insertIntoFocused(text)
        }
        shelf?.show()

        // مراقبة الحافظة
        clipboardManager.addPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (!text.isNullOrEmpty() && text != lastText) {
                    lastText = text
                    Log.d("ClipboardService", "Copied: $text")
                    
                    // إضافة النص للرف والسجل
                    shelf?.add(text)
                    SyncManager.addToHistory(text)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        shelf?.hide()
        super.onDestroy()
    }

    // دالة الإدراج في خانة الكتابة المفتوحة
    private fun insertIntoFocused(text: String) {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("shelf", text))
        
        // نجرب ACTION_PASTE الأول، لو فشل نجرب ACTION_SET_TEXT
        val success = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        if (!success) {
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }
}
