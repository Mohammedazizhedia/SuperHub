// Built with BFB.AI
package com.bfbai.superhub.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ClipData
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
import android.view.DragEvent
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.bfbai.superhub.ui.HistoryActivity
import com.bfbai.superhub.utils.Detection
import com.bfbai.superhub.utils.HistoryRepository
import com.bfbai.superhub.utils.ItemKind
import com.bfbai.superhub.utils.SmartDetector
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
    var isPinned: Boolean = false
)

internal fun withAlpha(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0x00FFFFFF)

/** كل شكل الرف في مكان واحد، وهنوصله بالإعدادات في خطوة قادمة */
object ShelfStyle {
    var panelTop: Int = Color.parseColor("#D91A3563")
    var panelBottom: Int = Color.parseColor("#E60A1730")
    var panelStroke: Int = Color.parseColor("#40FFFFFF")
    var handleColor: Int = Color.parseColor("#E6102448")
    var accent: Int = Color.parseColor("#4DE1E1")
    var textColor: Int = Color.parseColor("#F2F7FF")
    var subTextColor: Int = Color.parseColor("#9FB3D1")

    var cornerDp: Float = 18f
    var cardCornerDp: Float = 14f
    var cardTintStartAlpha: Int = 0x59
    var cardTintEndAlpha: Int = 0x14
    var cardStrokeAlpha: Int = 0x66

    var blurEnabled: Boolean = true
    var blurRadiusDp: Float = 28f

    var closedWidthDp: Float = 24f
    var closedHeightDp: Float = 150f
    var expandedWidthDp: Float = 280f
    var expandedHeightDp: Float = 560f

    var autoCloseMs: Long = 10_000L
    var otpTtlMs: Long = 60_000L

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
}

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

    var onShownChanged: ((Boolean) -> Unit)? = null
    var appNameProvider: (() -> String)? = null
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
                if (!item.isPinned && detectOf(item.text).kind == ItemKind.OTP) {
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
                items.addAll(all.sortedByDescending { it.isPinned }.take(100))
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
                            if (!expanded) setExpanded(true)
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

    private fun resetAutoClose() {
        autoCloseHandler.removeCallbacks(autoCloseRunnable)
        if (expanded) autoCloseHandler.postDelayed(autoCloseRunnable, ShelfStyle.autoCloseMs)
    }

    private fun detectOf(text: String): Detection {
        val cached = detectCache[text]
        if (cached != null) return cached
        if (detectCache.size > 500) detectCache.clear()
        val d = SmartDetector.detect(text)
        detectCache[text] = d
        return d
    }

    private fun handleDrag(e: DragEvent): Boolean {
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                val hasText = e.clipDescription?.hasMimeType("text/*") == true
                if (hasText) {
                    dropped = false
                    dragFromHidden = userHidden
                    setExpanded(true)
                }
                return hasText
            }

            DragEvent.ACTION_DROP -> {
                dropped = true
                e.clipData?.let { cd ->
                    for (i in 0 until cd.itemCount) {
                        cd.getItemAt(i).coerceToText(ctx)?.toString()
                            ?.let { add(it, appNameProvider?.invoke() ?: "سحب", popUp = !dragFromHidden) }
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

    // زجاجي: تدرج شفاف + حد خفيف. والمطوي أغمق شوية مع حد سماوي
    private fun updateBackground() {
        val r = ShelfStyle.cornerDp * dp
        val top = if (expanded) ShelfStyle.panelTop else ShelfStyle.handleColor
        val bottom = if (expanded) ShelfStyle.panelBottom else ShelfStyle.handleColor
        val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
        bg.shape = GradientDrawable.RECTANGLE
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
        onShownChanged?.invoke(true)
    }

    // الإخفاء بيخلي النافذة موجودة (عشان السحب يفضل شغال) لكن الرف مش ظاهر
    fun hide() {
        if (!added) return
        userHidden = true
        setExpanded(false)
        root.visibility = View.GONE
        onShownChanged?.invoke(false)
    }

    fun destroy() {
        onShownChanged = null
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
            lp.width = (ShelfStyle.closedWidthDp * dp).toInt()
            lp.height = (ShelfStyle.closedHeightDp * dp).toInt()
            hideCustomMenu()
            autoCloseHandler.removeCallbacks(autoCloseRunnable)
            if (userHidden) root.visibility = View.GONE
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

    private fun buildItem(item: ShelfItem, index: Int): LinearLayout {
        val det = detectOf(item.text)
        val color = ShelfStyle.kindColors[det.kind]
            ?: ShelfStyle.textPalette[index % ShelfStyle.textPalette.size]
        val pad = (12 * dp).toInt()

        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(pad, pad, pad, pad)

        // كارت زجاجي ملون: تدرج شفاف من لون النوع + حد خفيف بنفس اللون
        val bg = GradientDrawable(
            if (isRightSide) GradientDrawable.Orientation.RIGHT_LEFT else GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                withAlpha(color, ShelfStyle.cardTintStartAlpha),
                withAlpha(color, ShelfStyle.cardTintEndAlpha)
            )
        )
        bg.cornerRadius = ShelfStyle.cardCornerDp * dp
        bg.setStroke((1 * dp).toInt(), withAlpha(color, ShelfStyle.cardStrokeAlpha))
        card.background = bg

        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cardParams.setMargins(0, 0, 0, (10 * dp).toInt())
        card.layoutParams = cardParams

        // الصف الأول: شارة النوع + (عد تنازلي للـ OTP) + النجمة
        val top = LinearLayout(ctx)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL

        val chip = TextView(ctx)
        chip.text = kindLabel(det.kind)
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

        if (det.kind == ItemKind.OTP && !item.isPinned) {
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
        card.addView(top)

        // المحتوى
        val content = TextView(ctx)
        if (det.kind == ItemKind.OTP) {
            content.text = det.value
            content.setTextColor(color)
            content.textSize = 26f
            content.typeface = Typeface.MONOSPACE
            content.letterSpacing = 0.15f
            content.textDirection = View.TEXT_DIRECTION_LTR
        } else {
            content.text = item.text
            content.setTextColor(ShelfStyle.textColor)
            content.textSize = 14f
            content.maxLines = 3
            content.ellipsize = android.text.TextUtils.TruncateAt.END
        }
        content.setPadding(0, (6 * dp).toInt(), 0, (2 * dp).toInt())
        card.addView(content)

        val meta = TextView(ctx)
        meta.text = "${item.source} • ${timeFormat.format(Date(item.timestamp))}"
        meta.setTextColor(ShelfStyle.subTextColor)
        meta.textSize = 11f
        card.addView(meta)

        // أزرار الإجراء حسب النوع
        val actions = LinearLayout(ctx)
        actions.orientation = LinearLayout.HORIZONTAL
        val actionsParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        actionsParams.topMargin = (10 * dp).toInt()
        actions.layoutParams = actionsParams

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

        // السحب للجانب بيحذف (إلا المثبّت)
        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                if (abs(diffX) > 100 && abs(velocityX) > 100) {
                    if (!item.isPinned) HistoryRepository.remove(item)
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

        // الضغط على الكارت بيدخل النص في الخانة المفتوحة (للكود: الرقم نفسه بس)
        val payload = if (det.kind == ItemKind.OTP) det.value else item.text
        card.setOnClickListener {
            onInsert(payload)
            setExpanded(false)
        }
        card.setOnLongClickListener { v: View ->
            val clip = ClipData.newPlainText("hubsync", payload)
            v.startDragAndDrop(clip, View.DragShadowBuilder(v), null, View.DRAG_FLAG_GLOBAL)
        }
        return card
    }
}
