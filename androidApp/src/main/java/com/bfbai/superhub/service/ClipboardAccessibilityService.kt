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

        private const val MAX_ATTEMPTS = 3
    }

    private lateinit var clipboardManager: ClipboardManager
    private val handler = Handler(Looper.getMainLooper())
    private var shelf: ShelfController? = null
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    private var lastApp: String? = null
    private var lastCopyApp: String? = null
    private var lastCopyAt: Long = 0L
    private var ignoreClipUntil: Long = 0L

    private var captureBusy: Boolean = false
    private var lastCaptured: String? = null

    private val copyWords = listOf("copy", "نسخ")

    val shelfShown: Boolean get() = shelf?.isShown == true

    fun toggleShelf(): Boolean {
        if (shelf?.isShown == true) shelf?.hide() else shelf?.show()
        return shelfShown
    }

    // التقاط يدوي للحافظة (ممكن يتنادى من أي زرار أو إشعار)
    fun pasteFromShelf() {
        requestCapture(manual = true, expectChange = false)
    }

    private fun dbg(msg: String) {
        if (DEBUG) Toast.makeText(this, "HubSync: $msg", Toast.LENGTH_SHORT).show()
    }

    // اسم مصدر النسخ: آخر تطبيق ضغطت فيه نسخ (لو كان من أقل من دقيقة)، وإلا التطبيق اللي قدامك
    private fun sourceName(): String {
        val c = lastCopyApp
        if (c != null && SystemClock.uptimeMillis() - lastCopyAt < 60_000) return c
        return getAppName()
    }

    private fun markCopy(app: String) {
        lastCopyApp = app
        lastCopyAt = SystemClock.uptimeMillis()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("ClipboardService", "Service Connected")

        HistoryRepository.init(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        if (shelf == null) {
            shelf = ShelfController(this) { insertIntoFocused(it) }
            shelf?.appNameProvider = { sourceName() }
            shelf?.onShownChanged = { shown ->
                updatePrefs(shown)
                SuperHubTileService.live?.refresh()
                TileService.requestListeningState(
                    this, ComponentName(this, SuperHubTileService::class.java)
                )
            }
            // لما تدوس على مقبض الرف: نقرا الحافظة (المسار اليدوي المضمون)
            shelf?.onOpenedByUser = {
                requestCapture(manual = true, expectChange = false)
            }
            shelf?.show()
        }

        clipListener?.let { clipboardManager.removePrimaryClipChangedListener(it) }

        val l = ClipboardManager.OnPrimaryClipChangedListener {
            val until = maxOf(ignoreClipUntil, HistoryRepository.ignoreClipUntil)
            if (System.currentTimeMillis() < until) return@OnPrimaryClipChangedListener
            dbg("النظام بلّغني بنسخ")
            markCopy(getAppName())
            if (!readDirect()) requestCapture(manual = false, expectChange = false)
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
                if (sensitive) {
                    dbg("محتوى حساس، اتجاهلته")
                    return true
                }
                if (!text.isNullOrBlank()) {
                    acceptText(text)
                    return true
                }
            }
        } catch (e: Exception) {
        }
        return false
    }

    private fun acceptText(text: String) {
        lastCaptured = text
        shelf?.add(text, sourceName())
        dbg("اتسجل من ${sourceName()}")
    }

    /**
     * الالتقاط بالتركيز.
     * expectChange = true معناها: ضغطة نسخ اتشافت، فلو الحافظة لسه فيها النص القديم
     * بنستنى ونقرا تاني (بعض التطبيقات بتتأخر في تحديث الحافظة).
     */
    private fun requestCapture(manual: Boolean, expectChange: Boolean) {
        if (captureBusy) return
        val s = shelf ?: return
        captureBusy = true
        // أمان: لو حصل أي حاجة غريبة نفك القفل بعد 6 ثواني
        handler.postDelayed({ captureBusy = false }, 6000)
        captureLoop(s, 0, lastCaptured, manual, expectChange)
    }

    private fun captureLoop(
        s: ShelfController,
        attempt: Int,
        baseline: String?,
        manual: Boolean,
        expectChange: Boolean
    ) {
        s.captureClipboard { text, sensitive ->
            if (sensitive) {
                captureBusy = false
                dbg("محتوى حساس، اتجاهلته")
                return@captureClipboard
            }
            val t = text
            val empty = t.isNullOrBlank()
            val unchanged = expectChange && !empty && t == baseline
            if ((empty || unchanged) && attempt < MAX_ATTEMPTS) {
                handler.postDelayed(
                    { captureLoop(s, attempt + 1, baseline, manual, expectChange) },
                    350
                )
            } else {
                captureBusy = false
                if (t.isNullOrBlank()) {
                    dbg("الحافظة فاضية أو النظام منع القراءة")
                    if (manual) {
                        Toast.makeText(this, "الحافظة فاضية أو النظام منع القراءة", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    acceptText(t)
                }
            }
        }
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
                    markCopy(labelFor(pkg))
                    dbg("ضغطة نسخ في ${lastCopyApp}")
                    // التطبيق محتاج لحظة يحط النص في الحافظة قبل ما نقراه
                    handler.postDelayed({ requestCapture(manual = false, expectChange = true) }, 150)
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
