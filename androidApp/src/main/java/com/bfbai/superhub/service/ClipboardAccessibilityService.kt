// Built with BFB.AI
package com.bfbai.superhub.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.quicksettings.TileService
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
    private var lastFocusCapture: Long = 0L
    private var manualCapture: Boolean = false
    private var focusView: View? = null

    val shelfShown: Boolean get() = shelf?.isShown == true

    fun toggleShelf(): Boolean {
        if (shelf?.isShown == true) shelf?.hide() else shelf?.show()
        return shelfShown
    }

    // التقاط يدوي للحافظة (ممكن يتنادى من أي زرار أو إشعار)
    fun pasteFromShelf() {
        captureWithFocus(force = true)
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
            shelf?.show()
        }

        clipListener?.let { clipboardManager.removePrimaryClipChangedListener(it) }

        val l = ClipboardManager.OnPrimaryClipChangedListener {
            val until = maxOf(ignoreClipUntil, HistoryRepository.ignoreClipUntil)
            if (System.currentTimeMillis() < until) return@OnPrimaryClipChangedListener
            lastCopyApp = getAppName()
            dbg("رصدت نسخ من $lastCopyApp")
            // أول محاولة: قراءة عادية. لو النظام منعها نفتح الالتقاط بالتركيز
            if (readClipAndAdd()) dbg("اتقرا مباشرة") else captureWithFocus(force = false)
        }
        clipListener = l
        clipboardManager.addPrimaryClipChangedListener(l)
    }

    // بترجع true لو الحافظة اتقرت (حتى لو المحتوى حساس وتجاهلناه)، وfalse لو النظام منع القراءة أو فاضية
    private fun readClipAndAdd(): Boolean {
        return try {
            val clip = clipboardManager.primaryClip
            if (clip == null || clip.itemCount == 0) {
                false
            } else {
                val sensitive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
                if (sensitive) {
                    true
                } else {
                    val text = clip.getItemAt(0).coerceToText(this)?.toString()
                    if (text.isNullOrBlank()) {
                        false
                    } else {
                        shelf?.add(text, lastCopyApp ?: getAppName())
                        true
                    }
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    // الالتقاط بالتركيز: Activity شفافة بتفتح لحظة وتقرا الحافظة وتقفل
    private fun captureWithFocus(force: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (captureBusy) return
        if (!force && now - lastFocusCapture < 700) return
        lastFocusCapture = now
        manualCapture = force
        captureBusy = true
        handler.postDelayed({ captureBusy = false }, 1500)

        dbg("النظام منع القراءة، بفتح الالتقاط")
        try {
            val i = Intent(this, ClipCaptureActivity::class.java)
            i.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
            )
            startActivity(i)
        } catch (e: Exception) {
            dbg("الـ Activity ما اشتغلتش، بجرب النافذة")
            captureWithOverlay()
        }
    }

    // الـ Activity بتنادي هنا بعد ما تقرا الحافظة
    fun handleCapturedFromActivity(text: String?, sensitive: Boolean) {
        captureBusy = false
        when {
            sensitive -> dbg("محتوى حساس، اتجاهلته")
            text.isNullOrBlank() -> {
                dbg("الحافظة فاضية حتى مع التركيز")
                if (manualCapture) {
                    Toast.makeText(this, "الحافظة فاضية أو النظام منع القراءة", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                dbg("اتقرا بالتركيز")
                shelf?.add(text, lastCopyApp ?: getAppName())
            }
        }
    }

    // خطة بديلة لو فتح الـ Activity من الخلفية اتمنع: نافذة 1×1 بتاخد التركيز لحظة
    private fun captureWithOverlay() {
        if (focusView != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val v = View(this)
        val p = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        try {
            wm.addView(v, p)
            focusView = v
        } catch (e: Exception) {
            captureBusy = false
            return
        }
        attemptOverlayRead(0)
    }

    private fun attemptOverlayRead(attempt: Int) {
        handler.postDelayed({
            val done = readClipAndAdd()
            if (done || attempt >= 2) {
                releaseFocusView()
                captureBusy = false
                if (!done) dbg("النافذة كمان ما قدرتش تقرا")
            } else {
                attemptOverlayRead(attempt + 1)
            }
        }, if (attempt == 0) 200L else 250L)
    }

    private fun releaseFocusView() {
        val v = focusView ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (e: Exception) {
        }
        focusView = null
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
        releaseFocusView()
        clipListener?.let { clipboardManager.removePrimaryClipChangedListener(it) }
        clipListener = null
        shelf?.destroy()
        shelf = null
        updatePrefs(false)
        super.onDestroy()
    }
}
