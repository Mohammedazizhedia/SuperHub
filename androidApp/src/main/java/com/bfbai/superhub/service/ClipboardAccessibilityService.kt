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
        Log.d("ClipboardService", "Service Connected")
        
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        if (shelf == null) {
            shelf = ShelfController(this) { insertIntoFocused(it) }
            shelf?.show()
        }

        clipboardManager.addPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (!text.isNullOrEmpty() && text != lastText) {
                    lastText = text
                    Log.d("ClipboardService", "Copied: $text")
                    
                    SyncManager.addToHistory(text)
                    // بنبعت النص للرف مع اسم التطبيق
                    shelf?.add(text, getAppName())
                }
            }
        }
    }

    // طريقة أسهل لمعرفة التطبيق اللي في المقدمة
    private fun getAppName(): String {
        var packageName: String? = rootInActiveWindow?.packageName?.toString()
        if (packageName == null || packageName == "com.bfbai.superhub") {
            return "تطبيق"
        }
        // بناخد آخر جزء من اسم الحزمة (مثلاً com.whatsapp يبقى whatsapp)
        return packageName.split(".").last().replaceFirstChar { it.uppercase() }
    }

    fun insertIntoFocused(text: String) {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        clipboardManager.setPrimaryClip(ClipData.newPlainText("shelf", text))
        node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        Log.d("ClipboardService", "Service Destroyed")
        shelf?.hide()
        shelf = null
        super.onDestroy()
    }
}
