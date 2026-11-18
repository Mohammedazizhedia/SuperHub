// Built with BFB.AI
package com.bfbai.superhub.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast

class ClipboardAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ClipboardAccessibilityService? = null
    }

    private lateinit var clipboardManager: ClipboardManager
    private var lastText: String? = null
    
    // تم تغييره إلى internal ليتم الوصول إليه من ShareReceiverActivity
    internal var shelf: ShelfController? = null 
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    private var lastApp: String? = null
    private var lastAppName: String = "تطبيق"
    private var lastCopyApp: String? = null
    private var ignoreClipUntil: Long = 0L

    val shelfShown: Boolean get() = shelf?.isShown == true

    fun toggleShelf(): Boolean {
        if (shelf?.isShown == true) shelf?.hide() else shelf?.show()
        return shelfShown
    }

    // دالة عامة جديدة لإضافة عنصر من خارج الخدمة (مثل ShareReceiverActivity)
    fun addToShelf(text: String, source: String) {
        shelf?.add(text, source)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("ClipboardService", "Service Connected")

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        if (shelf == null) {
            shelf = ShelfController(this) { insertIntoFocused(it) }
            shelf?.appNameProvider = { lastCopyApp ?: getAppName() }

            shelf?.onShownChanged = { shown ->
                updatePrefs(shown)
            }
            shelf?.show()
        }

        clipListener?.let { clipboardManager.removePrimaryClipChangedListener(it) }

        val l = ClipboardManager.OnPrimaryClipChangedListener {
            if (System.currentTimeMillis() < ignoreClipUntil) return@OnPrimaryClipChangedListener
            lastCopyApp = getAppName()
            try {
                val clip = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(this)?.toString()
                    if (!text.isNullOrBlank()) {
                        lastText = text
                        lastAppName = lastCopyApp ?: "تطبيق"
                        shelf?.add(text, lastAppName)
                    }
                }
            } catch (e: Exception) {
                Log.d("ClipboardService", "clip read failed: ${e.message}")
            }
        }
        clipListener = l
        clipboardManager.addPrimaryClipChangedListener(l)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "TOGGLE_SHELF" -> toggleShelf()
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
        val prefs = getSharedPreferences("HubSyncPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("shelf_visible", isVisible).apply()
    }

    private fun currentForegroundPackage(): String? {
        return try {
            val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            val w = apps.firstOrNull { it.isActive } ?: apps.firstOrNull()
            val p = w?.root?.packageName?.toString()
            if (p != null && p != packageName) p else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getAppName(): String {
        val p = currentForegroundPackage() ?: lastApp ?: return "تطبيق"
        return try {
            val appInfo = packageManager.getApplicationInfo(p, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            p.substringAfterLast('.')
        }
    }

    fun insertIntoFocused(text: String) {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (node == null) {
            Toast.makeText(this, "اضغط على خانة الكتابة الأول", Toast.LENGTH_SHORT).show()
            return
        }
        ignoreClipUntil = System.currentTimeMillis() + 1000
        clipboardManager.setPrimaryClip(ClipData.newPlainText("hubsync", text))
        lastCopyApp = null

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
        instance = null
        clipListener?.let { clipboardManager.removePrimaryClipChangedListener(it) }
        clipListener = null
        shelf?.onShownChanged = null
        shelf?.hide()
        shelf = null
        updatePrefs(false)
        super.onDestroy()
    }
}
