// Built with BFB.AI
package com.bfbai.superhub.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

    private var lastRealApp: String? = null
    private var lastCopyApp: String? = null
    private var lastCopyAt: Long = 0L
    private var ignoreClipUntil: Long = 0L

    private var captureBusy: Boolean = false
    private var lastCaptured: String? = null

    // آخر تطبيق حصل فيه تحديد نص (لو خرجت منه بعدها، بنفحص الحافظة)
    private var selectionPkg: String? = null
    private var selectionAt: Long = 0L

    private val copyWords = listOf("copy", "نسخ")

    private val homePackage: String? by lazy {
        try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }

    val shelfShown: Boolean get() = shelf?.isShown == true

    fun toggleShelf(): Boolean {
        if (shelf?.isShown == true) shelf?.hide() else shelf?.show()
        return shelfShown
    }

    // بتتنادى من شاشة المشاركة بعد ما تحفظ حاجة
    fun popShelf() {
        shelf?.popOpen()
    }

    // التقاط يدوي للحافظة (ممكن يتنادى من أي زرار أو إشعار)
    fun pasteFromShelf() {
        requestCapture(manual = true, expectChange = false, acceptUnchanged = true, maxAttempts = 2)
    }

    private fun dbg(msg: String) {
        if (DEBUG) Toast.makeText(this, "HubSync: $msg", Toast.LENGTH_SHORT).show()
    }

    // ---------- تمييز التطبيقات ----------

    private fun isSystemNoise(p: String): Boolean =
        p == packageName || p == "com.android.systemui" ||
            p.contains("inputmethod") || p.contains("keyboard")

    private fun isLauncher(p: String): Boolean =
        p.contains("launcher") || (homePackage != null && p == homePackage)

    private fun isIgnored(p: String): Boolean = isSystemNoise(p) || isLauncher(p)

    private fun labelFor(p: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString()
        } catch (e: Exception) {
            p.substringAfterLast('.')
        }
    }

    private fun currentForegroundPackage(): String? {
        return try {
            val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            val w = apps.firstOrNull { it.isActive } ?: apps.firstOrNull()
            val p = w?.root?.packageName?.toString()
            if (p != null && !isIgnored(p)) p else null
        } catch (e: Exception) {
            null
        }
    }

    // اسم مصدر النسخ: آخر تطبيق ضغطت فيه نسخ (لو كان من أقل من دقيقة)،
    // وإلا التطبيق اللي قدامك، وإلا آخر تطبيق حقيقي كنت فيه (مش اللانشر)
    private fun sourceName(): String {
        val c = lastCopyApp
        if (c != null && SystemClock.uptimeMillis() - lastCopyAt < 60_000) return c
        val p = currentForegroundPackage() ?: lastRealApp
        return if (p != null) labelFor(p) else "تطبيق"
    }

    private fun markCopy(app: String) {
        lastCopyApp = app
        lastCopyAt = SystemClock.uptimeMillis()
    }

    private fun noteSelection(pkg: String) {
        val now = SystemClock.uptimeMillis()
        val first = selectionPkg != pkg || now - selectionAt > 5000
        selectionPkg = pkg
        selectionAt = now
        if (first) dbg("تحديد نص في ${labelFor(pkg)}")
    }

    // ---------- التشغيل ----------

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
                requestCapture(manual = true, expectChange = false, acceptUnchanged = true, maxAttempts = 2)
            }
            shelf?.show()
        }

        clipListener?.let { clipboardManager.removePrimaryClipChangedListener(it) }

        val l = ClipboardManager.OnPrimaryClipChangedListener {
            val until = maxOf(ignoreClipUntil, HistoryRepository.ignoreClipUntil)
            if (System.currentTimeMillis() < until) return@OnPrimaryClipChangedListener
            dbg("النظام بلّغني بنسخ")
            markCopy(sourceName())
            if (!readDirect()) {
                requestCapture(manual = false, expectChange = false, acceptUnchanged = true, maxAttempts = 2)
            }
        }
        clipListener = l
        clipboardManager.addPrimaryClipChangedListener(l)
    }

    // قراءة عادية بدون تركيز: بترجع true لو اتقرت أو اتجاهلت عن قصد
    private fun readDirect(): Boolean {
        try {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val d = clip.description
                val textual = d == null || d.hasMimeType("text/*")
                val sensitive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    d?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
                if (sensitive || !textual) {
                    dbg("اتجاهلته (حساس أو مش نص)")
                    return true
                }
                val text = clip.getItemAt(0).coerceToText(this)?.toString()
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
     * expectChange: لو الحافظة لسه فيها النص القديم، نستنى ونقرا تاني (بعض التطبيقات بتتأخر).
     * acceptUnchanged: لو النص فضل زي ما هو بعد كل المحاولات، نسجله برضه (نسخ متكرر مقصود)
     */
    private fun requestCapture(
        manual: Boolean,
        expectChange: Boolean,
        acceptUnchanged: Boolean,
        maxAttempts: Int
    ) {
        if (captureBusy) return
        val s = shelf ?: return
        captureBusy = true
        // أمان: لو حصل أي حاجة غريبة نفك القفل بعد 6 ثواني
        handler.postDelayed({ captureBusy = false }, 6000)
        captureLoop(s, 0, lastCaptured, manual, expectChange, acceptUnchanged, maxAttempts)
    }

    private fun captureLoop(
        s: ShelfController,
        attempt: Int,
        baseline: String?,
        manual: Boolean,
        expectChange: Boolean,
        acceptUnchanged: Boolean,
        maxAttempts: Int
    ) {
        s.captureClipboard { text, skipped ->
            if (skipped) {
                captureBusy = false
                dbg("اتجاهلته (حساس أو مش نص)")
                return@captureClipboard
            }
            val t = text
            val empty = t.isNullOrBlank()
            val unchanged = expectChange && !empty && t == baseline
            if ((empty || unchanged) && attempt < maxAttempts) {
                handler.postDelayed(
                    {
                        captureLoop(s, attempt + 1, baseline, manual, expectChange, acceptUnchanged, maxAttempts)
                    },
                    350
                )
            } else {
                captureBusy = false
                if (t.isNullOrBlank()) {
                    dbg("الحافظة فاضية أو النظام منع القراءة")
                    if (manual) {
                        Toast.makeText(this, "الحافظة فاضية أو النظام منع القراءة", Toast.LENGTH_SHORT).show()
                    }
                } else if (unchanged && !acceptUnchanged) {
                    dbg("مفيش نسخ جديد")
                } else {
                    acceptText(t)
                }
            }
        }
    }

    // ---------- الأحداث ----------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (isSystemNoise(pkg)) return
                if (!isLauncher(pkg)) lastRealApp = pkg

                // خرجت من تطبيق بعد ما حددت فيه نص: غالباً نسخت، نفحص الحافظة
                val sp = selectionPkg
                if (sp != null && pkg != sp && SystemClock.uptimeMillis() - selectionAt < 60_000) {
                    selectionPkg = null
                    markCopy(labelFor(sp))
                    dbg("خرجت من ${labelFor(sp)} بعد تحديد نص، بفحص الحافظة")
                    handler.postDelayed({
                        requestCapture(manual = false, expectChange = true, acceptUnchanged = false, maxAttempts = 1)
                    }, 500)
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (isSystemNoise(pkg)) return
                if (looksLikeCopy(event)) {
                    markCopy(labelFor(pkg))
                    dbg("ضغطة نسخ في ${labelFor(pkg)}")
                    // التطبيق محتاج لحظة يحط النص في الحافظة قبل ما نقراه
                    handler.postDelayed({
                        requestCapture(manual = false, expectChange = true, acceptUnchanged = true, maxAttempts = 3)
                    }, 150)
                }
            }

            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                if (!isIgnored(pkg)) noteSelection(pkg)
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                if (isIgnored(pkg)) return
                // المؤشر لوحده مش تحديد
                if (event.fromIndex == event.toIndex) return
                noteSelection(pkg)
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
