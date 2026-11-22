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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.bfbai.superhub.ui.GroupNameActivity
import com.bfbai.superhub.ui.HistoryActivity
import com.bfbai.superhub.utils.Detection
import com.bfbai.superhub.utils.HistoryRepository
import com.bfbai.superhub.utils.ImageStore
import com.bfbai.superhub.utils.ItemKind
import com.bfbai.superhub.utils.PrivacyPolicy
import com.bfbai.superhub.utils.ShelfBehavior
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
import kotlin.math.hypot

data class ShelfItem(
    val text: String,
    val source: String,
    val timestamp: Long = System.currentTimeMillis(),
    var isPinned: Boolean = false,
    val imagePath: String? = null,
    val imageHash: String? = null,
    val groupId: String? = null,
    val groupName: String? = null,
    val pastedAt: Long = 0L
)

internal fun withAlpha(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0x00FFFFFF)

private val PRIVATE_COLOR: Int = Color.parseColor("#FF9800")
private val MANUAL_COLOR: Int = Color.parseColor("#B388FF")
private val GROUP_COLORS: IntArray = intArrayOf(
    Color.parseColor("#4DE1E1"),
    Color.parseColor("#FFB020"),
    Color.parseColor("#C792FF"),
    Color.parseColor("#3DDC97"),
    Color.parseColor("#4DA3FF"),
    Color.parseColor("#FF8FB1")
)

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

    // الكارت اللي اتلصق: درجة الخفوت (1 = من غير خفوت)
    var pastedAlpha: Float = 0.5f

    var blurEnabled: Boolean = true
    var blurRadiusDp: Float = 28f

    // أبعاد الرف
    var closedWidthDp: Float = 24f
    var closedHeightDp: Float = 150f
    var expandedWidthDp: Float = 280f
    var expandedHeightDp: Float = 560f
    var peekHeightDp: Float = 220f
    var dropZoneWidthDp: Float = 120f
    var dropZoneHeightDp: Float = 240f

    // المسافة (dp) اللي لما الرف يقربها من حافة بيلتصق بيها
    var snapThresholdDp: Float = 24f

    var autoCloseMs: Long = 10_000L
    var otpTtlMs: Long = 60_000L

    // أقصى طول نص بيتعرض في الكارت (النص الكامل بيتحفظ كامل)
    var previewChars: Int = 400

    // السحب بيظهر منطقة الإفلات حتى لو الرف مخفي. هيبقى خيار في الإعدادات
    var dragWakesShelf: Boolean = true

    // سحب الحذف: تدرج من أحمر لطيف لأبيض، ونسبة السحب من عرض الكارت اللي بعدها بيتحذف
    var deleteColorStart: Int = Color.parseColor("#F28B82")
    var deleteColorEnd: Int = Color.WHITE
    var swipeThreshold: Float = 0.4f

    // Throw to close: ترمي الرف بسرعة فيختفي
    var throwEnabled: Boolean = true
    // "hide" يخفي الرف بالكامل (بتظهره من الـ Tile)، "collapse" يطويه لشريط
    var throwAction: String = "hide"
    // أقل سرعة (dp في الثانية) تعتبر رمي
    var throwMinSpeedDp: Float = 1200f

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

// حالات الرف: شريط، معاينة، مفتوح، منطقة إفلات
private enum class Mode { BAR, PEEK, OPEN, DROP }

private sealed class Row {
    class Single(val item: ShelfItem) : Row()
    class Group(val id: String, val members: List<ShelfItem>) : Row()
}

/** أيقونة سلة محذوفات مرسومة بالكود، غطاها بيتفتح مع نسبة السحب */
private class TrashView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var progress: Float = 0f
        set(value) {
            field = value
            alpha = value.coerceIn(0f, 1f)
            invalidate()
        }

    init {
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val s = width / 24f
        val p = progress.coerceIn(0f, 1f)
        paint.strokeWidth = 2f * s

        canvas.save()
        val scale = 0.7f + 0.3f * p
        canvas.scale(scale, scale, width / 2f, height / 2f)

        // جسم السلة
        val body = Path()
        body.moveTo(6f * s, 9f * s)
        body.lineTo(7f * s, 20f * s)
        body.quadTo(7.1f * s, 21f * s, 8.2f * s, 21f * s)
        body.lineTo(15.8f * s, 21f * s)
        body.quadTo(16.9f * s, 21f * s, 17f * s, 20f * s)
        body.lineTo(18f * s, 9f * s)
        canvas.drawPath(body, paint)
        canvas.drawLine(10f * s, 12f * s, 10f * s, 18f * s, paint)
        canvas.drawLine(14f * s, 12f * s, 14f * s, 18f * s, paint)

        // الغطا بيتفتح مع السحب
        canvas.save()
        canvas.rotate(-28f * p, 19f * s, 6f * s)
        canvas.drawLine(4f * s, 6f * s, 20f * s, 6f * s, paint)
        canvas.drawLine(9.5f * s, 6f * s, 10.3f * s, 3.6f * s, paint)
        canvas.drawLine(10.3f * s, 3.6f * s, 13.7f * s, 3.6f * s, paint)
        canvas.drawLine(13.7f * s, 3.6f * s, 14.5f * s, 6f * s, paint)
        canvas.restore()

        canvas.restore()
    }
}

/** حاوية بتتحرك فيها الواجهة الأمامية (الكارت) مع السحب الأفقي، والخلفية بتظهر تدريجياً */
private class SwipeLayout(ctx: Context) : FrameLayout(ctx) {
    var swipeEnabled: Boolean = true
    var thresholdFraction: Float = 0.4f
    var onProgress: ((Float, Int) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private val slop: Int = ViewConfiguration.get(ctx).scaledTouchSlop
    private val density: Float = ctx.resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var tracker: VelocityTracker? = null

    private fun front(): View? = if (childCount > 0) getChildAt(childCount - 1) else null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!swipeEnabled) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                dragging = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain()
                tracker?.addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(ev)
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!dragging && abs(dx) > slop * 1.5f && abs(dx) > abs(dy) * 1.4f) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) {
                    tracker?.recycle()
                    tracker = null
                }
            }
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)
        tracker?.addMovement(event)
        val f = front() ?: return true
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                f.translationX = dx
                val p = (abs(dx) / (width * thresholdFraction).coerceAtLeast(1f)).coerceIn(0f, 1f)
                onProgress?.invoke(p, if (dx >= 0) 1 else -1)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                tracker?.recycle()
                tracker = null
                dragging = false
                val dx = f.translationX
                val fast = abs(vx) > 900f * density && abs(dx) > 40f * density && ((vx > 0) == (dx > 0))
                val past = abs(dx) > width * thresholdFraction || fast
                if (event.actionMasked == MotionEvent.ACTION_UP && past) {
                    val dir = if (dx >= 0) 1f else -1f
                    f.animate()
                        .translationX(dir * width)
                        .alpha(0f)
                        .setDuration(160)
                        .withEndAction { onDismiss?.invoke() }
                        .start()
                } else {
                    f.animate().translationX(0f).setDuration(180).start()
                    onProgress?.invoke(0f, 1)
                }
            }
        }
        return true
    }
}

@SuppressLint("ClickableViewAccessibility")
class ShelfController(
    private val ctx: Context,
    private val onInsert: (String) -> Boolean
) {
    private val wm: WindowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val dp: Float = ctx.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    // نسخة للعرض فقط، المصدر الحقيقي هو HistoryRepository
    private val items: ArrayList<ShelfItem> = ArrayList()
    private val shownItems: ArrayList<ShelfItem> = ArrayList()
    private val otpLabels: ArrayList<Pair<ShelfItem, TextView>> = ArrayList()
    private val detectCache: HashMap<String, Detection> = HashMap()
    private val selected: HashSet<String> = HashSet()
    private var selectMode: Boolean = false
    private var manualInfo: HistoryRepository.ManualGroup? = null

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private var mode: Mode = Mode.BAR
    private var added: Boolean = false
    private var userHidden: Boolean = false
    private var dropActive: Boolean = false

    // مكان الرف: الزاوية العلوية الشمال للشريط، وأي حافة ملتصق بيها
    private var posX: Int = 0
    private var posY: Int = 0
    private var snapL: Boolean = false
    private var snapR: Boolean = true
    private var snapT: Boolean = false
    private var snapB: Boolean = false

    private var ignoreOutsideUntil: Long = 0L
    private var tracker: VelocityTracker? = null
    private var snapAnim: ValueAnimator? = null

    var onShownChanged: ((Boolean) -> Unit)? = null
    var appNameProvider: (() -> String)? = null
    var onOpenedByUser: (() -> Unit)? = null
    val isShown: Boolean get() = added && !userHidden

    private val headerPx: Int = (54 * dp).toInt()
    private val selectBarPx: Int = (46 * dp).toInt()

    private val list: LinearLayout = LinearLayout(ctx)
    private val scroll: ScrollView = ScrollView(ctx)
    private val root: FrameLayout = FrameLayout(ctx)
    private val topBar: LinearLayout = LinearLayout(ctx)
    private val titleTv: TextView = TextView(ctx)
    private val countTv: TextView = TextView(ctx)
    private val grip: View = View(ctx)
    private val selectBar: LinearLayout = LinearLayout(ctx)
    private val selectCountTv: TextView = TextView(ctx)
    private val dropTv: TextView = TextView(ctx)
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

    // بيرجّع الرف لشريط بعد المعاينة أو الفتح
    private val collapseRunnable: Runnable = Runnable {
        if (mode == Mode.OPEN || mode == Mode.PEEK) setMode(Mode.BAR)
    }

    // بيحذف أكواد التحقق المنتهية ويحدّث العد التنازلي كل ثانية
    private val ticker: Runnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            var alive = false
            val expired = ArrayList<ShelfItem>()
            for (item in shownItems) {
                if (item.imagePath == null && !item.isPinned && detectOf(item.text).kind == ItemKind.OTP) {
                    if (now - item.timestamp >= ShelfStyle.otpTtlMs) expired.add(item) else alive = true
                }
            }
            for (item in expired) HistoryRepository.remove(item)
            for ((item, tv) in otpLabels) {
                val left = ((ShelfStyle.otpTtlMs - (now - item.timestamp)) / 1000).coerceAtLeast(0)
                tv.text = "ينتهي بعد ${left}ث"
            }
            if (alive) handler.postDelayed(this, 1000)
        }
    }

    private fun px(v: Int): Int = (v * dp).toInt()
    private fun px(v: Float): Int = (v * dp).toInt()

    init {
        ShelfBehavior.init(ctx)
        PrivacyPolicy.init(ctx)
        HistoryRepository.init(ctx)

        val sp = ShelfBehavior.loadPosition()
        if (sp != null) {
            posX = sp.x
            posY = sp.y
            snapL = sp.snapL
            snapR = sp.snapR
            snapT = sp.snapT
            snapB = sp.snapB
        } else {
            posY = px(120)
            snapR = true
        }

        // المحتوى دايماً من اليمين لليسار على الجانبين، والهندسة بتتعكس بالزوايا بس
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL

        // قص المحتوى على زوايا الرف (من غير ركن مربّع)
        root.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (view.width <= 0 || view.height <= 0) {
                    outline.setEmpty()
                    return
                }
                val p = Path()
                p.addRoundRect(
                    RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()),
                    cornerRadii(),
                    Path.Direction.CW
                )
                outline.setConvexPath(p)
            }
        }
        root.clipToOutline = true

        val pad = px(10)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(pad, pad, pad, pad)

        scroll.addView(list)
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.setOnTouchListener { _, _ ->
            resetAutoClose()
            false
        }

        // الترويسة: اسم التطبيق + عدد العناصر + زرار القائمة
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.setPadding(px(14), px(6), px(6), 0)

        val titleBox = LinearLayout(ctx)
        titleBox.orientation = LinearLayout.VERTICAL
        titleBox.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        titleTv.text = "HubSync"
        titleTv.setTextColor(ShelfStyle.textColor)
        titleTv.textSize = 16f
        titleTv.setTypeface(null, Typeface.BOLD)
        countTv.setTextColor(ShelfStyle.subTextColor)
        countTv.textSize = 11f
        titleBox.addView(titleTv)
        titleBox.addView(countTv)

        val menuBtn = TextView(ctx)
        menuBtn.text = "⋮"
        menuBtn.setTextColor(ShelfStyle.accent)
        menuBtn.textSize = 22f
        menuBtn.gravity = Gravity.CENTER
        menuBtn.setPadding(px(14), px(6), px(14), px(6))
        menuBtn.setOnClickListener {
            if (customMenu != null) hideCustomMenu() else showCustomMenu()
        }
        topBar.addView(titleBox)
        topBar.addView(menuBtn)

        // مقبض صغير بيظهر والرف مطوي
        val gripBg = GradientDrawable()
        gripBg.cornerRadius = 3 * dp
        gripBg.setColor(withAlpha(ShelfStyle.accent, 0xCC))
        grip.background = gripBg

        // شريط التحديد (تجميع / إخراج / حذف / إلغاء)
        selectBar.orientation = LinearLayout.HORIZONTAL
        selectBar.gravity = Gravity.CENTER_VERTICAL
        selectBar.setPadding(px(12), px(4), px(8), px(4))
        selectCountTv.textSize = 12f
        selectCountTv.setTextColor(ShelfStyle.subTextColor)
        selectCountTv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        selectBar.addView(selectCountTv)
        selectBar.addView(pill("تجميع", ShelfStyle.accent, true) { doGroupSelected() })
        selectBar.addView(pill("إخراج", ShelfStyle.accent, false) { doUngroupSelected() })
        selectBar.addView(pill("حذف", ShelfStyle.deleteColorStart, false) { doDeleteSelected() })
        selectBar.addView(pill("إلغاء", ShelfStyle.subTextColor, false) { exitSelect(true) })

        // منطقة الإفلات
        dropTv.text = "أفلت\nهنا"
        dropTv.gravity = Gravity.CENTER
        dropTv.textSize = 15f
        dropTv.setTypeface(null, Typeface.BOLD)
        dropTv.setTextColor(ShelfStyle.accent)

        root.addView(grip, FrameLayout.LayoutParams(px(3), px(40), Gravity.CENTER))
        root.addView(topBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, headerPx))
        val sbp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, selectBarPx)
        sbp.topMargin = headerPx
        root.addView(selectBar, sbp)
        root.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            dropTv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // أي تغيير في المخزن (من الرف أو من شاشة السجل) بيتعرض هنا فوراً
        HistoryRepository.items
            .onEach { all ->
                items.clear()
                items.addAll(all)
                render()
            }
            .launchIn(scope)

        HistoryRepository.manualGroup
            .onEach { m ->
                manualInfo = m
                render()
            }
            .launchIn(scope)

        root.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var dragging = false
            private var longPressed = false
            private val longPress = Runnable {
                longPressed = true
                animateDisappear()
            }

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                if (userHidden) return false

                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    // لمسة بعد ما الرف يتفتح بلحظة (مثلاً ضغطة نسخ) ما تقفلوش
                    if (SystemClock.uptimeMillis() < ignoreOutsideUntil) return true
                    if (customMenu != null) {
                        hideCustomMenu()
                    } else if (mode == Mode.OPEN || mode == Mode.PEEK) {
                        if (!selectMode) setMode(Mode.BAR)
                    }
                    return true
                }

                if (customMenu != null) return false
                if (mode == Mode.DROP) return true

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        snapAnim?.cancel()
                        startX = lp.x
                        startY = lp.y
                        touchX = event.rawX
                        touchY = event.rawY
                        dragging = false
                        longPressed = false

                        tracker?.recycle()
                        tracker = VelocityTracker.obtain()
                        track(event)

                        resetAutoClose()
                        if (mode != Mode.PEEK) handler.postDelayed(longPress, 600)
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        track(event)
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()

                        if (abs(dx) > 15 || abs(dy) > 15) {
                            if (!dragging) {
                                dragging = true
                                handler.removeCallbacks(longPress)
                                // وهو عايم كل زواياه مستديرة
                                snapL = false
                                snapR = false
                                snapT = false
                                snapB = false
                                updateBackground()
                            }
                            lp.x = (startX + dx).coerceIn(0, maxOf(0, screenW() - lp.width))
                            lp.y = (startY + dy).coerceIn(0, maxOf(0, screenH() - lp.height))
                            flushLayout()
                            resetAutoClose()
                        }
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        handler.removeCallbacks(longPress)
                        track(event)

                        if (longPressed) {
                            releaseTracker()
                            return true
                        }

                        if (dragging) {
                            // Throw to close: لو سيبت الرف بسرعة عالية يطير ويختفي
                            var thrown = false
                            if (ShelfStyle.throwEnabled) {
                                val t = tracker
                                if (t != null) {
                                    t.computeCurrentVelocity(1000)
                                    val vx = t.xVelocity
                                    val vy = t.yVelocity
                                    if (hypot(vx, vy) > ShelfStyle.throwMinSpeedDp * dp) {
                                        thrown = true
                                        releaseTracker()
                                        finishDrag(false)
                                        throwAway(vx, vy)
                                    }
                                }
                            }
                            if (!thrown) {
                                releaseTracker()
                                finishDrag(true)
                            }
                        } else {
                            releaseTracker()
                            if (mode == Mode.BAR) {
                                setMode(Mode.OPEN)
                                // الضغطة على المقبض = "اقرا الحافظة دلوقتي"
                                onOpenedByUser?.invoke()
                            } else if (mode == Mode.PEEK) {
                                setMode(Mode.OPEN)
                            }
                        }
                        return true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPress)
                        releaseTracker()
                        return true
                    }
                }
                return false
            }
        })

        root.setOnDragListener { _, e -> handleDrag(e) }

        updateTitle()
        applyVisual()
        applyGeometry()
        updateBackground()
    }

    // ---------- الشاشة والهندسة ----------

    private fun screenW(): Int = ctx.resources.displayMetrics.widthPixels
    private fun screenH(): Int = ctx.resources.displayMetrics.heightPixels

    // زوايا الرف: الزاوية اللي على حافة ملتصقة بتتسطّح، والباقي مستدير
    private fun cornerRadii(): FloatArray {
        val r = ShelfStyle.cornerDp * dp
        val tl = if (snapL || snapT) 0f else r
        val tr = if (snapR || snapT) 0f else r
        val br = if (snapR || snapB) 0f else r
        val bl = if (snapL || snapB) 0f else r
        return floatArrayOf(tl, tl, tr, tr, br, br, bl, bl)
    }

    private fun sizeFor(m: Mode): IntArray {
        val sh = screenH()
        return when (m) {
            Mode.BAR -> intArrayOf(
                if (userHidden) px(12) else px(ShelfStyle.closedWidthDp),
                px(ShelfStyle.closedHeightDp)
            )
            Mode.PEEK -> intArrayOf(
                px(ShelfStyle.expandedWidthDp),
                px(ShelfStyle.peekHeightDp).coerceAtMost((sh * 0.5f).toInt())
            )
            Mode.OPEN -> intArrayOf(
                px(ShelfStyle.expandedWidthDp),
                px(ShelfStyle.expandedHeightDp).coerceAtMost((sh * 0.9f).toInt())
            )
            Mode.DROP -> intArrayOf(px(ShelfStyle.dropZoneWidthDp), px(ShelfStyle.dropZoneHeightDp))
        }
    }

    private fun anchorX(w: Int, rawX: Int): Int {
        val sw = screenW()
        return when {
            snapL -> 0
            snapR -> sw - w
            else -> rawX.coerceIn(0, maxOf(0, sw - w))
        }
    }

    private fun anchorY(h: Int, rawY: Int): Int {
        val sh = screenH()
        return when {
            snapT -> 0
            snapB -> sh - h
            else -> rawY.coerceIn(0, maxOf(0, sh - h))
        }
    }

    private fun applyGeometry() {
        val s = sizeFor(mode)
        lp.width = s[0]
        lp.height = s[1]
        lp.x = anchorX(s[0], posX)
        lp.y = anchorY(s[1], posY)
        lp.gravity = Gravity.TOP or Gravity.START
    }

    private fun flushLayout() {
        if (!added) return
        try {
            wm.updateViewLayout(root, lp)
        } catch (e: Exception) {
        }
    }

    // لما تسيب الرف بعد التحريك: يلتصق بأقرب حافة (لو قريب) ويفتكر مكانه
    private fun finishDrag(animate: Boolean) {
        val sw = screenW()
        val sh = screenH()
        val th = px(ShelfStyle.snapThresholdDp)
        val x = lp.x
        val y = lp.y
        val w = lp.width
        val h = lp.height

        snapL = x <= th
        snapR = !snapL && x + w >= sw - th
        snapT = y <= th
        snapB = !snapT && y + h >= sh - th
        posX = x
        posY = y

        updateBackground()
        val tx = anchorX(w, x)
        val ty = anchorY(h, y)
        if (animate) {
            animateTo(tx, ty)
        } else {
            lp.x = tx
            lp.y = ty
            flushLayout()
        }
        ShelfBehavior.savePosition(posX, posY, snapL, snapR, snapT, snapB)
    }

    private fun animateTo(tx: Int, ty: Int) {
        snapAnim?.cancel()
        val sx = lp.x
        val sy = lp.y
        if (sx == tx && sy == ty) return
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 140
        a.addUpdateListener { v ->
            val t = v.animatedValue as Float
            lp.x = (sx + (tx - sx) * t).toInt()
            lp.y = (sy + (ty - sy) * t).toInt()
            flushLayout()
        }
        snapAnim = a
        a.start()
    }

    // لون واحد شفاف (التدرج خيار) + حد خفيف. والمطوي لون المقبض
    private fun updateBackground() {
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        val open = mode == Mode.OPEN || mode == Mode.PEEK
        if (open && mode == Mode.OPEN && ShelfStyle.panelGradient) {
            bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM)
            bg.setColors(intArrayOf(ShelfStyle.panelTop, ShelfStyle.panelBottom))
        } else {
            val fill = when {
                mode == Mode.DROP -> withAlpha(ShelfStyle.accent, if (dropActive) 0x70 else 0x38)
                open -> ShelfStyle.panelColor
                else -> ShelfStyle.handleColor
            }
            bg.setColor(fill)
        }
        bg.cornerRadii = cornerRadii()
        val strokeColor = when {
            mode == Mode.DROP -> withAlpha(ShelfStyle.accent, if (dropActive) 0xFF else 0xB0)
            open -> ShelfStyle.panelStroke
            else -> withAlpha(ShelfStyle.accent, 0x80)
        }
        bg.setStroke(if (mode == Mode.DROP) px(2) else px(1), strokeColor)
        root.background = bg
        root.invalidateOutline()
    }

    // ضبابية خلف الرف (أندرويد 12 وما فوق، وبس لو الجهاز بيدعمها)
    private fun applyBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            if (mode == Mode.OPEN && ShelfStyle.blurEnabled && wm.isCrossWindowBlurEnabled) {
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                lp.blurBehindRadius = (ShelfStyle.blurRadiusDp * dp).toInt()
            } else {
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                lp.blurBehindRadius = 0
            }
        } catch (e: Throwable) {
        }
    }

    // بيظبط إيه اللي ظاهر في كل حالة
    private fun applyVisual() {
        val open = mode == Mode.OPEN
        topBar.visibility = if (open) View.VISIBLE else View.GONE
        selectBar.visibility = if (open && selectMode) View.VISIBLE else View.GONE
        scroll.visibility = if (open || mode == Mode.PEEK) View.VISIBLE else View.GONE
        grip.visibility = if (mode == Mode.BAR) View.VISIBLE else View.GONE
        dropTv.visibility = if (mode == Mode.DROP) View.VISIBLE else View.GONE

        val sp = scroll.layoutParams as FrameLayout.LayoutParams
        sp.topMargin = if (open) headerPx + (if (selectMode) selectBarPx else 0) else 0
        scroll.layoutParams = sp

        root.visibility = View.VISIBLE
        root.alpha = if (userHidden && mode == Mode.BAR) 0f else 1f
        updateTitle()
    }

    private fun setMode(m: Mode) {
        handler.removeCallbacks(collapseRunnable)
        mode = m
        if (m != Mode.OPEN) {
            hideCustomMenu()
            if (selectMode) exitSelect(true)
        }
        if (m == Mode.OPEN || m == Mode.PEEK) {
            ignoreOutsideUntil = SystemClock.uptimeMillis() + 1200
        }
        applyVisual()
        applyGeometry()
        updateBackground()
        applyBlur()
        flushLayout()
        if (m == Mode.PEEK) scroll.scrollTo(0, 0)
        resetAutoClose()
    }

    private fun resetAutoClose() {
        handler.removeCallbacks(collapseRunnable)
        if (selectMode) return
        when (mode) {
            Mode.OPEN -> handler.postDelayed(collapseRunnable, ShelfStyle.autoCloseMs)
            Mode.PEEK -> handler.postDelayed(collapseRunnable, ShelfBehavior.peekMs.toLong())
            else -> {
            }
        }
    }

    private fun dbgToast(msg: String) {
        if (ShelfStyle.debug) Toast.makeText(ctx, "HubSync: $msg", Toast.LENGTH_SHORT).show()
    }

    // ---------- الوضع الخاص: مؤشر في الترويسة وفي لون المقبض ----------

    private fun updateTitle() {
        val priv = PrivacyPolicy.isPrivateMode()
        titleTv.text = if (priv) "HubSync • وضع خاص" else "HubSync"
        titleTv.setTextColor(if (priv) PRIVATE_COLOR else ShelfStyle.textColor)
        (grip.background as? GradientDrawable)?.setColor(
            withAlpha(if (priv) PRIVATE_COLOR else ShelfStyle.accent, 0xCC)
        )
    }

    private fun privateLabel(): String =
        if (PrivacyPolicy.isPrivateMode()) "إيقاف الوضع الخاص" else "وضع خاص (30 دقيقة)"

    private fun togglePrivate() {
        if (PrivacyPolicy.isPrivateMode()) {
            PrivacyPolicy.stopPrivate()
            Toast.makeText(ctx, "الوضع الخاص اتقفل، الالتقاط رجع", Toast.LENGTH_SHORT).show()
        } else {
            PrivacyPolicy.startPrivate(30)
            Toast.makeText(ctx, "الوضع الخاص شغال 30 دقيقة: مش هلتقط أي نسخ", Toast.LENGTH_LONG).show()
        }
        updateTitle()
    }

    // ---------- Throw to close ----------

    // الإحداثيات الخام (rawX/rawY)، لأن النافذة نفسها بتتحرك مع الإصبع
    private fun track(e: MotionEvent) {
        val t = tracker ?: return
        val c = MotionEvent.obtain(e)
        c.setLocation(e.rawX, e.rawY)
        t.addMovement(c)
        c.recycle()
    }

    private fun releaseTracker() {
        tracker?.recycle()
        tracker = null
    }

    private fun throwAway(vx: Float, vy: Float) {
        if (!added) return
        val horizontal = abs(vx) >= abs(vy)
        val dirX = if (horizontal) (if (vx > 0) 1f else -1f) else 0f
        val dirY = if (!horizontal) (if (vy > 0) 1f else -1f) else 0f
        val baseX = lp.x
        val baseY = lp.y
        val travelX = (screenW() + lp.width).toFloat()
        val travelY = (screenH() + lp.height).toFloat()

        handler.removeCallbacks(collapseRunnable)
        hideCustomMenu()

        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 260
        anim.interpolator = AccelerateInterpolator(1.4f)
        anim.addUpdateListener { a ->
            if (!added) return@addUpdateListener
            val t = a.animatedValue as Float
            lp.x = (baseX + dirX * travelX * t).toInt()
            lp.y = (baseY + dirY * travelY * t).toInt()
            root.alpha = 1f - t
            flushLayout()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                root.alpha = 1f
                if (ShelfStyle.throwAction == "collapse") {
                    setMode(Mode.BAR)
                } else {
                    hide()
                    dbgToast("الرف اتخبى، دوس على الـ Tile لإظهاره")
                }
            }
        })
        anim.start()
    }

    // الضغط المطوّل: الرف يتلاشى ويختفي
    private fun animateDisappear() {
        val startY = lp.y
        val anim = ValueAnimator.ofFloat(1f, 0f)
        anim.duration = 400
        anim.addUpdateListener { animation ->
            if (!added) return@addUpdateListener
            val value = animation.animatedValue as Float
            root.alpha = value
            lp.y = startY + ((1f - value) * (40 * dp)).toInt()
            flushLayout()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                root.alpha = 1f
                hide()
            }
        })
        anim.start()
    }

    // ---------- التقاط الحافظة بالتركيز ----------

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
        handler.postDelayed({
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
                flushLayout()
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
                // سحب خارج من الرف نفسه: مش بنقبله تاني
                if (e.localState == INTERNAL_DRAG) return false
                if (!isAcceptable(e.clipDescription)) {
                    dbgToast("سحب مش نص ولا صورة")
                    return false
                }
                if (userHidden && !ShelfStyle.dragWakesShelf) return false
                dropActive = false
                // منطقة إفلات صغيرة بدل ما الرف يتفتح كله
                if (mode != Mode.OPEN) setMode(Mode.DROP)
                dbgToast("بدأ سحب: منطقة الإفلات ظهرت")
                return true
            }

            DragEvent.ACTION_DRAG_ENTERED -> {
                dropActive = true
                updateBackground()
                return true
            }

            DragEvent.ACTION_DRAG_EXITED -> {
                dropActive = false
                updateBackground()
                return true
            }

            DragEvent.ACTION_DROP -> {
                dropActive = false
                val cd = e.clipData
                if (cd != null) {
                    val m = if (cd.description.mimeTypeCount > 0) cd.description.getMimeType(0) else "?"
                    dbgToast("إفلات: $m")
                    for (i in 0 until cd.itemCount) {
                        handleDropped(cd.getItemAt(i), cd.description)
                    }
                } else {
                    dbgToast("إفلات من غير بيانات")
                }
                return true
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                dropActive = false
                if (mode == Mode.DROP) setMode(Mode.BAR)
                return true
            }
        }
        return true
    }

    private fun handleDropped(item: ClipData.Item, desc: ClipDescription?) {
        val uri = item.uri
        val imageMime = desc?.hasMimeType("image/*") == true

        // سحب صورة من غير رابط: مفيش حاجة نقراها
        if (imageMime && uri == null) {
            Toast.makeText(
                ctx,
                "الصورة اتسحبت من غير رابط يتقري. استخدم المشاركة (Share)",
                Toast.LENGTH_LONG
            ).show()
            return
        }

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
            dbgToast("صورة من ${uri.authority ?: "?"}")
            Thread {
                val path = ImageStore.saveFromUri(ctx, uri)
                handler.post {
                    if (path != null) {
                        HistoryRepository.addImage(path, src)
                        notifyNew("صورة مسحوبة")
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
            if (t != null) add(t, appNameProvider?.invoke() ?: "سحب")
        }
    }

    // ---------- التحكم من برّه ----------

    fun show() {
        if (!added) {
            applyGeometry()
            wm.addView(root, lp)
            added = true
        }
        userHidden = false
        setMode(Mode.BAR)
        onShownChanged?.invoke(true)
    }

    // الإخفاء: النافذة بتفضل موجودة وشفافة تماماً وضيقة، عشان السحب يفضل شغال
    fun hide() {
        if (!added) return
        userHidden = true
        setMode(Mode.BAR)
        onShownChanged?.invoke(false)
    }

    fun destroy() {
        onShownChanged = null
        onOpenedByUser = null
        handler.removeCallbacksAndMessages(null)
        snapAnim?.cancel()
        releaseTracker()
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
        popOpen()
    }

    // فتح كامل صريح (مثلاً بعد مشاركة): بيظهر الرف لو كان مخفي
    fun popOpen() {
        if (!added) return
        if (userHidden) show()
        setMode(Mode.OPEN)
    }

    // وصل عنصر جديد: الفتح بيتبع إعداد "openOnCopy"، والرف المخفي ما بيظهرش
    fun notifyNew(reason: String = "عنصر جديد") {
        if (!added || userHidden || selectMode) return
        when (ShelfBehavior.openOnCopy) {
            ShelfBehavior.OPEN_FULL -> {
                dbgToast("الرف اتفتح: $reason")
                setMode(Mode.OPEN)
            }
            ShelfBehavior.OPEN_PULSE -> pulse()
            else -> {
                if (mode != Mode.OPEN) {
                    dbgToast("معاينة: $reason")
                    setMode(Mode.PEEK)
                } else {
                    resetAutoClose()
                }
            }
        }
    }

    private fun pulse() {
        grip.animate().cancel()
        grip.animate().scaleX(3f).scaleY(1.25f).setDuration(240).withEndAction {
            grip.animate().scaleX(1f).scaleY(1f).setDuration(240).start()
        }.start()
    }

    fun add(text: String, source: String = "تطبيق", popUp: Boolean = true) {
        val t = text.trim()
        if (t.isEmpty()) return
        HistoryRepository.add(t, source)
        if (popUp) notifyNew("نسخ جديد من $source")
    }

    fun setExpanded(on: Boolean) {
        setMode(if (on) Mode.OPEN else Mode.BAR)
    }

    // ---------- العرض ----------

    private fun rowTime(r: Row): Long = when (r) {
        is Row.Single -> r.item.timestamp
        is Row.Group -> r.members.maxOf { it.timestamp }
    }

    private fun rowPinned(r: Row): Boolean = when (r) {
        is Row.Single -> r.item.isPinned
        is Row.Group -> r.members.any { it.isPinned }
    }

    private fun rowAllPasted(r: Row): Boolean = when (r) {
        is Row.Single -> r.item.pastedAt > 0L
        is Row.Group -> r.members.all { it.pastedAt > 0L }
    }

    private fun membersOf(r: Row): List<ShelfItem> = when (r) {
        is Row.Single -> listOf(r.item)
        is Row.Group -> r.members
    }

    // الترتيب: المثبّت الأول، وبعدين (لو مفعّل) اللي لسه ما اتلصقش، وبعدين الأحدث نسخاً.
    // اللصق نفسه ما بيغيّرش الترتيب.
    private fun buildRows(): List<Row> {
        val groups = LinkedHashMap<String, ArrayList<ShelfItem>>()
        val rows = ArrayList<Row>()
        for (i in items) {
            val g = i.groupId
            if (g == null) {
                rows.add(Row.Single(i))
            } else {
                groups.getOrPut(g) { ArrayList() }.add(i)
            }
        }
        for ((id, members) in groups) {
            rows.add(Row.Group(id, members.sortedBy { it.timestamp }))
        }
        val sorted = rows.sortedWith(
            compareByDescending<Row> { rowPinned(it) }
                .thenBy { if (ShelfBehavior.sortUnpastedFirst && rowAllPasted(it)) 1 else 0 }
                .thenByDescending { rowTime(it) }
        )
        val out = ArrayList<Row>()
        var count = 0
        for (r in sorted) {
            out.add(r)
            count += membersOf(r).size
            if (count >= 80) break
        }
        return out
    }

    private fun render() {
        otpLabels.clear()
        shownItems.clear()
        list.removeAllViews()
        countTv.text = if (items.isEmpty()) "فارغ" else "${items.size} عنصر"
        selectCountTv.text = "${selected.size} محدد"

        val m = manualInfo
        if (m != null) list.addView(buildManualBanner(m))

        val rows = buildRows()
        if (rows.isEmpty()) {
            val empty = TextView(ctx)
            empty.text = "الرف فارغ\nانسخ أي نص أو اسحبه هنا"
            empty.setTextColor(ShelfStyle.subTextColor)
            empty.textSize = 13f
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, px(40), 0, 0)
            list.addView(empty)
        } else {
            var index = 0
            if (selectMode) {
                // وضع التحديد: كل العناصر منفردة عشان تقدر تختارها
                for (r in rows) {
                    for (mem in membersOf(r)) {
                        shownItems.add(mem)
                        list.addView(buildItem(mem, index, false))
                        index++
                    }
                }
            } else {
                for (r in rows) {
                    when (r) {
                        is Row.Single -> {
                            shownItems.add(r.item)
                            list.addView(buildItem(r.item, index, false))
                            index++
                        }
                        is Row.Group -> {
                            shownItems.addAll(r.members)
                            list.addView(buildGroup(r))
                        }
                    }
                }
            }
        }

        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    // ---------- التحديد ----------

    private fun keyOf(item: ShelfItem): String =
        "${item.timestamp}|${item.imagePath ?: item.text.hashCode().toString()}"

    private fun toggleSelected(item: ShelfItem) {
        val k = keyOf(item)
        if (!selected.remove(k)) selected.add(k)
        render()
    }

    private fun selectedItems(): List<ShelfItem> = items.filter { selected.contains(keyOf(it)) }

    private fun enterSelect() {
        selectMode = true
        selected.clear()
        handler.removeCallbacks(collapseRunnable)
        applyVisual()
        flushLayout()
        render()
    }

    private fun exitSelect(rerender: Boolean) {
        if (!selectMode) return
        selectMode = false
        selected.clear()
        applyVisual()
        if (rerender) render()
        resetAutoClose()
    }

    private fun doGroupSelected() {
        val sel = selectedItems()
        if (sel.size < 2) {
            Toast.makeText(ctx, "اختار عنصرين على الأقل", Toast.LENGTH_SHORT).show()
            return
        }
        val gid = HistoryRepository.createGroup(sel, null)
        exitSelect(true)
        openNameDialog(GroupNameActivity.MODE_NAME_NEW, gid, null)
    }

    private fun doUngroupSelected() {
        val sel = selectedItems().filter { it.groupId != null }
        if (sel.isEmpty()) {
            Toast.makeText(ctx, "مفيش عناصر مجمّعة في اللي اخترته", Toast.LENGTH_SHORT).show()
            return
        }
        HistoryRepository.removeFromGroup(sel)
        exitSelect(true)
    }

    private fun doDeleteSelected() {
        val sel = selectedItems().filter { !it.isPinned }
        for (s in sel) HistoryRepository.remove(s)
        exitSelect(true)
    }

    private fun openNameDialog(dialogMode: String, groupId: String?, current: String?) {
        setMode(Mode.BAR)
        val i = Intent(ctx, GroupNameActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.putExtra(GroupNameActivity.EXTRA_MODE, dialogMode)
        i.putExtra(GroupNameActivity.EXTRA_GROUP, groupId)
        i.putExtra(GroupNameActivity.EXTRA_NAME, current)
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(ctx, "تعذر فتح شاشة التسمية", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- القايمة ----------

    private fun showCustomMenu() {
        if (mode != Mode.OPEN) return
        hideCustomMenu()

        val menu = LinearLayout(ctx)
        menu.orientation = LinearLayout.VERTICAL
        val mp = px(8)
        menu.setPadding(mp, mp, mp, mp)
        val mbg = GradientDrawable()
        mbg.cornerRadius = 14 * dp
        mbg.setColor(Color.parseColor("#F20E1F42"))
        mbg.setStroke(px(1), ShelfStyle.panelStroke)
        menu.background = mbg

        val lpMenu = FrameLayout.LayoutParams(
            px(210),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        )
        lpMenu.topMargin = px(50)
        lpMenu.marginEnd = px(10)
        menu.layoutParams = lpMenu

        val entries = ArrayList<Pair<String, String>>()
        entries.add("history" to "السجل")
        entries.add("select" to "تحديد وتجميع")
        entries.add("group" to (if (manualInfo == null) "مجموعة جديدة…" else "إنهاء المجموعة الجارية"))
        entries.add("private" to privateLabel())
        entries.add("settings" to "الإعدادات")
        entries.add("close" to "إغلاق")

        for ((index, entry) in entries.withIndex()) {
            val key = entry.first
            val tv = TextView(ctx)
            tv.text = entry.second
            tv.setTextColor(
                when (key) {
                    "private" -> PRIVATE_COLOR
                    "group" -> MANUAL_COLOR
                    else -> ShelfStyle.textColor
                }
            )
            tv.textSize = 15f
            tv.setPadding(px(14), px(12), px(14), px(12))
            tv.setOnClickListener {
                hideCustomMenu()
                when (key) {
                    "history" -> {
                        setMode(Mode.BAR)
                        val intent = Intent(ctx, HistoryActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            ctx.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "افتح التطبيق من الأيقونة", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "select" -> enterSelect()
                    "group" -> {
                        if (manualInfo == null) {
                            openNameDialog(GroupNameActivity.MODE_START, null, null)
                        } else {
                            HistoryRepository.endManualGroup()
                            Toast.makeText(ctx, "المجموعة اتقفلت", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "private" -> togglePrivate()
                    "settings" -> Toast.makeText(ctx, "الإعدادات قريباً", Toast.LENGTH_SHORT).show()
                    "close" -> setMode(Mode.BAR)
                }
            }
            menu.addView(tv)

            if (index < entries.size - 1) {
                val divider = View(ctx)
                divider.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    px(1)
                )
                divider.setBackgroundColor(withAlpha(ShelfStyle.accent, 0x26))
                menu.addView(divider)
            }
        }
        customMenu = menu
        root.addView(menu)
    }

    private fun hideCustomMenu() {
        val m = customMenu ?: return
        root.removeView(m)
        customMenu = null
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
        setMode(Mode.BAR)
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

    private fun payloadOf(item: ShelfItem): String {
        val det = detectOf(item.text)
        return if (det.kind == ItemKind.OTP) det.value else item.text
    }

    // لصق عنصر في الخانة المفتوحة: لو نجح بنعلّمه "اتلصق" من غير ما نغيّر مكانه
    private fun insertItem(item: ShelfItem) {
        val ok = onInsert(payloadOf(item))
        if (ok) {
            if (ShelfBehavior.autoRemoveAfterPaste && !item.isPinned) {
                HistoryRepository.remove(item)
            } else {
                HistoryRepository.markPasted(item, true)
            }
        }
        setMode(Mode.BAR)
    }

    // لصق بالترتيب: أول عنصر في المجموعة لسه ما اتلصقش
    private fun pasteNext(members: List<ShelfItem>) {
        val next = members.firstOrNull { it.pastedAt == 0L && it.imagePath == null }
        if (next == null) {
            Toast.makeText(
                ctx,
                "كل عناصر المجموعة اتلصقت. دوس على علامة ✓ في أي كارت لإعادته",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        insertItem(next)
    }

    // حذف بزرار ✕: بنشيل الكارت (وحاويته) من الشاشة الأول وبعدين من المخزن
    private fun deleteItem(item: ShelfItem, card: View) {
        if (item.isPinned) {
            Toast.makeText(ctx, "ألغِ التثبيت الأول", Toast.LENGTH_SHORT).show()
            return
        }
        val container = (card.parent as? View) ?: card
        (container.parent as? LinearLayout)?.removeView(container)
        HistoryRepository.remove(item)
    }

    // حذف بالسحب
    private fun dismissItem(item: ShelfItem, container: View) {
        (container.parent as? LinearLayout)?.removeView(container)
        HistoryRepository.remove(item)
    }

    private fun pill(label: String, color: Int, primary: Boolean, onClick: () -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = label
        tv.textSize = 12f
        tv.setTypeface(null, if (primary) Typeface.BOLD else Typeface.NORMAL)
        tv.setPadding(px(14), px(6), px(14), px(6))
        val b = GradientDrawable()
        b.cornerRadius = 20 * dp
        if (primary) {
            b.setColor(withAlpha(color, 0xE6))
            tv.setTextColor(Color.parseColor("#0A1730"))
        } else {
            b.setColor(Color.TRANSPARENT)
            b.setStroke(px(1), withAlpha(color, 0x99))
            tv.setTextColor(color)
        }
        tv.background = b
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = px(6)
        tv.layoutParams = params
        tv.setOnClickListener {
            resetAutoClose()
            onClick()
        }
        return tv
    }

    // ---------- شكل الكروت ----------

    private fun cardBackground(color: Int, isSelected: Boolean): GradientDrawable {
        val fill = if (ShelfStyle.cardTintByKind) {
            withAlpha(color, ShelfStyle.cardFillAlpha)
        } else {
            withAlpha(ShelfStyle.cardFillColor, ShelfStyle.cardFillAlpha)
        }
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = ShelfStyle.cardCornerDp * dp
        if (ShelfStyle.cardGradient) {
            bg.setOrientation(GradientDrawable.Orientation.RIGHT_LEFT)
            bg.setColors(intArrayOf(fill, withAlpha(fill, ShelfStyle.cardGradientEndAlpha)))
        } else {
            bg.setColor(fill)
        }
        if (isSelected) {
            bg.setStroke(px(2), ShelfStyle.accent)
        } else {
            bg.setStroke(px(1), withAlpha(ShelfStyle.cardStrokeColor, ShelfStyle.cardStrokeAlpha))
        }
        return bg
    }

    private fun cardLayoutParams(compact: Boolean): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        p.setMargins(0, 0, 0, if (compact) px(6) else px(10))
        return p
    }

    // الصف الأول: (مربع تحديد) + شارة النوع + علامة اتلصق + عد تنازلي + نجمة + حذف
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

        if (selectMode) {
            val cb = TextView(ctx)
            cb.text = if (selected.contains(keyOf(item))) "☑" else "☐"
            cb.textSize = 20f
            cb.setTextColor(ShelfStyle.accent)
            cb.setPadding(0, 0, px(8), 0)
            top.addView(cb)
        }

        val chip = TextView(ctx)
        chip.text = label
        chip.textSize = 11f
        chip.setTextColor(color)
        chip.setTypeface(null, Typeface.BOLD)
        chip.setPadding(px(9), px(2), px(9), px(2))
        val chipBg = GradientDrawable()
        chipBg.cornerRadius = 20 * dp
        chipBg.setColor(withAlpha(color, 0x2E))
        chip.background = chipBg
        top.addView(chip)

        // علامة "اتلصق": دوس عليها تلغيها
        if (item.pastedAt > 0L && !selectMode) {
            val pc = TextView(ctx)
            pc.text = "✓ اتلصق"
            pc.textSize = 10.5f
            pc.setTextColor(ShelfStyle.accent)
            pc.setPadding(px(8), px(2), px(8), px(2))
            val pcBg = GradientDrawable()
            pcBg.cornerRadius = 20 * dp
            pcBg.setColor(withAlpha(ShelfStyle.accent, 0x22))
            pc.background = pcBg
            val pcp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            pcp.marginStart = px(6)
            pc.layoutParams = pcp
            pc.setOnClickListener { HistoryRepository.markPasted(item, false) }
            top.addView(pc)
        }

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

        if (!selectMode) {
            val pinBtn = TextView(ctx)
            pinBtn.text = if (item.isPinned) "★" else "☆"
            pinBtn.textSize = 18f
            pinBtn.setTextColor(if (item.isPinned) Color.parseColor("#FFD54F") else ShelfStyle.subTextColor)
            pinBtn.setPadding(px(10), 0, 0, 0)
            pinBtn.setOnClickListener { HistoryRepository.togglePin(item) }
            top.addView(pinBtn)

            val delBtn = TextView(ctx)
            delBtn.text = "✕"
            delBtn.textSize = 14f
            delBtn.setTextColor(ShelfStyle.subTextColor)
            delBtn.setPadding(px(12), 0, 0, 0)
            delBtn.setOnClickListener { deleteItem(item, card) }
            top.addView(delBtn)
        }
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
        p.topMargin = px(10)
        actions.layoutParams = p
        return actions
    }

    // بيلف الكارت في حاوية سحب: خلفية متدرجة (أحمر لطيف ← أبيض) + سلة بتظهر تدريجياً مع السحب
    private fun wrapSwipe(card: View, item: ShelfItem, compact: Boolean): View {
        val frame = SwipeLayout(ctx)
        frame.layoutParams = cardLayoutParams(compact)
        frame.swipeEnabled = !item.isPinned && !selectMode
        frame.thresholdFraction = ShelfStyle.swipeThreshold

        val redDrawable = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(ShelfStyle.deleteColorStart, ShelfStyle.deleteColorEnd)
        )
        redDrawable.cornerRadius = ShelfStyle.cardCornerDp * dp
        val redBg = View(ctx)
        redBg.background = redDrawable
        redBg.alpha = 0f
        frame.addView(
            redBg,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val trash = TrashView(ctx)
        val size = px(30)
        val tp = FrameLayout.LayoutParams(size, size, Gravity.CENTER_VERTICAL or Gravity.LEFT)
        tp.leftMargin = px(20)
        tp.rightMargin = px(20)
        trash.layoutParams = tp
        trash.progress = 0f
        frame.addView(trash)

        card.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        frame.addView(card)

        frame.onProgress = { p, dir ->
            redBg.alpha = p * 0.95f
            trash.progress = p
            // الأحمر عند ناحية السلة (جامد) ويتدرج لأبيض ناحية الكارت
            redDrawable.orientation = if (dir > 0) {
                GradientDrawable.Orientation.LEFT_RIGHT
            } else {
                GradientDrawable.Orientation.RIGHT_LEFT
            }
            val params = trash.layoutParams as FrameLayout.LayoutParams
            val g = Gravity.CENTER_VERTICAL or (if (dir > 0) Gravity.LEFT else Gravity.RIGHT)
            if (params.gravity != g) {
                params.gravity = g
                trash.layoutParams = params
            }
        }
        frame.onDismiss = { dismissItem(item, frame) }
        return frame
    }

    private fun buildItem(item: ShelfItem, index: Int, compact: Boolean): View {
        if (item.imagePath != null) return buildImageCard(item, compact)

        val det = detectOf(item.text)
        val color = ShelfStyle.kindColors[det.kind]
            ?: if (ShelfStyle.rotateTextColors) {
                ShelfStyle.textPalette[index % ShelfStyle.textPalette.size]
            } else {
                ShelfStyle.neutralColor
            }
        val pad = px(12)

        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(pad, pad, pad, pad)
        card.background = cardBackground(color, selectMode && selected.contains(keyOf(item)))
        if (item.pastedAt > 0L && ShelfBehavior.pasteDim) card.alpha = ShelfStyle.pastedAlpha

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
        content.setPadding(0, px(6), 0, px(2))
        card.addView(content)
        card.addView(buildMeta(item))

        // أزرار الإجراء حسب النوع (مش بتظهر في وضع التحديد)
        if (!selectMode) {
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
        }

        // الضغط على الكارت بيلصق النص في الخانة المفتوحة (أو بيحدّده في وضع التحديد)
        card.setOnClickListener {
            if (selectMode) toggleSelected(item) else insertItem(item)
        }
        if (!selectMode) {
            card.setOnLongClickListener { v: View ->
                val clip = ClipData.newPlainText("hubsync", payloadOf(item))
                v.startDragAndDrop(clip, View.DragShadowBuilder(v), INTERNAL_DRAG, View.DRAG_FLAG_GLOBAL)
            }
        }
        return wrapSwipe(card, item, compact)
    }

    private fun buildImageCard(item: ShelfItem, compact: Boolean): View {
        val path = item.imagePath ?: return LinearLayout(ctx)
        val color = ShelfStyle.imageColor
        val pad = px(12)

        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(pad, pad, pad, pad)
        card.background = cardBackground(color, selectMode && selected.contains(keyOf(item)))

        card.addView(buildTopRow(item, "صورة", color, card, false))

        val iv = ImageView(ctx)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.tag = path
        val ivBg = GradientDrawable()
        ivBg.cornerRadius = 10 * dp
        ivBg.setColor(Color.parseColor("#22FFFFFF"))
        iv.background = ivBg
        iv.clipToOutline = true
        val ivp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            px(150)
        )
        ivp.topMargin = px(8)
        ivp.bottomMargin = px(6)
        iv.layoutParams = ivp
        Thumbs.load(path, 520) { bmp ->
            if (iv.tag == path && bmp != null) iv.setImageBitmap(bmp)
        }
        card.addView(iv)

        card.addView(buildMeta(item))

        if (!selectMode) {
            val actions = newActionsRow()
            actions.addView(pill("مشاركة", color, true) {
                startExternal(ImageStore.shareIntent(ctx, path))
            })
            actions.addView(pill("نسخ", color, false) { copyImage(path) })
            card.addView(actions)
        }

        card.setOnClickListener {
            if (selectMode) {
                toggleSelected(item)
            } else {
                copyImage(path)
                setMode(Mode.BAR)
            }
        }
        // سحب الصورة لتطبيق تاني (بيشتغل لو التطبيق الهدف بيقبل صور)
        if (!selectMode) {
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
        }
        return wrapSwipe(card, item, compact)
    }

    // ---------- المجموعات ----------

    private fun buildManualBanner(m: HistoryRepository.ManualGroup): View {
        val count = items.count { it.groupId == m.id }
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(px(12), px(8), px(8), px(8))
        val bg = GradientDrawable()
        bg.cornerRadius = px(12).toFloat()
        bg.setColor(withAlpha(MANUAL_COLOR, 0x26))
        bg.setStroke(px(1), withAlpha(MANUAL_COLOR, 0xAA))
        row.background = bg
        row.layoutParams = cardLayoutParams(false)

        val tv = TextView(ctx)
        val nm = m.name
        tv.text = "مجموعة قيد الإنشاء" + (if (nm != null) ": $nm" else "") + " • $count"
        tv.textSize = 12f
        tv.setTextColor(MANUAL_COLOR)
        tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(tv)
        row.addView(pill("إنهاء", MANUAL_COLOR, true) { HistoryRepository.endManualGroup() })
        return row
    }

    private fun buildGroup(r: Row.Group): View {
        val id = r.id
        val members = r.members
        val color = GROUP_COLORS[(id.hashCode() and 0x7fffffff) % GROUP_COLORS.size]
        val collapsed = ShelfBehavior.isCollapsed(id)
        val hasFile = ShelfBehavior.groupCodeFile &&
            members.any { HistoryRepository.fileNameOf(it.text) != null }
        val explicit = members.firstOrNull { !it.groupName.isNullOrBlank() }?.groupName
        val auto = if (ShelfBehavior.groupAutoName) {
            members.firstNotNullOfOrNull { HistoryRepository.fileNameOf(it.text) }
        } else {
            null
        }
        val title = explicit ?: auto ?: "مجموعة"

        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(px(8), px(8), px(8), px(2))
        val boxBg = GradientDrawable()
        boxBg.cornerRadius = ShelfStyle.cardCornerDp * dp + px(2)
        boxBg.setColor(withAlpha(color, 0x1A))
        boxBg.setStroke(px(1), withAlpha(color, 0x99))
        box.background = boxBg
        box.layoutParams = cardLayoutParams(false)

        // الترويسة
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(px(4), px(2), px(4), px(8))

        val chev = TextView(ctx)
        chev.text = if (collapsed) "▸" else "▾"
        chev.textSize = 16f
        chev.setTextColor(color)
        chev.setPadding(0, 0, px(6), 0)
        head.addView(chev)

        if (hasFile) {
            val kc = TextView(ctx)
            kc.text = "ملف كود"
            kc.textSize = 10.5f
            kc.setTextColor(color)
            kc.setTypeface(null, Typeface.BOLD)
            kc.setPadding(px(8), px(2), px(8), px(2))
            val kcBg = GradientDrawable()
            kcBg.cornerRadius = 20 * dp
            kcBg.setColor(withAlpha(color, 0x2E))
            kc.background = kcBg
            val kcp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            kcp.marginEnd = px(6)
            kc.layoutParams = kcp
            head.addView(kc)
        }

        val titleView = TextView(ctx)
        titleView.text = title
        titleView.textSize = 14f
        titleView.setTextColor(ShelfStyle.textColor)
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.maxLines = 1
        titleView.ellipsize = android.text.TextUtils.TruncateAt.END
        titleView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        head.addView(titleView)

        val done = members.count { it.pastedAt > 0L }
        val meta = TextView(ctx)
        meta.text = if (done > 0) "اتلصق $done/${members.size}" else "${members.size} عناصر"
        meta.textSize = 11f
        meta.setTextColor(if (done == members.size) ShelfStyle.accent else ShelfStyle.subTextColor)
        meta.setPadding(px(6), 0, px(6), 0)
        head.addView(meta)

        val allPinned = members.all { it.isPinned }
        val star = TextView(ctx)
        star.text = if (allPinned) "★" else "☆"
        star.textSize = 18f
        star.setTextColor(if (allPinned) Color.parseColor("#FFD54F") else ShelfStyle.subTextColor)
        star.setPadding(px(8), 0, 0, 0)
        star.setOnClickListener { HistoryRepository.setPinned(members, !allPinned) }
        head.addView(star)

        val del = TextView(ctx)
        del.text = "✕"
        del.textSize = 14f
        del.setTextColor(ShelfStyle.subTextColor)
        del.setPadding(px(12), 0, px(2), 0)
        del.setOnClickListener {
            HistoryRepository.removeGroup(id)
            Toast.makeText(ctx, "اتمسحت المجموعة (المثبّت بيفضل)", Toast.LENGTH_SHORT).show()
        }
        head.addView(del)

        head.setOnClickListener {
            ShelfBehavior.setCollapsed(id, !collapsed)
            render()
        }
        box.addView(head)

        if (!collapsed) {
            val act = newActionsRow()
            val ap = act.layoutParams as LinearLayout.LayoutParams
            ap.topMargin = 0
            ap.bottomMargin = px(8)
            act.layoutParams = ap
            act.addView(pill("لصق التالي", color, true) { pasteNext(members) })
            act.addView(pill("تسمية", color, false) {
                openNameDialog(GroupNameActivity.MODE_RENAME, id, explicit)
            })
            act.addView(pill("فك", color, false) { HistoryRepository.ungroup(id) })
            box.addView(act)

            for ((i, mem) in members.withIndex()) {
                box.addView(buildItem(mem, i, true))
            }
        }
        return box
    }
}
