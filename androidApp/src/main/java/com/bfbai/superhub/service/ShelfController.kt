// Built with BFB.AI
package com.bfbai.superhub.service

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

data class ShelfItem(val text: String, val source: String, val color: Int)

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

    private val list: LinearLayout = LinearLayout(ctx)
    private val scroll: ScrollView = ScrollView(ctx)
    private val root: FrameLayout = FrameLayout(ctx)

    private val lp: WindowManager.LayoutParams = WindowManager.LayoutParams(
        (24 * dp).toInt(),
        (160 * dp).toInt(),
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

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#E60B1B3A"))
            cornerRadii = floatArrayOf((16 * dp), (16 * dp), 0f, 0f, 0f, 0f, (16 * dp), (16 * dp))
        }
        root.background = bg
        
        list.orientation = LinearLayout.VERTICAL
        val pad = (12 * dp).toInt()
        list.setPadding(pad, pad, pad, pad)
        
        // إعداد الـ ScrollView
        scroll.addView(list)
        root.addView(scroll)
        scroll.visibility = View.GONE

        root.setOnTouchListener(object : View.OnTouchListener {
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialY = lp.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        if (abs(deltaX) > 10 || abs(deltaY) > 10) isDragging = true
                        
                        lp.y = initialY + deltaY
                        if (added) wm.updateViewLayout(root, lp)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) setExpanded(!expanded)
                        return true
                    }
                    MotionEvent.ACTION_OUTSIDE -> {
                        if (expanded) setExpanded(false)
                        return true
                    }
                }
                return false
            }
        })

        root.setOnDragListener { _, e -> handleDrag(e) }
    }

    private fun handleDrag(e: DragEvent): Boolean {
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> setExpanded(true)
            DragEvent.ACTION_DROP -> {
                e.clipData?.let { cd ->
                    for (i in 0 until cd.itemCount) {
                        cd.getItemAt(i).coerceToText(ctx)?.toString()?.let { add(it, "سحب") }
                    }
                }
            }
            DragEvent.ACTION_DRAG_ENDED -> setExpanded(false)
        }
        return true
    }

    fun show() {
        if (!added) { wm.addView(root, lp); added = true }
    }

    fun hide() {
        if (added) { wm.removeView(root); added = false }
    }

    // الدالة دي بقت بتحفظ في السجل كمان
    fun add(text: String, source: String = "تطبيق") {
        val t: String = text.trim()
        if (t.isEmpty()) return
        
        items.removeAll { it.text == t }
        
        val cardColor = when(source.lowercase()) {
            "whatsapp", "واتساب" -> Color.parseColor("#991E3B2C")
            "chrome", "كروم" -> Color.parseColor("#992C1E3B")
            else -> Color.parseColor("#9913264D")
        }
        
        items.add(0, ShelfItem(t, source, cardColor))
        // مسحنا شرط الـ 3، وخليتها 30
        while (items.size > 30) items.removeAt(items.size - 1)
        render()
    }

    fun setExpanded(on: Boolean) {
        expanded = on
        lp.width = if (on) (260 * dp).toInt() else (24 * dp).toInt()
        lp.height = if (on) (350 * dp).toInt() else (160 * dp).toInt()
        scroll.visibility = if (on) View.VISIBLE else View.GONE
        if (added) wm.updateViewLayout(root, lp)
    }

    private fun render() {
        list.removeAllViews()
        
        // شيلنا زرار الإغلاق (X) من هنا خلاص

        for (item in items) {
            list.addView(buildItem(item))
        }
    }

    private fun buildItem(item: ShelfItem): LinearLayout {
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        val pad = (14 * dp).toInt()
        card.setPadding(pad, pad, pad, pad)
        
        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(item.color)
            cornerRadius = (12 * dp)
            setStroke((1 * dp).toInt(), Color.parseColor("#554DE1E1"))
        }
        card.background = cardBg
        
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, (10 * dp).toInt())
        card.layoutParams = params

        val tvText = TextView(ctx)
        tvText.text = item.text
        tvText.maxLines = 3
        tvText.setTextColor(Color.WHITE)
        tvText.textSize = 14f
        tvText.setTypeface(null, Typeface.BOLD)
        card.addView(tvText)

        val tvSource = TextView(ctx)
        tvSource.text = "من: ${item.source}"
        tvSource.setTextColor(Color.parseColor("#4DE1E1"))
        tvSource.textSize = 11f
        tvSource.setPadding(0, (4 * dp).toInt(), 0, 0)
        card.addView(tvSource)
        
        card.setOnClickListener { 
            onInsert(item.text)
            setExpanded(false)
        }
        
        card.setOnLongClickListener { v: View ->
            val clip: ClipData = ClipData.newPlainText("shelf", item.text)
            v.startDragAndDrop(
                clip,
                View.DragShadowBuilder(v),
                null,
                View.DRAG_FLAG_GLOBAL
            )
        }
        return card
    }
}
