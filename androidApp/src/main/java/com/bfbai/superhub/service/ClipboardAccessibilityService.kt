// Built with BFB.AI
package com.bfbai.superhub.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.quicksettings.TileService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.bfbai.superhub.utils.HistoryRepository

class ClipboardAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ClipboardAccessibilityService? = null

        // رسائل التشخيص المؤقتة: خليها false بعد ما نتأكد إن النسخ شغال
        private const val DEBUG = true
    }

    private lateinit var clipboardManager: ClipboardManager
    private val handler = Handler(Looper.getMainLooper())
    private var shelf: ShelfController? = null
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    private var lastApp: String? = null
    private var lastCopyApp: String? = null
    private var ignoreClipUntil: Long = 0L

    private var captureBusy: Boolean = false
    private var lastCaptured: String? = null
    private var lastCapturedAt: Long = 0L

    private val copyWords = listOf("copy", "نسخ")

    val shelfShown: Boolean get() = shelf?.isShown == true

    fun toggleShelf(): Boolean {
        if (shelf?.isShown == true) shelf?.hide() else shelf?.show()
        return shelfShown
    }

    // التقاط يدوي للحافظة (ممكن يتنادى من أي زرار أو إشعار)
    fun pasteFromShelf() {
        lastCopyApp = getAppName()
        requestCapture(manual = true)
    }

    private fun dbg(msg: String) {
        if (DEBUG) Toast.makeText(this, "HubSync: $msg", Toast.LENGTH_SHORT).show()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("ClipboardService", "Service Connected")

        HistoryRepository.init(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        if (shelf == null) {
            shelf = ShelfController(this) { insertIntoFocused(it) }
            shelf?.appNameProvider = { lastCopyApp ?: getAppName() }
            shelf?.onShownChanged = { shown ->
                updatePrefs(shown)
                SuperHubTileService.live?.refresh()
                TileService.requestListeningState(
                    this, ComponentName(this, SuperHubTileService::class.java)
                )
            }
            // لما تدوس على مقبض الرف: نقرا الحافظة (المسار اليدوي المضمون)
            shelf?.onOpenedByUser = {
                lastCopyApp = getAppName()
                requestCapture(manual = true)
            }
            shelf?.show()
        }

        clipListener?.let { clipboardManager.removePrimaryClipChangedListener(it) }

        val l = ClipboardManager.OnPrimaryClipChangedListener {
            val until = maxOf(ignoreClipUntil, HistoryRepository.ignoreClipUntil)
            if (System.currentTimeMillis() < until) return@OnPrimaryClipChangedListener
            dbg("النظام بلّغني بنسخ")
            if (lastCopyApp == null) lastCopyApp = getAppName()
            if (!readDirect()) requestCapture(manual = false)
        }
        clipListener = l
        clipboardManager.addPrimaryClipChangedListener(l)
    }

    // قراءة عادية بدون تركيز: بترجع true لو اتقرت
    private fun readDirect(): Boolean {
        try {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val sensitive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
                val text = clip.getItemAt(0).coerceToText(this)?.toString()
                if (sensitive || !text.isNullOrBlank()) {
                    onCaptured(text, sensitive, false)
                    return true
                }
            }
        } catch (e: Exception) {
        }
        return false
    }

    // الالتقاط بالتركيز: نافذة الرف بتبقى قابلة للتركيز لحظة وبتقرا الحافظة
    private fun requestCapture(manual: Boolean) {
        if (captureBusy) return
        val s = shelf
        if (s == null) return
        captureBusy = true
        handler.postDelayed({ captureBusy = false }, 3000)
        s.captureClipboard { text, sensitive ->
            captureBusy = false
            onCaptured(text, sensitive, manual)
        }
    }

    private fun onCaptured(text: String?, sensitive: Boolean, manual: Boolean) {
        if (sensitive) {
            dbg("محتوى حساس، اتجاهلته")
            return
        }
        if (text.isNullOrBlank()) {
            dbg("الحافظة فاضية أو النظام منع القراءة")
            if (manual) {
                Toast.makeText(this, "الحافظة فاضية أو النظام منع القراءة", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val now = SystemClock.uptimeMillis()
        val duplicate = text == lastCaptured && (manual || now - lastCapturedAt < 5000)
        if (duplicate) {
            dbg("نفس النص، اتجاهلته")
            return
        }
        lastCaptured = text
        lastCapturedAt = now
        shelf?.add(text, lastCopyApp ?: getAppName())
        dbg("اتسجل من ${lastCopyApp ?: "تطبيق"}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val p = event.packageName?.toString() ?: return
                if (p != packageName && p != "com.android.systemui" &&
                    !p.contains("inputmethod") && !p.contains("keyboard")) {
                    lastApp = p
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val pkg = event.packageName?.toString()
                if (pkg == null || pkg == packageName) return
                if (looksLikeCopy(event)) {
                    lastCopyApp = labelFor(pkg)
                    dbg("ضغطة نسخ في $lastCopyApp")
                    // التطبيق محتاج لحظة يحط النص في الحافظة قبل ما نقراه
                    handler.postDelayed({ requestCapture(manual = false) }, 150)
                }
            }
        }
    }

    // هل الضغطة كانت على زرار اسمه "نسخ" أو "Copy"؟
    private fun looksLikeCopy(e: AccessibilityEvent): Boolean {
        val parts = ArrayList<String>()
        try {
            e.text?.forEach { parts.add(it.toString()) }
            e.contentDescription?.let { parts.add(it.toString()) }
            val n = e.source
            if (n != null) {
                n.text?.let { parts.add(it.toString()) }
                n.contentDescription?.let { parts.add(it.toString()) }
                n.viewIdResourceName?.let { parts.add(it) }
            }
        } catch (ex: Exception) {
        }
        val joined = parts.joinToString(" ").lowercase()
        // نص طويل معناه رسالة مش زرار
        if (joined.isEmpty() || joined.length > 60) return false
        return copyWords.any { joined.contains(it) }
    }

    private fun updatePrefs(isVisible: Boolean) {
        val prefs = getSharedPreferences("SuperHubPrefs", Context.MODE_PRIVATE)
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

    private fun labelFor(p: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString()
        } catch (e: Exception) {
            p.substringAfterLast('.')
        }
    }

    private fun getAppName(): String {
        val p = currentForegroundPackage() ?: lastApp ?: return "تطبيق"
        return labelFor(p)
    }

    fun insertIntoFocused(text: String) {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (node == null) {
            Toast.makeText(this, "اضغط على خانة الكتابة الأول", Toast.LENGTH_SHORT).show()
            return
        }
        ignoreClipUntil = System.currentTimeMillis() + 1000
        // الحافظة هتبقى فيها النص ده، فنعتبره "اتلقط" عشان ما يترجعش للرف تاني
        lastCaptured = text
        lastCapturedAt = SystemClock.uptimeMillis()
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
        instance = null
        handler.removeCallbacksAndMessages(null)
        clipListener?.let { clipboardManager.removePrimaryClipChangedListener(it) }
        clipListener = null
        shelf?.destroy()
        shelf = null
        updatePrefs(false)
        super.onDestroy()
    }
}
