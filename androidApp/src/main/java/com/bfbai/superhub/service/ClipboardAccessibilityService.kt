// Built with BFB.AI
package com.bfbai.superhub.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.bfbai.superhub.sync.ClipboardItem
import com.bfbai.superhub.sync.SyncManager

class ClipboardAccessibilityService : AccessibilityService() {

    private lateinit var clipboardManager: ClipboardManager
    private var lastText: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        clipboardManager.addPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (!text.isNullOrEmpty() && text != lastText) {
                    lastText = text
                    Log.d("ClipboardService", "Copied: $text")
                    
                    // إنشاء عنصر جديد وإضافته للقائمة
                    val newItem = ClipboardItem(text = text)
                    SyncManager.addToHistory(newItem)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
