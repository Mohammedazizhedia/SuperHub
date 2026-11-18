// Built with BFB.AI
package com.bfbai.superhub.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import com.bfbai.superhub.AppConfig
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

@SuppressLint("ClickableViewAccessibility")
class ShelfController(
    private val ctx: Context,
    private val onInsert: (String) -> Unit
) {
    private val wm: WindowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val dp: Float = ctx.resources.displayMetrics.density
    private val items: ArrayList<ShelfItem> = ArrayList()

    private var expanded: Boolean = false
    private var added: Boolean = false
    private var isRightSide: Boolean = true
    private var dropped: Boolean = false

    var onShownChanged: ((Boolean) -> Unit)? = null
    val isShown: Boolean get() = added

    private val autoCloseHandler = Handler(Looper.getMainLooper())
    private val autoCloseRunnable = Runnable { setExpanded(false) }

    private val list: LinearLayout = LinearLayout(ctx)
    private val scroll: ScrollView = ScrollView(ctx)
    private val root: FrameLayout = FrameLayout(ctx)
    private val topBar: LinearLayout = LinearLayout(ctx)

    private var customMenu: LinearLayout? = null

    private val lp: WindowManager.LayoutParams = WindowManager.LayoutParams(
        (AppConfig.SHELF_CLOSED_WIDTH_DP * dp).toInt(),
        (AppConfig.SHELF_CLOSED_HEIGHT_DP * dp).toInt(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT
    )

    init {
        lp.gravity = Gravity.TOP or Gravity.END
        lp.x = 0
        lp.y = 400

        updateBackground()

        list.layoutDirection = View.LAYOUT_DIRECTION_RTL
        list.orientation = LinearLayout.VERTICAL
        val pad = (10 * dp).toInt()
        list.setPadding(pad, pad, pad, pad)

        scroll.addView(list)
        scroll.visibility = View.GONE
        scroll.setOnTouchListener { _, _ -> resetAutoClose(); false }

        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.setPadding(pad, pad, pad, 0)
        topBar.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val title = TextView(ctx).apply {
            text = "SuperHub"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val pasteBtn = TextView(ctx).apply {
            text = "⎘"
            textSize = 24f
            setTextColor(AppConfig.ACTION_ICON_COLOR)
            setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)
            setOnClickListener {
                if (!expanded) setExpanded(true)
                readClipboardNow { text -> add(text, "لصق مباشر") }
            }
        }

        val menuBtn = TextView(ctx).apply {
            text = "⋮⋮"
            setTextColor(AppConfig.ACTION_ICON_COLOR)
            textSize = 20f
            setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)
            setOnClickListener { showCustomMenu() }
        }

        topBar.addView(title)
        topBar.addView(pasteBtn)
        topBar.addView(menuBtn)

        root.addView(topBar)
        root.addView(scroll)

        render()

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

                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    if (customMenu?.visibility == View.VISIBLE) hideCustomMenu()
                    else if (expanded) setExpanded(false)
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
                                list.layoutDirection = View.LAYOUT_DIRECTION_RTL
                                topBar.layoutDirection = View.LAYOUT_DIRECTION_RTL
                            } else {
                                lp.gravity = Gravity.TOP or Gravity.START
                                isRightSide = false
                                list.layoutDirection = View.LAYOUT_DIRECTION_LTR
                                topBar.layoutDirection = View.LAYOUT_DIRECTION_LTR
                            }
                            lp.x = 0
                            updateBackground()
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

    private fun maxX(): Int =
        maxOf(0, ctx.resources.displayMetrics.widthPixels - lp.width)

    private fun maxY(): Int =
        maxOf(0, ctx.resources.displayMetrics.heightPixels - lp.height)

    private fun resetAutoClose() {
        autoCloseHandler.removeCallbacks(autoCloseRunnable)
        if (expanded) autoCloseHandler.postDelayed(autoCloseRunnable, AppConfig.AUTO_CLOSE_DELAY_MS)
    }

    private fun readClipboardNow(onText: (String) -> Unit) {
        if (!added) return
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        try {
            wm.updateViewLayout(root, lp)
        } catch (e: Exception) {
            return
        }
        root.postDelayed({
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(ctx)?.toString()
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            if (added) {
                try {
                    wm.updateViewLayout(root, lp)
                } catch (e: Exception) {
                }
            }
            if (t.isNullOrBlank()) {
                Toast.makeText(ctx, "الحافظة فاضية أو النظام منع القراءة", Toast.LENGTH_SHORT).show()
            } else onText(t)
        }, 250)
    }

    fun pasteFromClipboard() {
        show()
        setExpanded(true)
        readClipboardNow { add(it, "لصق مباشر") }
    }

    private fun handleDrag(e: DragEvent): Boolean {
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                val hasText = e.clipDescription?.hasMimeType("text/*") == true
                if (hasText) {
                    dropped = false
                    setExpanded(true)
                }
                return hasText
            }
            DragEvent.ACTION_DROP -> {
                dropped = true
                e.clipData?.let { cd ->
                    for (i in 0 until cd.itemCount) {
                        cd.getItemAt(i).coerceToText(ctx)?.toString()?.let { add(it, "سحب") }
                    }
                }
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                if (!dropped) setExpanded(false) else resetAutoClose()
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
            lp.y = startY + ((1f - value) * 150).toInt()
            wm.updateViewLayout(root, lp)
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                hide()
                root.alpha = 1f
                lp.y = startY
            }
        })
        anim.start()
    }

    private fun updateBackground() {
        val radius = AppConfig.SHELF_CORNER_RADIUS_DP * dp
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(AppConfig.SHELF_BG_COLOR)
            if (isRightSide) {
                cornerRadii = floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
            } else {
                cornerRadii = floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
            }
            setStroke((1 * dp).toInt(), AppConfig.SHELF_STROKE_COLOR)
        }
        root.background = bg
    }

    fun show() {
        if (!added) {
            wm.addView(root, lp)
            added = true
            root.alpha = 1f
            onShownChanged?.invoke(true)
        }
    }

    fun hide() {
        if (added) {
            wm.removeView(root)
            added = false
            onShownChanged?.invoke(false)
        }
    }

    fun add(text: String, source: String = "تطبيق") {
        val t = text.trim()
        if (t.isEmpty()) return

        if (!added) show()

        items.removeAll { it.text == t }
        items.add(0, ShelfItem(t, source))
        while (items.size > AppConfig.MAX_HISTORY_ITEMS) items.removeAt(items.size - 1)

        render()
        setExpanded(true)
    }

    fun setExpanded(on: Boolean) {
        expanded = on
        if (on) {
            lp.width = (AppConfig.SHELF_EXPANDED_WIDTH_DP * dp).toInt()
            lp.height = (AppConfig.SHELF_EXPANDED_HEIGHT_DP * dp).toInt()
            if (lp.y > maxY()) lp.y = maxY()
            scroll.visibility = View.VISIBLE

            val params = scroll.layoutParams as FrameLayout.LayoutParams
            params.topMargin = (40 * dp).toInt()
            scroll.layoutParams = params

            resetAutoClose()
        } else {
            lp.width = (AppConfig.SHELF_CLOSED_WIDTH_DP * dp).toInt()
            lp.height = (AppConfig.SHELF_CLOSED_HEIGHT_DP * dp).toInt()
            scroll.visibility = View.GONE
            hideCustomMenu()
            autoCloseHandler.removeCallbacks(autoCloseRunnable)
        }
        if (added) wm.updateViewLayout(root, lp)
    }

    private fun render() {
        list.removeAllViews()
        if (items.isEmpty()) {
            list.addView(TextView(ctx).apply {
                text = "الرف فاضي، اسحب نص هنا"
                setTextColor(AppConfig.CARD_SUBTEXT_COLOR)
                textSize = 13f
            })
        } else {
            val sortedItems = items.sortedByDescending { it.isPinned }
            for (item in sortedItems) {
                list.addView(buildItem(item))
            }
        }
    }

    private fun showCustomMenu() {
        if (customMenu == null) {
            customMenu = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(AppConfig.MENU_BG_COLOR)
                val pad = (10 * dp).toInt()
                setPadding(pad, pad, pad, pad)
                y = (50 * dp)
                layoutParams = FrameLayout.LayoutParams(
                    (150 * dp).toInt(),
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val options = listOf("Select", "Clipboard", "Settings", "Close")
            for ((index, opt) in options.withIndex()) {
                val tv = TextView(ctx).apply {
                    text = opt
                    setTextColor(AppConfig.MENU_TEXT_COLOR)
                    textSize = 16f
                    setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    setOnClickListener {
                        if (opt == "Close") setExpanded(false)
                        hideCustomMenu()
                    }
                }
                customMenu?.addView(tv)

                if (index < options.size - 1) {
                    val divider = View(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            (1 * dp).toInt()
                        ).apply {
                            setMargins(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
                        }
                        setBackgroundColor(AppConfig.MENU_DIVIDER_COLOR)
                    }
                    customMenu?.addView(divider)
                }
            }
            root.addView(customMenu)
        }
        customMenu?.visibility = View.VISIBLE
    }

    private fun hideCustomMenu() {
        customMenu?.visibility = View.GONE
    }

    private fun buildItem(item: ShelfItem): LinearLayout {
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.HORIZONTAL
        val pad = (12 * dp).toInt()
        card.setPadding(pad, pad, pad, pad)
        card.layoutDirection = list.layoutDirection

        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(AppConfig.CARD_BG_COLOR)
            cornerRadius = (10 * dp)
            setStroke((1 * dp).toInt(), AppConfig.SHELF_STROKE_COLOR)
        }
        card.background = cardBg

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, (8 * dp).toInt())
        card.layoutParams = params

        val textLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvText = TextView(ctx)
        tvText.text = item.text
        tvText.maxLines = 3
        tvText.setTextColor(AppConfig.CARD_TEXT_COLOR)
        tvText.textSize = 14f
        tvText.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        textLayout.addView(tvText)

        val dateFormat = SimpleDateFormat("hh:mm a - dd MMM", Locale.getDefault())
        val timeStr = dateFormat.format(Date(item.timestamp))

        val tvSource = TextView(ctx)
        tvSource.text = "${item.source} • $timeStr"
        tvSource.setTextColor(AppConfig.CARD_SUBTEXT_COLOR)
        tvSource.textSize = 11f
        tvSource.setPadding(0, (4 * dp).toInt(), 0, 0)
        tvSource.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        textLayout.addView(tvSource)

        card.addView(textLayout)

        val pinBtn = TextView(ctx).apply {
            text = if (item.isPinned) "★" else "☆"
            textSize = 22f
            setPadding((8 * dp).toInt(), 0, 0, 0)
            setTextColor(if (item.isPinned) AppConfig.PIN_ACTIVE_COLOR else AppConfig.ACTION_ICON_COLOR)
            setOnClickListener {
                item.isPinned = !item.isPinned
                render()
            }
        }
        card.addView(pinBtn)

        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                if (abs(diffX) > 100 && abs(velocityX) > 100) {
                    if (!item.isPinned) {
                        items.remove(item)
                        render()
                    }
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

        card.setOnClickListener {
            onInsert(item.text)
            setExpanded(false)
        }

        card.setOnLongClickListener { v: View ->
            val clip = ClipData.newPlainText("shelf", item.text)
            v.startDragAndDrop(clip, View.DragShadowBuilder(v), null, View.DRAG_FLAG_GLOBAL)
        }
        return card
    }
}
