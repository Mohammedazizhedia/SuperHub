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

class ShelfItem(
    val text: String,
    val sourceApp: String,
    val color: Int
)

class ShelfController(
    private val ctx: Context,
    private val onInsert: (String) -> Unit
) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val dp = ctx.resources.displayMetrics.density
    private val items = ArrayList<ShelfItem>()
    private var expanded = false
    private var added = false
    private lateinit var lp: WindowManager.LayoutParams

    private val list = LinearLayout(ctx).apply { 
        orientation = LinearLayout.VERTICAL
        setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private val root = FrameLayout(ctx).apply {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#E60B1B3A"))
            cornerRadii = floatArrayOf((16 * dp), (16 * dp), 0f, 0f, 0f, 0f, (16 * dp), (16 * dp))
        }
        background = bg
        
        addView(ScrollView(ctx).apply { addView(list) })
        
        // إعدادات السحب والإفلات
        setOnDragListener { _, e ->
            when (e.action) {
                DragEvent.ACTION_DRAG_STARTED -> { setExpanded(true); true }
                DragEvent.ACTION_DROP -> {
                    e.clipData?.let { cd ->
                        for (i in 0 until cd.itemCount)
                            cd.getItemAt(i).coerceToText(ctx)?.toString()?.let { add(it, "سحب") }
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> { true }
                else -> true
            }
        }

        // إعدادات تحريك الرف
        setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = lp.x
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
                        
                        lp.x = initialX + deltaX
                        lp.y = initialY + deltaY
                        if (added) wm.updateViewLayout(root, lp)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) setExpanded(!expanded)
                        return true
                    }
                }
                return false
            }
        })
    }

    init {
        lp = WindowManager.LayoutParams(
            (20 * dp).toInt(), (140 * dp).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { 
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 400 
        }
    }

    fun show() { if (!added) { wm.addView(root, lp); added = true } }
    fun hide() { if (added) { wm.removeView(root); added = false } }

    fun add(text: String, source: String = "تطبيق") {
        val t = text.trim()
        if (t.isEmpty()) return
        if (items.none { it.text == t }) {
            val color = when(source.lowercase()) {
                "whatsapp", "واتساب" -> Color.parseColor("#1E3B2C")
                "chrome", "كروم" -> Color.parseColor("#2C1E3B")
                else -> Color.parseColor("#13264D")
            }
            items.add(0, ShelfItem(t, source, color))
            while (items.size > 30) items.removeAt(items.lastIndex)
            render()
        }
    }

    fun setExpanded(on: Boolean) {
        expanded = on
        lp.width = if (on) (260 * dp).toInt() else (20 * dp).toInt()
        lp.height = if (on) (350 * dp).toInt() else (140 * dp).toInt()
        list.visibility = if (on) View.VISIBLE else View.INVISIBLE
        if (added) wm.updateViewLayout(root, lp)
    }

    private fun render() {
        list.removeAllViews()
        items.forEach { item ->
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
                
                val cardBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(item.color)
                    cornerRadius = (12 * dp)
                    setStroke((1 * dp).toInt(), Color.parseColor("#334DE1E1"))
                }
                background = cardBg
                
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, (10 * dp).toInt())
                layoutParams = params

                addView(TextView(ctx).apply {
                    this.text = item.text
                    maxLines = 3
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                })

                addView(TextView(ctx).apply {
                    this.text = "من: ${item.sourceApp}"
                    setTextColor(Color.parseColor("#4DE1E1"))
                    textSize = 11f
                    setPadding(0, (4 * dp).toInt(), 0, 0)
                })
                
                setOnClickListener { 
                    onInsert(item.text)
                    setExpanded(false)
                }
                
                setOnLongClickListener { v ->
                    v.startDragAndDrop(
                        ClipData.newPlainText("shelf", item.text),
                        View.DragShadowBuilder(v), null, View.DRAG_FLAG_GLOBAL
                    )
                    true
                }
            }
            list.addView(card)
        }
    }
}
