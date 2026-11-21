// Built with BFB.AI
package com.bfbai.superhub.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.DragEvent
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.bfbai.superhub.ui.HistoryActivity
import com.bfbai.superhub.utils.Detection
import com.bfbai.superhub.utils.HistoryRepository
import com.bfbai.superhub.utils.ImageStore
import com.bfbai.superhub.utils.ItemKind
import com.bfbai.superhub.utils.SmartDetector
import com.bfbai.superhub.utils.Thumbs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class ShelfItem(
    val text: String,
    val source: String,
    val timestamp: Long = System.currentTimeMillis(),
    var isPinned: Boolean = false,
    val imagePath: String? = null
)

internal fun withAlpha(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0x00FFFFFF)

/** كل شكل الرف وسلوكه في مكان واحد، وهنوصله بالإعدادات في خطوة قادمة */
object ShelfStyle {
    // الشكل الكلاسيكي (الافتراضي): لوحة كحلي غامقة + كروت زرقاء شفافة بحد سماوي
    var panelColor: Int = Color.parseColor("#E60B1B3A")
    var panelGradient: Boolean = false
    var panelTop: Int = Color.parseColor("#D91A3563")
    var panelBottom: Int = Color.parseColor("#E60A1730")
    var panelStroke: Int = Color.parseColor("#334DE1E1")
    var handleColor: Int = Color.parseColor("#E60B1B3A")
    var accent: Int = Color.parseColor("#4DE1E1")
    var textColor: Int = Color.WHITE
    var subTextColor: Int = Color.parseColor("#9FB3D1")
    var metaColor: Int = Color.parseColor("#4DE1E1")
    var boldText: Boolean = true

    var cornerDp: Float = 18f
    var cardCornerDp: Float = 14f

    // شفافية الكروت: 0xFF صلب تماماً، 0x59 شفاف نص نص، 0x40 أكتر شفافية، 0x2E زجاجي جداً
    var cardFillColor: Int = Color.parseColor("#1E3A6D")
    var cardFillAlpha: Int = 0x59
    var cardStrokeColor: Int = Color.parseColor("#4DE1E1")
    var cardStrokeAlpha: Int = 0x66
    // خيارات للإعدادات: لون الكارت من نوعه، والتدرج
    var cardTintByKind: Boolean = false
    var cardGradient: Boolean = false
    var cardGradientEndAlpha: Int = 0x10
    var rotateTextColors: Boolean = false
    var neutralColor: Int = Color.parseColor("#B7C7E4")
    var imageColor: Int = Color.parseColor("#FF8FB1")

    var blurEnabled: Boolean = true
    var blurRadiusDp: Float = 28f

    var closedWidthDp: Float = 24f
    var closedHeightDp: Float = 150f
    var expandedWidthDp: Float = 280f
    var expandedHeightDp: Float = 560f

    var autoCloseMs: Long = 10_000L
    var otpTtlMs: Long = 60_000L

    // أقصى طول نص بيتعرض في الكارت (النص الكامل بيتحفظ كامل)
    var previewChars: Int = 400

    // السحب بيصحّي الرف ويفتحه (حتى لو مخفي). هيبقى خيار في الإعدادات
    var dragWakesShelf: Boolean = true

    // رسائل التشخيص المؤقتة
    var debug: Boolean = true

    val kindColors: Map<ItemKind, Int> = mapOf(
        ItemKind.OTP to Color.parseColor("#FFB020"),
        ItemKind.LINK to Color.parseColor("#4DA3FF"),
        ItemKind.PHONE to Color.parseColor("#3DDC97"),
        ItemKind.EMAIL to Color.parseColor("#C792FF")
    )

    var textPalette: List<Int> = listOf(
        Color.parseColor("#4DE1E1"),
        Color.parseColor("#B388FF"),
        Color.parseColor("#FF8FB1"),
        Color.parseColor("#B8E986")
    )

    // ثيمات جاهزة (للإعدادات لاحقاً): "classic" و "glass"
    fun applyPreset(name: String) {
        if (name == "glass") {
            panelColor = Color.parseColor("#CC0F2147")
            panelStroke = Color.parseColor("#40FFFFFF")
            handleColor = Color.parseColor("#E6102448")
            cardFillColor = Color.WHITE
            cardFillAlpha = 0x2E
            cardStrokeColor = Color.WHITE
            cardStrokeAlpha = 0x40
            metaColor = Color.parseColor("#9FB3D1")
            textColor = Color.parseColor("#F2F7FF")
            boldText = false
        } else {
            panelColor = Color.parseColor("#E60B1B3A")
            panelStroke = Color.parseColor("#334DE1E1")
            handleColor = Color.parseColor("#E60B1B3A")
            cardFillColor = Color.parseColor("#1E3A6D")
            cardFillAlpha = 0x59
            cardStrokeColor = Color.parseColor("#4DE1E1")
            cardStrokeAlpha = 0x66
            metaColor = Color.parseColor("#4DE1E1")
            textColor = Color.WHITE
            boldText = true
        }
    }
}

private const val INTERNAL_DRAG = "hubsync-internal-drag"

@SuppressLint("ClickableViewAccessibility")
class ShelfController(
    private val ctx: Context,
    private val onInsert: (String) -> Unit
) {
    private val wm: WindowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val dp: Float = ctx.resources.displayMetrics.density

    // نسخة للعرض فقط، المصدر الحقيقي هو HistoryRepository
    private val items: ArrayList<ShelfItem> = ArrayList()
    private val otpLabels: ArrayList<Pair<ShelfItem, TextView>> = ArrayList()
    private val detectCache: HashMap<String, Detection> = HashMap()

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private var expanded: Boolean = false
    private var added: Boolean = false
    private var userHidden: Boolean = false
    private var dragFromHidden: Boolean = false
    private var isRightSide: Boolean = true
    private var dropped: Boolean = false
    private var ignoreOutsideUntil: Long = 0L

    var onShownChanged: ((Boolean) -> Unit)? = null
    var appNameProvider: (() -> String)? = null
    var onOpenedByUser: (() -> Unit)? = null
    val isShown: Boolean get() = added && !userHidden

    private val autoCloseHandler = Handler(Looper.getMainLooper())
    private val autoCloseRunnable = Runnable { setExpanded(false) }

    private val list: LinearLayout = LinearLayout(ctx)
    private val scroll: ScrollView = ScrollView(ctx)
    private val root: FrameLayout = FrameLayout(ctx)
    private val topBar: LinearLayout = LinearLayout(ctx)
    private val countTv: TextView = TextView(ctx)
    private val grip: View = View(ctx)
    private var customMenu: LinearLayout? = null

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    private val lp: WindowManager.LayoutParams = WindowManager.LayoutParams(
        (ShelfStyle.closedWidthDp * dp).toInt(),
        (ShelfStyle.closedHeightDp * dp).toInt(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT
    )

    // بيحذف أكواد التحقق المنتهية ويحدّث العد التنازلي كل ثانية
    private val ticker: Runnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            var alive = false
            val expired = ArrayList<ShelfItem>()
            for (item in items) {
                if (item.imagePath == null && !item.isPinned && detectOf(item.text).kind == ItemKind.OTP) {
                    if (now - item.timestamp >= ShelfStyle.otpTtlMs) expired.add(item) else alive = true
                }
            }
            for (item in expired) HistoryRepository.remove(item)
            for ((item, tv) in otpLabels) {
                val left = ((ShelfStyle.otpTtlMs - (now - item.timestamp)) / 1000).coerceAtLeast(0)
                tv.text = "ينتهي بعد ${left}ث"
            }
            if (alive) autoCloseHandler.postDelayed(this, 1000)
        }
    }

    init {
        HistoryRepository.init(ctx)

        lp.gravity = Gravity.TOP or Gravity.END
        lp.x = 0
        lp.y = (120 * dp).toInt()

        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        updateBackground()

        val pad = (10 * dp).toInt()
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(pad, pad, pad, pad)

        scroll.addView(list)
        scroll.visibility = View.GONE
        scroll.setOnTouchListener { _, _ ->
            resetAutoClose()
            false
        }

        // الترويسة: اسم التطبيق + عدد العناصر + زرار القائمة
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.setPadding((14 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), 0)

        val titleBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(ctx).apply {
            text = "HubSync"
            setTextColor(ShelfStyle.textColor)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        countTv.setTextColor(ShelfStyle.subTextColor)
        countTv.textSize = 11f
        titleBox.addView(title)
        titleBox.addView(countTv)

        val menuBtn = TextView(ctx).apply {
            text = "⋮"
            setTextColor(ShelfStyle.accent)
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding((14 * dp).toInt(), (6 * dp).toInt(), (14 * dp).toInt(), (6 * dp).toInt())
            setOnClickListener {
                if (customMenu?.visibility == View.VISIBLE) hideCustomMenu() else showCustomMenu()
            }
        }
        topBar.addView(titleBox)
        topBar.addView(menuBtn)

        // مقبض صغير بيظهر والرف مطوي
        grip.background = GradientDrawable().apply {
            cornerRadius = 3 * dp
            setColor(withAlpha(ShelfStyle.accent, 0xCC))
        }

        root.addView(grip, FrameLayout.LayoutParams((3 * dp).toInt(), (40 * dp).toInt(), Gravity.CENTER))
        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (54 * dp).toInt()
            )
        )
        root.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { topMargin = (54 * dp).toInt() }
        )
        topBar.visibility = View.GONE

        // أي تغيير في المخزن (من الرف أو من شاشة السجل) بيتعرض هنا فوراً
        HistoryRepository.items
            .onEach { all ->
                items.clear()
                items.addAll(all.sortedByDescending { it.isPinned }.take(50))
                render()
            }
            .launchIn(scope)

        root.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false
            private var isLongPressed = false
            private val longPressRunnable = Runnable {
                isLongPressed = true
                animateDisappear()
            }

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                if (userHidden) return false

                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    // لمسة بعد ما الرف يتفتح بلحظة (مثلاً ضغطة نسخ) ما تقفلوش
                    if (SystemClock.uptimeMillis() < ignoreOutsideUntil) return true
                    if (customMenu?.visibility == View.VISIBLE) {
                        hideCustomMenu()
                    } else if (expanded) {
                        setExpanded(false)
                    }
                    return true
                }

                if (customMenu?.visibility == View.VISIBLE) return false

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = lp.x
                        initialY = lp.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        isLongPressed = false

                        resetAutoClose()
                        autoCloseHandler.postDelayed(longPressRunnable, 600)
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()

                        if (abs(deltaX) > 15 || abs(deltaY) > 15) {
                            isDragging = true
                            autoCloseHandler.removeCallbacks(longPressRunnable)

                            val rawX = if (isRightSide) initialX - deltaX else initialX + deltaX
                            lp.x = rawX.coerceIn(0, maxX())
                            lp.y = (initialY + deltaY).coerceIn(0, maxY())
                            if (added) wm.updateViewLayout(root, lp)

                            resetAutoClose()
                        }
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        autoCloseHandler.removeCallbacks(longPressRunnable)

                        if (isLongPressed) return true

                        if (isDragging) {
                            val midPoint = ctx.resources.displayMetrics.widthPixels / 2
                            if (event.rawX > midPoint) {
                                lp.gravity = Gravity.TOP or Gravity.END
                                isRightSide = true
                                root.layoutDirection = View.LAYOUT_DIRECTION_RTL
                            } else {
                                lp.gravity = Gravity.TOP or Gravity.START
                                isRightSide = false
                                root.layoutDirection = View.LAYOUT_DIRECTION_LTR
                            }
                            lp.x = 0
                            updateBackground()
                            render()
                            if (added) wm.updateViewLayout(root, lp)
                        } else {
                            if (!expanded) {
                                setExpanded(true)
                                // الضغطة على المقبض = "اقرا الحافظة دلوقتي"
                                onOpenedByUser?.invoke()
                            }
                        }
                        return true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        autoCloseHandler.removeCallbacks(longPressRunnable)
                        return true
                    }
                }
                return false
            }
        })

        root.setOnDragListener { _, e -> handleDrag(e) }
    }

    private fun maxX(): Int = maxOf(0, ctx.resources.displayMetrics.widthPixels - lp.width)
    private fun maxY(): Int = maxOf(0, ctx.resources.displayMetrics.heightPixels - lp.height)

    private fun dbgToast(msg: String) {
        if (ShelfStyle.debug) Toast.makeText(ctx, "HubSync: $msg", Toast.LENGTH_SHORT).show()
    }

    private fun resetAutoClose() {
        autoCloseHandler.removeCallbacks(autoCloseRunnable)
        if (expanded) autoCloseHandler.postDelayed(autoCloseRunnable, ShelfStyle.autoCloseMs)
    }

    /**
     * بتخلي نافذة الرف قابلة للتركيز لحظة قصيرة، وده اللي بيسمح لأندرويد نقرا الحافظة.
     * النتيجة: (نص أو null، رابط صورة أو null، هل اتجاهلنا المحتوى لأنه حساس أو نوع تاني)
     */
    fun captureClipboard(onResult: (String?, Uri?, Boolean) -> Unit) {
        if (!added) {
            onResult(null, null, false)
            return
        }
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        try {
            wm.updateViewLayout(root, lp)
        } catch (e: Exception) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            onResult(null, null, false)
            return
        }
        attemptCapture(0, onResult)
    }

    private fun attemptCapture(n: Int, onResult: (String?, Uri?, Boolean) -> Unit) {
        autoCloseHandler.postDelayed({
            var text: String? = null
            var uri: Uri? = null
            var skip = false
            try {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val d = clip.description
                    val sensitive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        d?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
                    val first = clip.getItemAt(0)
                    val u = first.uri
                    if (sensitive) {
                        skip = true
                    } else if (d != null && d.hasMimeType("image/*") && u != null) {
                        uri = u
                    } else if (d == null || d.hasMimeType("text/*")) {
                        text = first.coerceToText(ctx)?.toString()
                    } else {
                        skip = true
                    }
                }
            } catch (e: Exception) {
            }
            val got = skip || uri != null || !text.isNullOrBlank()
            if (!got && n < 2 && added) {
                attemptCapture(n + 1, onResult)
            } else {
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                if (added) {
                    try {
                        wm.updateViewLayout(root, lp)
                    } catch (e: Exception) {
                    }
                }
                onResult(text, uri, skip)
            }
        }, if (n == 0) 140L else 220L)
    }

    // النصوص الطويلة ما بنحللهاش (بتبطّئ، ومحدش بيعمل OTP أو رابط من 1000 حرف)
    private fun detectOf(text: String): Detection {
        if (text.length > 1000) return Detection(ItemKind.TEXT, text)
        val cached = detectCache[text]
        if (cached != null) return cached
        if (detectCache.size > 500) detectCache.clear()
        val d = SmartDetector.detect(text)
        detectCache[text] = d
        return d
    }

    // ---------- السحب والإفلات ----------

    private fun isAcceptable(d: ClipDescription?): Boolean =
        d != null && (d.hasMimeType("text/*") || d.hasMimeType("image/*"))

    private fun handleDrag(e: DragEvent): Boolean {
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                dropped = false
                // سحب خارج من الرف نفسه: مش بنقبله تاني
                if (e.localState == INTERNAL_DRAG) return false
                val d = e.clipDescription
                if (!isAcceptable(d)) {
                    dbgToast("سحب مش نص ولا صورة")
                    return false
                }
                if (userHidden && !ShelfStyle.dragWakesShelf) return false
                dragFromHidden = userHidden
                dbgToast("بدأ سحب، بفتح الرف")
                setExpanded(true)
                return true
            }

            DragEvent.ACTION_DROP -> {
                dropped = true
                dbgToast("إفلات")
                val cd = e.clipData
                if (cd != null) {
                    val open = !dragFromHidden
                    for (i in 0 until cd.itemCount) {
                        handleDropped(cd.getItemAt(i), cd.description, open)
                    }
                }
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                if (dragFromHidden) {
                    dragFromHidden = false
                    setExpanded(false)
                } else if (!dropped) {
                    setExpanded(false)
                } else {
                    resetAutoClose()
                }
            }
        }
        return true
    }

    private fun handleDropped(item: ClipData.Item, desc: ClipDescription?, open: Boolean) {
        val uri = item.uri
        val imageMime = desc?.hasMimeType("image/*") == true
        var isImage = false
        if (uri != null) {
            isImage = imageMime || try {
                ctx.contentResolver.getType(uri)?.startsWith("image/") == true
            } catch (e: Exception) {
                false
            }
        }

        if (isImage && uri != null) {
            val src = appNameProvider?.invoke() ?: "سحب"
            Thread {
                val path = ImageStore.saveFromUri(ctx, uri)
                autoCloseHandler.post {
                    if (path != null) {
                        HistoryRepository.addImage(path, src)
                        if (open) popOpen()
                    } else {
                        Toast.makeText(
                            ctx,
                            "التطبيق ده ما سمحش بقراءة الصورة (${ImageStore.lastError ?: "-"}). استخدم المشاركة (Share)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.start()
        } else {
            val t = item.coerceToText(ctx)?.toString()
            if (t != null) add(t, appNameProvider?.invoke() ?: "سحب", popUp = open)
        }
    }

    private fun animateDisappear() {
        val startY = lp.y
        val anim = ValueAnimator.ofFloat(1f, 0f)
        anim.duration = 400
        anim.addUpdateListener { animation ->
            if (!added) return@addUpdateListener
            val value = animation.animatedValue as Float
            root.alpha = value
            lp.y = startY + ((1f - value) * (40 * dp)).toInt()
            wm.updateViewLayout(root, lp)
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                lp.y = startY
                root.alpha = 1f
                hide()
            }
        })
        anim.start()
    }

    // لون واحد شفاف (التدرج خيار) + حد خفيف. والمطوي لون المقبض
    private fun updateBackground() {
        val r = ShelfStyle.cornerDp * dp
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        if (expanded && ShelfStyle.panelGradient) {
            bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM)
            bg.setColors(intArrayOf(ShelfStyle.panelTop, ShelfStyle.panelBottom))
        } else {
            bg.setColor(if (expanded) ShelfStyle.panelColor else ShelfStyle.handleColor)
        }
        bg.cornerRadii = if (isRightSide) {
            floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
        } else {
            floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        }
        bg.setStroke(
            (1 * dp).toInt(),
            if (expanded) ShelfStyle.panelStroke else withAlpha(ShelfStyle.accent, 0x80)
        )
        root.background = bg
    }

    // ضبابية خلف الرف (أندرويد 12 وما فوق، وبس لو الجهاز بيدعمها)
    private fun applyBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            if (expanded && ShelfStyle.blurEnabled && wm.isCrossWindowBlurEnabled) {
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                lp.blurBehindRadius = (ShelfStyle.blurRadiusDp * dp).toInt()
            } else {
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                lp.blurBehindRadius = 0
            }
        } catch (e: Throwable) {
        }
    }

    fun show() {
        if (!added) {
            wm.addView(root, lp)
            added = true
        }
        userHidden = false
        root.visibility = View.VISIBLE
        root.alpha = 1f
        if (!expanded) {
            lp.width = (ShelfStyle.closedWidthDp * dp).toInt()
            try {
                wm.updateViewLayout(root, lp)
            } catch (e: Exception) {
            }
        }
        onShownChanged?.invoke(true)
    }

    // الإخفاء: النافذة بتفضل موجودة وظاهرة بس شفافة تماماً وضيقة،
    // عشان السحب يفضل شغال (النافذة الـ GONE غالباً ما بتستقبلش أحداث السحب)
    fun hide() {
        if (!added) return
        userHidden = true
        setExpanded(false)
        onShownChanged?.invoke(false)
    }

    fun destroy() {
        onShownChanged = null
        onOpenedByUser = null
        autoCloseHandler.removeCallbacksAndMessages(null)
        scope.cancel()
        if (added) {
            try {
                wm.removeView(root)
            } catch (ignored: Exception) {
            }
            added = false
        }
        userHidden = false
    }

    // موجودة عشان أي كود قديم ينادي عليها ما يقعش في البناء
    fun pasteFromClipboard() {
        show()
        setExpanded(true)
    }

    // يفتح الرف (ويظهره لو كان مخفي)
    fun popOpen() {
        if (!added || userHidden) show()
        setExpanded(true)
    }

    fun add(text: String, source: String = "تطبيق", popUp: Boolean = true) {
        val t = text.trim()
        if (t.isEmpty()) return

        if (popUp && (!added || userHidden)) show()

        HistoryRepository.add(t, source)

        if (popUp) setExpanded(true)
    }

    fun setExpanded(on: Boolean) {
        expanded = on
        if (on) {
            ignoreOutsideUntil = SystemClock.uptimeMillis() + 1200
            root.visibility = View.VISIBLE
            root.alpha = 1f
            topBar.visibility = View.VISIBLE
            scroll.visibility = View.VISIBLE
            grip.visibility = View.GONE
            lp.width = (ShelfStyle.expandedWidthDp * dp).toInt()
            // الارتفاع مثبّت من ShelfStyle، ومحمي إنه ما يعدّيش 90% من الشاشة
            lp.height = (ShelfStyle.expandedHeightDp * dp).toInt()
                .coerceAtMost((ctx.resources.displayMetrics.heightPixels * 0.9f).toInt())
            if (lp.y > maxY()) lp.y = maxY()
            resetAutoClose()
        } else {
            topBar.visibility = View.GONE
            scroll.visibility = View.GONE
            grip.visibility = View.VISIBLE
            lp.width = if (userHidden) (12 * dp).toInt() else (ShelfStyle.closedWidthDp * dp).toInt()
            lp.height = (ShelfStyle.closedHeightDp * dp).toInt()
            hideCustomMenu()
            autoCloseHandler.removeCallbacks(autoCloseRunnable)
            root.visibility = View.VISIBLE
            root.alpha = if (userHidden) 0f else 1f
        }
        updateBackground()
        applyBlur()
        if (added) wm.updateViewLayout(root, lp)
    }

    private fun render() {
        otpLabels.clear()
        list.removeAllViews()
        countTv.text = if (items.isEmpty()) "فارغ" else "${items.size} عنصر"

        if (items.isEmpty()) {
            list.addView(TextView(ctx).apply {
                text = "الرف فارغ\nانسخ أي نص أو اسحبه هنا"
                setTextColor(ShelfStyle.subTextColor)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, (40 * dp).toInt(), 0, 0)
            })
        } else {
            for ((index, item) in items.withIndex()) {
                list.addView(buildItem(item, index))
            }
        }

        autoCloseHandler.removeCallbacks(ticker)
        autoCloseHandler.post(ticker)
    }

    private fun showCustomMenu() {
        if (!expanded) return
        if (customMenu == null) {
            val menu = LinearLayout(ctx)
            menu.orientation = LinearLayout.VERTICAL
            val mp = (8 * dp).toInt()
            menu.setPadding(mp, mp, mp, mp)
            menu.background = GradientDrawable().apply {
                cornerRadius = 14 * dp
                setColor(Color.parseColor("#F20E1F42"))
                setStroke((1 * dp).toInt(), ShelfStyle.panelStroke)
            }
            val lpMenu = FrameLayout.LayoutParams(
                (170 * dp).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
            lpMenu.topMargin = (50 * dp).toInt()
            lpMenu.marginEnd = (10 * dp).toInt()
            menu.layoutParams = lpMenu

            val options = listOf("السجل", "الإعدادات", "إغلاق")
            for ((index, opt) in options.withIndex()) {
                val tv = TextView(ctx).apply {
                    text = opt
                    setTextColor(ShelfStyle.textColor)
                    textSize = 15f
                    setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
                    setOnClickListener {
                        hideCustomMenu()
                        when (opt) {
                            "السجل" -> {
                                setExpanded(false)
                                val intent = Intent(ctx, HistoryActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try {
                                    ctx.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "افتح التطبيق من الأيقونة", Toast.LENGTH_SHORT).show()
                                }
                            }
                            "الإعدادات" -> {
                                Toast.makeText(ctx, "الإعدادات قريباً", Toast.LENGTH_SHORT).show()
                            }
                            "إغلاق" -> {
                                setExpanded(false)
                            }
                        }
                    }
                }
                menu.addView(tv)

                if (index < options.size - 1) {
                    val divider = View(ctx)
                    divider.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * dp).toInt()
                    )
                    divider.setBackgroundColor(withAlpha(ShelfStyle.accent, 0x26))
                    menu.addView(divider)
                }
            }
            customMenu = menu
            root.addView(menu)
        }
        customMenu?.visibility = View.VISIBLE
    }

    private fun hideCustomMenu() {
        customMenu?.visibility = View.GONE
    }

    // ---------- إجراءات الكروت ----------

    private fun copyText(t: String) {
        HistoryRepository.ignoreClipUntil = System.currentTimeMillis() + 1500
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("hubsync", t))
        Toast.makeText(ctx, "تم النسخ", Toast.LENGTH_SHORT).show()
    }

    private fun copyImage(path: String) {
        if (ImageStore.copyImage(ctx, path)) {
            Toast.makeText(ctx, "تم نسخ الصورة، الصقها في المكان اللي عايزه", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(ctx, "تعذر نسخ الصورة", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startExternal(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, "مفيش تطبيق يفتح ده", Toast.LENGTH_SHORT).show()
        }
        setExpanded(false)
    }

    // واتساب محتاج رقم بكود الدولة، والرقم المحلي (0101...) ممكن مايفتحش
    private fun whatsappUri(number: String): Uri {
        var d = number.filter { it.isDigit() }
        if (d.startsWith("00")) d = d.substring(2)
        return Uri.parse("https://wa.me/$d")
    }

    private fun kindLabel(k: ItemKind): String = when (k) {
        ItemKind.OTP -> "كود تحقق"
        ItemKind.LINK -> "رابط"
        ItemKind.PHONE -> "هاتف"
        ItemKind.EMAIL -> "بريد"
        ItemKind.TEXT -> "نص"
    }

    // حذف فوري: بنشيل الكارت من الشاشة الأول وبعدين من المخزن
    private fun deleteItem(item: ShelfItem, card: View) {
        if (item.isPinned) {
            Toast.makeText(ctx, "ألغِ التثبيت الأول", Toast.LENGTH_SHORT).show()
            return
        }
        list.removeView(card)
        HistoryRepository.remove(item)
    }

    private fun pill(label: String, color: Int, primary: Boolean, onClick: () -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = label
        tv.textSize = 12f
        tv.setTypeface(null, if (primary) Typeface.BOLD else Typeface.NORMAL)
        tv.setPadding((14 * dp).toInt(), (6 * dp).toInt(), (14 * dp).toInt(), (6 * dp).toInt())
        val b = GradientDrawable()
        b.cornerRadius = 20 * dp
        if (primary) {
            b.setColor(withAlpha(color, 0xE6))
            tv.setTextColor(Color.parseColor("#0A1730"))
        } else {
            b.setColor(Color.TRANSPARENT)
            b.setStroke((1 * dp).toInt(), withAlpha(color, 0x99))
            tv.setTextColor(color)
        }
        tv.background = b
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = (6 * dp).toInt()
        tv.layoutParams = params
        tv.setOnClickListener {
            resetAutoClose()
            onClick()
        }
        return tv
    }

    // ---------- شكل الكروت ----------

    private fun cardBackground(color: Int): GradientDrawable {
        val fill = if (ShelfStyle.cardTintByKind) {
            withAlpha(color, ShelfStyle.cardFillAlpha)
        } else {
            withAlpha(ShelfStyle.cardFillColor, ShelfStyle.cardFillAlpha)
        }
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = ShelfStyle.cardCornerDp * dp
        if (ShelfStyle.cardGradient) {
            bg.setOrientation(
                if (isRightSide) GradientDrawable.Orientation.RIGHT_LEFT
                else GradientDrawable.Orientation.LEFT_RIGHT
            )
            bg.setColors(intArrayOf(fill, withAlpha(fill, ShelfStyle.cardGradientEndAlpha)))
        } else {
            bg.setColor(fill)
        }
        bg.setStroke((1 * dp).toInt(), withAlpha(ShelfStyle.cardStrokeColor, ShelfStyle.cardStrokeAlpha))
        return bg
    }

    private fun cardLayoutParams(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        p.setMargins(0, 0, 0, (10 * dp).toInt())
        return p
    }

    // الصف الأول: شارة النوع + (عد تنازلي للـ OTP) + النجمة + حذف
    private fun buildTopRow(
        item: ShelfItem,
        label: String,
        color: Int,
        card: View,
        otp: Boolean
    ): LinearLayout {
        val top = LinearLayout(ctx)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL

        val chip = TextView(ctx)
        chip.text = label
        chip.textSize = 11f
        chip.setTextColor(color)
        chip.setTypeface(null, Typeface.BOLD)
        chip.setPadding((9 * dp).toInt(), (2 * dp).toInt(), (9 * dp).toInt(), (2 * dp).toInt())
        chip.background = GradientDrawable().apply {
            cornerRadius = 20 * dp
            setColor(withAlpha(color, 0x2E))
        }
        top.addView(chip)

        val spacer = View(ctx)
        spacer.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        top.addView(spacer)

        if (otp && !item.isPinned) {
            val cd = TextView(ctx)
            cd.textSize = 11f
            cd.setTextColor(ShelfStyle.subTextColor)
            val left = ((ShelfStyle.otpTtlMs - (System.currentTimeMillis() - item.timestamp)) / 1000)
                .coerceAtLeast(0)
            cd.text = "ينتهي بعد ${left}ث"
            top.addView(cd)
            otpLabels.add(Pair(item, cd))
        }

        val pinBtn = TextView(ctx)
        pinBtn.text = if (item.isPinned) "★" else "☆"
        pinBtn.textSize = 18f
        pinBtn.setTextColor(if (item.isPinned) Color.parseColor("#FFD54F") else ShelfStyle.subTextColor)
        pinBtn.setPadding((10 * dp).toInt(), 0, 0, 0)
        pinBtn.setOnClickListener { HistoryRepository.togglePin(item) }
        top.addView(pinBtn)

        val delBtn = TextView(ctx)
        delBtn.text = "✕"
        delBtn.textSize = 14f
        delBtn.setTextColor(ShelfStyle.subTextColor)
        delBtn.setPadding((12 * dp).toInt(), 0, 0, 0)
        delBtn.setOnClickListener { deleteItem(item, card) }
        top.addView(delBtn)
        return top
    }

    private fun buildMeta(item: ShelfItem): TextView {
        val meta = TextView(ctx)
        meta.text = "${item.source} • ${timeFormat.format(Date(item.timestamp))}"
        meta.setTextColor(ShelfStyle.metaColor)
        meta.textSize = 11f
        return meta
    }

    private fun newActionsRow(): LinearLayout {
        val actions = LinearLayout(ctx)
        actions.orientation = LinearLayout.HORIZONTAL
        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        p.topMargin = (10 * dp).toInt()
        actions.layoutParams = p
        return actions
    }

    // السحب للجانب بيحذف (إلا المثبّت)
    private fun attachSwipeDelete(card: View, item: ShelfItem) {
        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffX) > 80 && abs(velocityX) > 80 && abs(diffX) > abs(diffY)) {
                    deleteItem(item, card)
                    return true
                }
                return false
            }
        })
        card.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            resetAutoClose()
            false
        }
    }

    private fun buildItem(item: ShelfItem, index: Int): LinearLayout {
        if (item.imagePath != null) return buildImageCard(item)

        val det = detectOf(item.text)
        val color = ShelfStyle.kindColors[det.kind]
            ?: if (ShelfStyle.rotateTextColors) {
                ShelfStyle.textPalette[index % ShelfStyle.textPalette.size]
            } else {
                ShelfStyle.neutralColor
            }
        val pad = (12 * dp).toInt()

        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(pad, pad, pad, pad)
        card.background = cardBackground(color)
        card.layoutParams = cardLayoutParams()

        card.addView(buildTopRow(item, kindLabel(det.kind), color, card, det.kind == ItemKind.OTP))

        // المحتوى (بنعرض أول جزء بس من النص الطويل)
        val content = TextView(ctx)
        if (det.kind == ItemKind.OTP) {
            content.text = det.value
            content.setTextColor(color)
            content.textSize = 26f
            content.typeface = Typeface.MONOSPACE
            content.letterSpacing = 0.15f
            content.textDirection = View.TEXT_DIRECTION_LTR
        } else {
            val shown = if (item.text.length > ShelfStyle.previewChars) {
                item.text.substring(0, ShelfStyle.previewChars) + "…"
            } else {
                item.text
            }
            content.text = shown
            content.setTextColor(ShelfStyle.textColor)
            content.setTypeface(null, if (ShelfStyle.boldText) Typeface.BOLD else Typeface.NORMAL)
            content.textSize = 14f
            content.maxLines = 3
            content.ellipsize = android.text.TextUtils.TruncateAt.END
        }
        content.setPadding(0, (6 * dp).toInt(), 0, (2 * dp).toInt())
        card.addView(content)
        card.addView(buildMeta(item))

        // أزرار الإجراء حسب النوع
        val actions = newActionsRow()
        when (det.kind) {
            ItemKind.OTP -> {
                actions.addView(pill("نسخ الكود", color, true) { copyText(det.value) })
            }
            ItemKind.LINK -> {
                actions.addView(pill("فتح", color, true) {
                    startExternal(Intent(Intent.ACTION_VIEW, Uri.parse(det.value)))
                })
                actions.addView(pill("نسخ", color, false) { copyText(det.value) })
            }
            ItemKind.PHONE -> {
                actions.addView(pill("اتصال", color, true) {
                    startExternal(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(det.value))))
                })
                actions.addView(pill("واتساب", color, false) {
                    startExternal(Intent(Intent.ACTION_VIEW, whatsappUri(det.value)))
                })
                actions.addView(pill("نسخ", color, false) { copyText(det.value) })
            }
            ItemKind.EMAIL -> {
                actions.addView(pill("مراسلة", color, true) {
                    startExternal(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + det.value)))
                })
                actions.addView(pill("نسخ", color, false) { copyText(det.value) })
            }
            ItemKind.TEXT -> {
            }
        }
        if (actions.childCount > 0) card.addView(actions)

        attachSwipeDelete(card, item)

        // الضغط على الكارت بيدخل النص في الخانة المفتوحة (للكود: الرقم نفسه بس)
        val payload = if (det.kind == ItemKind.OTP) det.value else item.text
        card.setOnClickListener {
            onInsert(payload)
            setExpanded(false)
        }
        card.setOnLongClickListener { v: View ->
            val clip = ClipData.newPlainText("hubsync", payload)
            v.startDragAndDrop(clip, View.DragShadowBuilder(v), INTERNAL_DRAG, View.DRAG_FLAG_GLOBAL)
        }
        return card
    }

    private fun buildImageCard(item: ShelfItem): LinearLayout {
        val path = item.imagePath ?: return LinearLayout(ctx)
        val color = ShelfStyle.imageColor
        val pad = (12 * dp).toInt()

        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(pad, pad, pad, pad)
        card.background = cardBackground(color)
        card.layoutParams = cardLayoutParams()

        card.addView(buildTopRow(item, "صورة", color, card, false))

        val iv = ImageView(ctx)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.tag = path
        iv.background = GradientDrawable().apply {
            cornerRadius = 10 * dp
            setColor(Color.parseColor("#22FFFFFF"))
        }
        iv.clipToOutline = true
        val ivp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (150 * dp).toInt()
        )
        ivp.topMargin = (8 * dp).toInt()
        ivp.bottomMargin = (6 * dp).toInt()
        iv.layoutParams = ivp
        Thumbs.load(path, 520) { bmp ->
            if (iv.tag == path && bmp != null) iv.setImageBitmap(bmp)
        }
        card.addView(iv)

        card.addView(buildMeta(item))

        val actions = newActionsRow()
        actions.addView(pill("مشاركة", color, true) {
            startExternal(ImageStore.shareIntent(ctx, path))
        })
        actions.addView(pill("نسخ", color, false) { copyImage(path) })
        card.addView(actions)

        attachSwipeDelete(card, item)

        card.setOnClickListener {
            copyImage(path)
            setExpanded(false)
        }
        // سحب الصورة لتطبيق تاني (بيشتغل لو التطبيق الهدف بيقبل صور)
        card.setOnLongClickListener { v: View ->
            try {
                val uri = ImageStore.uriFor(ctx, path)
                val clip = ClipData.newUri(ctx.contentResolver, "hubsync-image", uri)
                v.startDragAndDrop(
                    clip,
                    View.DragShadowBuilder(v),
                    INTERNAL_DRAG,
                    View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ
                )
            } catch (e: Exception) {
                false
            }
        }
        return card
    }
}
