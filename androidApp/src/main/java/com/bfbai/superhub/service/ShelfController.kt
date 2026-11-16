// Built with BFB.AI
package com.bfbai.superhub.service

import android.content.ClipData
import android.content.Context
import android.graphics.PixelFormat
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
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

    private val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val root = FrameLayout(ctx).apply {
        setBackgroundColor(0xE6202124.toInt())
        addView(ScrollView(ctx).apply { addView(list) })
        setOnClickListener { if (!expanded) setExpanded(true) else setExpanded(false) }
        setOnDragListener { _, e ->
            when (e.action) {
                DragEvent.ACTION_DRAG_STARTED -> { setExpanded(true); true }
                DragEvent.ACTION_DROP -> {
                    e.clipData?.let { cd ->
                        for (i in 0 until cd.itemCount)
                            cd.getItemAt(i).coerceToText(ctx)?.toString()?.let { add(it) }
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> { true }
                else -> true
            }
        }
    }

    private val lp = WindowManager.LayoutParams(
        (24 * dp).toInt(), (160 * dp).toInt(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }

    fun show() { if (!added) { wm.addView(root, lp); added = true } }
    fun hide() { if (added) { wm.removeView(root); added = false } }

    fun add(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        items.remove(t)
        items.add(0, t)
        while (items.size > 30) items.removeAt(items.lastIndex)
        render()
    }

    fun setExpanded(on: Boolean) {
        expanded = on
        lp.width = if (on) (170 * dp).toInt() else (24 * dp).toInt()
        lp.height = if (on) (320 * dp).toInt() else (160 * dp).toInt()
        list.visibility = if (on) View.VISIBLE else View.INVISIBLE
        if (added) wm.updateViewLayout(root, lp)
    }

    private fun render() {
        list.removeAllViews()
        items.forEach { text ->
            list.addView(TextView(ctx).apply {
                this.text = text
                maxLines = 2
                setTextColor(0xFFFFFFFF.toInt())
                setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
                setOnClickListener { 
                    onInsert(text)
                    setExpanded(false)
                }
                setOnLongClickListener { v ->
                    v.startDragAndDrop(
                        ClipData.newPlainText("shelf", text),
                        View.DragShadowBuilder(v), null, View.DRAG_FLAG_GLOBAL
                    )
                    true
                }
            })
        }
    }
}
