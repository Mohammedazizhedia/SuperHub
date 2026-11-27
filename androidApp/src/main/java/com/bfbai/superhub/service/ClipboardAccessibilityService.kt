// Built with BFB.AI
package com.bfbai.superhub.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
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
import com.bfbai.superhub.utils.DiagLog
import com.bfbai.superhub.utils.HistoryRepository
import com.bfbai.superhub.utils.ImageStore
import com.bfbai.superhub.utils.PrivacyPolicy
import com.bfbai.superhub.utils.ShelfBehavior
import kotlin.math.abs

private class CaptureReq(
    val manual: Boolean,
    val passive: Boolean,
    val expectChange: Boolean,
    val maxAttempts: Int
)

private enum class Res { DONE, RETRY }

class ClipboardAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ClipboardAccessibilityService? = null

        // مدة اعتبار ضغطة النسخ مصدراً للنص (ملي ثانية)
        private const val TRIGGER_WINDOW = 2500L
    }

    private lateinit var clipboardManager: ClipboardManager
    private val handler = Handler(Looper.getMainLooper())
    private var shelf: ShelfController? = null
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    // خط زمني للتطبيق الأمامي: (الوقت، الحزمة) والحزمة null معناها مصدر مجهول (تطبيق بيعمّي الإتاحة)
    private val fg = ArrayList<Pair<Long, String?>>()
    private var lastRealApp: String? = null

    // آخر ضغطة نسخ اتشافت
    private var triggerPkg: String? = null
    private var triggerWall: Long = 0L

    private var ignoreClipUntil: Long = 0L

    // الالتقاط بطابور: لو جه طلب والتالي شغال، بنحفظ آخر طلب وبننفذه بعد ما الحالي يخلص
    private var captureBusy: Boolean = false
    private var captureToken: Int = 0
    private var pending: CaptureReq? = null

    // آخر نسخة اتعالجت (ختم وقتها من النظام)
    private var lastClipTs: Long = 0L
    private var lastEvtPkg: String? = null
    private var lastCaptured: String? = null

    // آخر نص لصقناه إحنا من الرف: لو رجع من الحافظة في التقاط سلبي بنتجاهله
    private var lastInsertedText: String? = null

    // آخر تطبيق حصل فيه تحديد نص (لو خرجت منه بعدها، بنفحص الحافظة)
    private var selectionPkg: String? = null
    private var selectionAt: Long = 0L

    private val copyTokens = setOf("copy", "نسخ", "انسخ")
    private val copyIdRe = Regex("(^|[_.:/\\-])copy([_.:/\\-]|\$)", RegexOption.IGNORE_CASE)
    private val splitRe = Regex("[^\\p{L}\\p{N}]+")

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
        requestCapture(manual = true, passive = true, expectChange = false, maxAttempts = 2)
    }

    private fun dbg(msg: String) {
        DiagLog.d("svc", msg)
        if (ShelfStyle.debug) Toast.makeText(this, "HubSync: $msg", Toast.LENGTH_SHORT).show()
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

    private fun nameOf(pkg: String?): String = if (pkg != null) labelFor(pkg) else tr("تطبيق", "App")

    // وقت الحدث بالساعة العادية
    private fun wall(eventTime: Long): Long =
        System.currentTimeMillis() - (SystemClock.uptimeMillis() - eventTime)

    private fun pushFg(t: Long, pkg: String?) {
        val last = fg.lastOrNull()
        if (last != null && last.second == pkg) return
        fg.add(Pair(t, pkg))
        if (fg.size > 200) fg.removeAt(0)
        if (pkg != null && !isLauncher(pkg)) lastRealApp = pkg
    }

    // بنشوف التطبيق اللي نافذته نشطة دلوقتي. لو ما قدرناش نقرا اسمه (بيعمّي الإتاحة) نسجله "مجهول"
    private fun evalForeground(t: Long) {
        try {
            val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            val w = apps.firstOrNull { it.isActive } ?: apps.firstOrNull() ?: return
            val p = w.root?.packageName?.toString()
            if (p != null) {
                if (!isSystemNoise(p)) pushFg(t, p)
            } else {
                val last = fg.lastOrNull()
                val recentKnown = last != null && last.second != null && t - last.first < 800L
                if (!recentKnown) pushFg(t, null)
            }
        } catch (e: Exception) {
        }
    }

    private fun markTrigger(pkg: String) {
        triggerPkg = pkg
        triggerWall = System.currentTimeMillis()
    }

    // التطبيق اللي النسخ حصل فيه وقت ts: ضغطة نسخ قريبة، وإلا الخط الزمني. null = مجهول
    private fun sourceAt(ts: Long): String? {
        val t = if (ts > 0L) ts else System.currentTimeMillis()
        val tp = triggerPkg
        if (tp != null && abs(t - triggerWall) < TRIGGER_WINDOW) return tp
        for (i in fg.indices.reversed()) {
            val e = fg[i]
            if (e.first <= t + 250L) return e.second
        }
        return lastRealApp
    }

    private fun noteSelection(pkg: String) {
        val now = SystemClock.uptimeMillis()
        val first = selectionPkg != pkg || now - selectionAt > 5000
        selectionPkg = pkg
        selectionAt = now
        if (first) DiagLog.d("svc", "selection in $pkg")
    }

    // ---------- التشغيل ----------

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("ClipboardService", "Service Connected")

        ThemeEngine.init(this)
        PrivacyPolicy.init(this)
        HistoryRepository.init(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        evalForeground(System.currentTimeMillis())

        if (shelf == null) {
            val s = ShelfController(this) { insertIntoFocused(it) }
            s.appNameProvider = { nameOf(sourceAt(System.currentTimeMillis())) }
            s.pkgProvider = { sourceAt(System.currentTimeMillis()) }
            s.onShownChanged = { shown ->
                updatePrefs(shown)
                SuperHubTileService.live?.refresh()
                TileService.requestListeningState(
                    this, ComponentName(this, SuperHubTileService::class.java)
                )
            }
            // لما تدوس على مقبض الرف: نقرا الحافظة (المسار اليدوي)
            s.onOpenedByUser = {
                requestCapture(manual = true, passive = true, expectChange = false, maxAttempts = 2)
            }
            shelf = s
            s.show()
        }

        clipListener?.let { clipboardManager.removePrimaryClipChangedListener(it) }

        val l = ClipboardManager.OnPrimaryClipChangedListener {
            val until = maxOf(ignoreClipUntil, HistoryRepository.ignoreClipUntil)
            if (System.currentTimeMillis() < until) return@OnPrimaryClipChangedListener
            DiagLog.d("svc", "clip changed event")
            if (!ShelfBehavior.autoCapture) {
                DiagLog.d("svc", "auto capture is off")
                return@OnPrimaryClipChangedListener
            }
            val pkg = sourceAt(System.currentTimeMillis())
            if (!PrivacyPolicy.allowsCapture(pkg)) {
                dbg(tr("الالتقاط متوقف", "Capture blocked") + " [" + (pkg ?: "?") + "]")
                return@OnPrimaryClipChangedListener
            }
            if (!readDirect()) {
                requestCapture(manual = false, passive = false, expectChange = false, maxAttempts = 2)
            }
        }
        clipListener = l
        clipboardManager.addPrimaryClipChangedListener(l)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            ThemeEngine.ensure(this)
            ShelfSettings.notifyChanged()
        } catch (e: Exception) {
        }
    }

    // ---------- قراءة الحافظة ----------

    // قراءة عادية بدون تركيز (بترجع null لو فاضية أو النظام منعها)
    private fun readClipSnap(): ClipSnap? {
        return try {
            val clip = clipboardManager.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            val d = clip.description
            val ts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && d != null) d.timestamp else 0L
            val sensitive = ShelfBehavior.ignoreSensitive &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                d?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
            val first = clip.getItemAt(0)
            val u = first.uri
            when {
                sensitive -> ClipSnap(null, null, true, ts)
                d != null && d.hasMimeType("image/*") && u != null -> ClipSnap(null, u, false, ts)
                d == null || d.hasMimeType("text/*") ->
                    ClipSnap(first.coerceToText(this)?.toString(), null, false, ts)
                else -> ClipSnap(null, null, true, ts)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readDirect(): Boolean {
        val snap = readClipSnap() ?: return false
        if (!snap.skipped && snap.uri == null && snap.text.isNullOrBlank()) return false
        processSnap(snap, CaptureReq(false, false, false, 0), lastClipTs, lastCaptured, 0, true)
        return true
    }

    /**
     * بيعالج نسخة اتقرت: يحدد مصدرها من ختم وقتها، ويطبق الخصوصية، ويتجاهل اللي اتعالج قبل كده.
     * RETRY معناها الحافظة لسه ما اتحدثتش (أو فاضية) ولسه فيه محاولات.
     */
    private fun processSnap(
        s: ClipSnap,
        req: CaptureReq,
        baseTs: Long,
        baseText: String?,
        attempt: Int,
        direct: Boolean
    ): Res {
        if (s.skipped) {
            dbg(tr("اتجاهلته (حساس أو مش نص ولا صورة)", "Ignored (sensitive or not text/image)"))
            return Res.DONE
        }
        val text = s.text
        val uri = s.uri
        val empty = uri == null && text.isNullOrBlank()
        if (empty) {
            if (attempt < req.maxAttempts) return Res.RETRY
            dbg(tr("الحافظة فاضية أو النظام منع القراءة", "Clipboard is empty or blocked"))
            if (req.manual) {
                Toast.makeText(
                    this,
                    tr("الحافظة فاضية أو النظام منع القراءة", "Clipboard is empty or blocked"),
                    Toast.LENGTH_SHORT
                ).show()
            }
            return Res.DONE
        }

        val ts = s.ts
        // النسخة لسه هي هي اللي كانت قبل الطلب: يعني التطبيق لسه ما حدّثش الحافظة
        val stale = if (ts > 0L) ts == baseTs else (text != null && text == baseText)
        if (req.expectChange && stale && attempt < req.maxAttempts) return Res.RETRY

        val pkg = sourceAt(if (ts > 0L) ts else System.currentTimeMillis())
        if (!PrivacyPolicy.allowsCapture(pkg)) {
            DiagLog.d("svc", "blocked by privacy: pkg=${pkg ?: "?"} ts=$ts")
            dbg(
                tr("الالتقاط متوقف (وضع خاص أو تطبيق مستثنى أو مصدر مجهول)", "Capture blocked (private mode, excluded or unknown app)") +
                    " [" + (pkg ?: "?") + "]"
            )
            if (ts > 0L) lastClipTs = ts
            if (req.manual) {
                Toast.makeText(
                    this,
                    tr("النسخة دي جاية من تطبيق مستثنى أو مجهول، فاتجاهلتها", "This copy came from an excluded or unknown app, so it was ignored"),
                    Toast.LENGTH_LONG
                ).show()
            }
            return Res.DONE
        }

        if (ts > 0L && ts == lastClipTs) {
            DiagLog.d("svc", "clip already handled ts=$ts")
            if (req.manual) shelf?.popOpen()
            return Res.DONE
        }

        if (uri != null) {
            val prev = lastClipTs
            if (ts > 0L) lastClipTs = ts
            acceptImage(uri, pkg, req) { failed ->
                if (failed) {
                    lastClipTs = prev
                    if (direct) requestCapture(manual = false, passive = false, expectChange = false, maxAttempts = 2)
                }
            }
            return Res.DONE
        }

        val t = text ?: return Res.DONE
        if (ts > 0L) lastClipTs = ts
        if (req.passive) {
            if (HistoryRepository.isDismissed(t)) {
                dbg(tr("النص ده اتمسح قبل كده، اتجاهلته", "That text was deleted before, ignored"))
                return Res.DONE
            }
            if (t == lastInsertedText) {
                dbg(tr("ده نص لسه لاصقه، سيبته في مكانه", "That is the text you just pasted, left in place"))
                return Res.DONE
            }
        }
        acceptText(t, pkg, req.passive)
        return Res.DONE
    }

    private fun acceptText(text: String, pkg: String?, passive: Boolean) {
        if (!passive) {
            HistoryRepository.forgetDismissed(text)
            lastInsertedText = null
        }
        lastCaptured = text
        val name = nameOf(pkg)
        shelf?.add(text, name, true, pkg)
        dbg(tr("اتسجل من $name", "Saved from $name"))
    }

    // بتحفظ الصورة. onFinished(true) معناها فشلت القراءة
    private fun acceptImage(uri: Uri, pkg: String?, req: CaptureReq, onFinished: (Boolean) -> Unit) {
        val name = nameOf(pkg)
        DiagLog.d("svc", "image in clipboard, saving (from $name)")
        Thread {
            val path = ImageStore.saveFromUri(this, uri)
            val h = if (path != null) ImageStore.hashOf(path) else null
            handler.post {
                val p = path
                if (p == null) {
                    dbg(tr("مقدرتش أقرا الصورة", "Could not read the image") + ": " + (ImageStore.lastError ?: "-"))
                    onFinished(true)
                } else if (req.passive && HistoryRepository.isDismissedImage(h)) {
                    ImageStore.delete(p)
                    dbg(tr("الصورة دي اتمسحت قبل كده، اتجاهلتها", "That image was deleted before, ignored"))
                    onFinished(false)
                } else {
                    if (h != null) HistoryRepository.forgetDismissedImage(h)
                    HistoryRepository.addImage(p, name, pkg)
                    shelf?.notifyNew(tr("صورة جديدة من $name", "new image from $name"))
                    dbg(tr("اتسجلت صورة من $name", "Image saved from $name"))
                    onFinished(false)
                }
            }
        }.start()
    }

    // ---------- الالتقاط بالتركيز (بطابور) ----------

    private fun requestCapture(manual: Boolean, passive: Boolean, expectChange: Boolean, maxAttempts: Int) {
        if (!manual && !ShelfBehavior.autoCapture) {
            DiagLog.d("svc", "auto capture is off")
            return
        }
        // بلاش نسرق التركيز (ورمشة الشاشة) لو الالتقاط أصلاً ممنوع
        if (!manual && !PrivacyPolicy.allowsCapture(sourceAt(System.currentTimeMillis()))) {
            DiagLog.d("svc", "capture skipped by privacy before focus")
            return
        }
        val req = CaptureReq(manual, passive, expectChange, maxAttempts)
        if (captureBusy) {
            val p = pending
            if (p == null || !p.manual || manual) pending = req
            return
        }
        startCapture(req)
    }

    private fun startCapture(req: CaptureReq) {
        val s = shelf ?: return
        captureBusy = true
        val token = ++captureToken
        // أمان: لو حصل أي حاجة غريبة نفك القفل بعد 8 ثواني
        handler.postDelayed({ if (captureToken == token && captureBusy) finishCapture() }, 8000)
        captureLoop(s, 0, lastClipTs, lastCaptured, req)
    }

    private fun finishCapture() {
        captureBusy = false
        val p = pending
        pending = null
        if (p != null) {
            handler.postDelayed({ if (!captureBusy) startCapture(p) }, 60)
        }
    }

    private fun captureLoop(s: ShelfController, attempt: Int, baseTs: Long, baseText: String?, req: CaptureReq) {
        s.captureClip { snap ->
            when (processSnap(snap, req, baseTs, baseText, attempt, false)) {
                Res.RETRY -> handler.postDelayed({ captureLoop(s, attempt + 1, baseTs, baseText, req) }, 250)
                Res.DONE -> finishCapture()
            }
        }
    }

    // ---------- الأحداث ----------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()
        val wt = wall(event.eventTime)

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> evalForeground(wt)

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg == null || isSystemNoise(pkg)) return
                pushFg(wt, pkg)

                // خرجت من تطبيق بعد ما حددت فيه نص: غالباً نسخت، نفحص الحافظة
                val sp = selectionPkg
                if (sp != null && pkg != sp && SystemClock.uptimeMillis() - selectionAt < 60_000) {
                    selectionPkg = null
                    markTrigger(sp)
                    if (PrivacyPolicy.allowsCapture(sp)) {
                        DiagLog.d("svc", "left $sp after selection, checking clipboard")
                        handler.postDelayed({
                            requestCapture(manual = false, passive = true, expectChange = true, maxAttempts = 1)
                        }, 450)
                    } else {
                        DiagLog.d("svc", "left excluded app $sp after selection, skipped")
                    }
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (pkg == null || isSystemNoise(pkg)) return
                if (looksLikeCopy(event)) {
                    markTrigger(pkg)
                    if (!PrivacyPolicy.allowsCapture(pkg)) {
                        dbg(tr("ضغطة نسخ في تطبيق مستثنى، اتجاهلتها", "Copy tap in an excluded app, ignored") + " [$pkg]")
                        return
                    }
                    DiagLog.d("svc", "copy tap in $pkg")
                    // التطبيق محتاج لحظة يحط النص في الحافظة قبل ما نقراه
                    handler.postDelayed({
                        requestCapture(manual = false, passive = false, expectChange = true, maxAttempts = 3)
                    }, 120)
                }
            }

            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                if (pkg != null && !isIgnored(pkg)) noteSelection(pkg)
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                if (pkg == null || isIgnored(pkg)) return
                // المؤشر لوحده مش تحديد
                if (event.fromIndex == event.toIndex) return
                noteSelection(pkg)
            }
        }
    }

    // هل الضغطة كانت على زرار "نسخ" أو "Copy"؟ أول كلمة لازم تكون "نسخ"/"copy"
    // ("نسخة احتياطية" و"Copyright" ما بيتحسبوش)
    private fun looksLikeCopy(e: AccessibilityEvent): Boolean {
        val texts = ArrayList<String>()
        var id: String? = null
        try {
            e.text?.forEach { texts.add(it.toString()) }
            e.contentDescription?.let { texts.add(it.toString()) }
            val n = e.source
            if (n != null) {
                n.text?.let { texts.add(it.toString()) }
                n.contentDescription?.let { texts.add(it.toString()) }
                id = n.viewIdResourceName
            }
        } catch (ex: Exception) {
        }
        for (t in texts) {
            val s = t.trim().lowercase()
            if (s.isEmpty() || s.length > 24) continue
            val tokens = s.split(splitRe).filter { it.isNotEmpty() }
            if (tokens.isNotEmpty() && tokens.size <= 3 && tokens[0] in copyTokens) return true
        }
        if (id != null && copyIdRe.containsMatchIn(id)) return true
        return false
    }

    private fun updatePrefs(isVisible: Boolean) {
        val prefs = getSharedPreferences("SuperHubPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("shelf_visible", isVisible).apply()
    }

    // بيلصق النص في الخانة المفتوحة: بيرجّع true لو نجح
    fun insertIntoFocused(text: String): Boolean {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (node == null) {
            Toast.makeText(
                this,
                tr("اضغط على خانة الكتابة الأول", "Tap a text field first"),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        ignoreClipUntil = System.currentTimeMillis() + 1000
        // الحافظة هتبقى فيها النص ده، فنعتبره "اتلقط" وما نحركهوش من مكانه
        lastCaptured = text
        lastInsertedText = text
        clipboardManager.setPrimaryClip(ClipData.newPlainText("shelf", text))

        var ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        if (!ok) {
            val old = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
            val s = minOf(node.textSelectionStart.coerceAtLeast(0), old.length)
            val e = minOf(node.textSelectionEnd.coerceAtLeast(s), old.length)
            val merged = old.substring(0, s) + text + old.substring(e)

            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, merged)
            }
            ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        return ok
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
