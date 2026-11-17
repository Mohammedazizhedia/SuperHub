// Built with BFB.AI
package com.bfbai.superhub.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.bfbai.superhub.sync.SyncManager

class ClipboardAccessibilityService : AccessibilityService() {

    private lateinit var clipboardManager: ClipboardManager
    private var lastText: String? = null
    private var shelf: ShelfController? = null
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ClipboardService", "Service Connected")
        
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        // تشغيل الرف العائم
        if (shelf == null) {
            shelf = ShelfController(this) { insertIntoFocused(it) }
            shelf?.show()
        }

        clipListener?.let { clipboardManager.removePrimaryClipChangedListener(it) }
        
        // مراقبة الحافظة (النسخ العادي)
        val l = ClipboardManager.OnPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (!text.isNullOrEmpty() && text != lastText) {
                    lastText = text
                    Log.d("ClipboardService", "Copied: $text")
                    
                    // إضافة النص للرف مع اسم التطبيق
                    shelf?.add(text, getAppName())
                }
            }
        }
        clipListener = l
        clipboardManager.addPrimaryClipChangedListener(l)
    }

    // دالة لمعرفة اسم التطبيق اللي بنسخ منه
    private fun getAppName(): String {
        val packageName: String? = rootInActiveWindow?.packageName?.toString()
        if (packageName == null || packageName == "com.bfbai.superhub") {
            return "تطبيق"
        }
        // بنجيب آخر جزء من اسم الباكدج ونكبر أول حرف (مثلاً whatsapp -> Whatsapp)
        return packageName.split(".").last().replaceFirstChar { it.uppercase() }
    }

    // دالة اللصق (لما تدوس على الكارت في الرف)
    fun insertIntoFocused(text: String) {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        lastText = text 
        clipboardManager.setPrimaryClip(ClipData.newPlainText("shelf", text))
        
        if (!node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            val old = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
            val s = minOf(node.textSelectionStart.coerceAtLeast(0), old.length)
            val e = minOf(node.textSelectionEnd.coerceAtLeast(s), old.length)
            val merged = old.substring(0, s) + text + old.substring(e)
            
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, merged)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    
    override fun onInterrupt() {}

    override fun onDestroy() {
        Log.d("ClipboardService", "Service Destroyed")
        clipListener?.let { clipboardManager.removePrimaryClipChangedListener(it) }
        clipListener = null
        
        shelf?.hide()
        shelf = null
        super.onDestroy()
    }
}
