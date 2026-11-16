// Built with BFB.AI
package com.bfbai.superhub.service

import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ShelfController(
    private val ctx: Context,
    private val onInsert: (String) -> Unit
) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val dp = ctx.resources.displayMetrics.density
    private val items = ArrayList<String>()
    private var expanded = false
    private var added = false
    private var isOnRight = true // يبدأ على اليمين

    // الحاوية الرئيسية للرف
    private val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xE60B1B3A.toInt())
        setPadding(8, 8, 8, 8)
        setOnClickListener { if (!expanded) setExpanded(true) else setExpanded(false) }
        
        // استقبال السحب والإفلات
        setOnDragListener { _, e ->
            when (e.action) {
                DragEvent.ACTION_DRAG_STARTED -> { setExpanded(true); true }
                DragEvent.ACTION_DROP -> {
                    e.clipData?.let { cd ->
                        for (i in 0 until cd.itemCount) {
                            cd.getItemAt(i).coerceToText(ctx)?.toString()?.let { add(it) }
                        }
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> { true }
                else -> true
            }
        }
    }

    // قائمة النصوص
    private val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    init {
        root.addView(ScrollView(ctx).apply { addView(list) })
    }

    // إعدادات النافذة (Accessibility Overlay)
    private val lp = WindowManager.LayoutParams(
        (24 * dp).toInt(), (160 * dp).toInt(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL } // يبدأ على اليمين

    fun show() { 
        if (!added) { 
            wm.addView(root, lp); added = true 
        } 
    }

    fun hide() { 
        if (added) { 
            wm.removeView(root); added = false 
        } 
    }

    fun add(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        items.remove(t); items.add(0, t)
        while (items.size > 30) items.removeAt(items.lastIndex)
        render()
    }

    // فتح وقفل الرف
    private fun setExpanded(on: Boolean) {
        expanded = on
        lp.width = if (on) (250 * dp).toInt() else (24 * dp).toInt()
        lp.height = if (on) (400 * dp).toInt() else (160 * dp).toInt()
        list.visibility = if (on) View.VISIBLE else View.GONE
        
        // تغيير شكل الشريط لو مقفول
        if (!on) {
            root.removeAllViews()
            val indicator = View(ctx).apply {
                val shape = GradientDrawable()
                shape.shape = GradientDrawable.RECTANGLE
                shape.cornerRadius = 12 * dp
                shape.setColor(0xFF4DE1E1.toInt())
                background = shape
            }
            root.addView(indicator, LinearLayout.LayoutParams((6 * dp).toInt(), (60 * dp).toInt()).apply { gravity = Gravity.CENTER_HORIZONTAL })
        } else {
            root.removeAllViews()
            root.addView(ScrollView(ctx).apply { addView(list) })
            render()
        }
        
        if (added) wm.updateViewLayout(root, lp)
    }

    // عرض النصوص في القائمة
    private fun render() {
        list.removeAllViews()
        items.forEach { text ->
            list.addView(TextView(ctx).apply {
                this.text = text
                maxLines = 2
                setTextColor(Color.WHITE)
                setBackgroundColor(0xFF13264D.toInt())
                setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
                
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                params.setMargins(0, 0, 0, (8 * dp).toInt())
                layoutParams = params
                
                setOnClickListener { onInsert(text) }
                // لسحب النص للخارج (للتطبيقات اللي بتدعم)
                setOnLongClickListener { v ->
                    v.startDragAndDrop(
                        ClipData.newPlainText("shelf", text),
                        View.DragShadowBuilder(v), null, View.DRAG_FLAG_GLOBAL
                    )
                }
            })
        }
    }
}
