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
import java.util.UUID
import kotlin.math.abs

// كلاس جديد عشان نحفظ النص ومصدره ونعطيه لون
data class ShelfItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val sourceApp: String = "جهازك",
    val color: Int = Color.parseColor("#13264D") // لون افتراضي
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
        
        setOnDragListener { _, e ->
            when (e.action) {
                DragEvent.ACTION_DRAG_STARTED -> { setExpanded(true); true }
                DragEvent.ACTION_DROP -> {
                    e.clipData?.let { cd ->
                        for (i in 0 until cd.itemCount)
                            cd.getItemAt(i).coerceToText(ctx)?.toString()?.let { 
                                add(it, "سحب وإفلات") 
                            }
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> { true }
                else -> true
            }
        }

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

    private val lp = WindowManager.LayoutParams(
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

    fun show() { if (!added) { wm.addView(root, lp); added = true } }
    fun hide() { if (added) { wm.removeView(root); added = false } }

    // دالة الإضافة الجديدة اللي بتاخد المصدر
    fun add(text: String, source: String = "تطبيق") {
        val t = text.trim()
        if (t.isEmpty()) return
        // عشان نمنع التكرار، بنشوف لو النص موجود بنفس المصدر
        if (items.none { it.text == t && it.sourceApp == source }) {
            // لون مختلف لكل مصدر
            val color = when(source.lowercase()) {
                "whatsapp", "واتساب" -> Color.parseColor("#1E3B2C") // أخضر غامق
                "chrome", "كروم" -> Color.parseColor("#2C1E3B") // بنفسجي غامق
                else -> Color.parseColor("#13264D") // كحلي
            }
            items.add(0, ShelfItem(text = t, sourceApp = source, color = color))
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
                    setColor(item.color) // اللون المخصص للمصدر
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

                // النص
                addView(TextView(ctx).apply {
                    this.text = item.text
                    maxLines = 3
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                })

                // المصدر
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
