// Built with BFB.AI
package com.bfbai.superhub.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.quicksettings.TileService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ClipboardAccessibilityService : AccessibilityService() {

    private lateinit var clipboardManager: ClipboardManager
    private var lastText: String? = null
    private var shelf: ShelfController? = null
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    
    private var lastApp: String? = null 
    private var lastAppName: String = "تطبيق" 

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ClipboardService", "Service Connected")
        
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        if (shelf == null) {
            shelf = ShelfController(this) { insertIntoFocused(it) }
            
            // ربط الستارة بالخدمة لتحديث اللون باستخدام requestListeningState
            shelf?.onShownChanged = { shown ->
                updatePrefs(shown)
                TileService.requestListeningState(
                    this, ComponentName(this, SuperHubTileService::class.java)
                )
            }
            shelf?.show()
        }

        clipListener?.let { clipboardManager.removePrimaryClipChangedListener(it) }
        
        val l = ClipboardManager.OnPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (!text.isNullOrEmpty() && text != lastText) {
                    lastText = text
                    lastAppName = getAppName()
                    shelf?.add(text, lastAppName)
                }
            }
        }
        clipListener = l
        clipboardManager.addPrimaryClipChangedListener(l)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "TOGGLE_SHELF" -> {
                if (shelf?.isShown == true) {
                    shelf?.hide()
                } else {
                    shelf?.show()
                }
            }
            "PASTE_FROM_SHELF" -> {
                // التعديل: استدعاء الدالة الجديدة من الستارة
                shelf?.pasteFromClipboard()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val p = event.packageName?.toString() ?: return
            if (p != packageName && p != "com.android.systemui" &&
                !p.contains("inputmethod") && !p.contains("keyboard")) {
                lastApp = p
            }
        }
    }

    private fun updatePrefs(isVisible: Boolean) {
        val prefs = getSharedPreferences("SuperHubPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("shelf_visible", isVisible).apply()
    }

    private fun getAppName(): String {
        val p = lastApp ?: return "تطبيق"
        return try {
            val appInfo = packageManager.getApplicationInfo(p, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            p.substringAfterLast('.')
        }
    }

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

    override fun onInterrupt() {}

    override fun onDestroy() {
        // التعديل: إلغاء الربط قبل التدمير لمنع الكراش
        clipListener?.let { clipboardManager.removePrimaryClipChangedListener(it) }
        clipListener = null
        shelf?.onShownChanged = null
        shelf?.hide()
        shelf = null
        updatePrefs(false)
        super.onDestroy()
    }
}
