// Built with BFB.AI
package com.bfbai.superhub.service

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.bfbai.superhub.AppConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class ShelfItem(
    val text: String, 
    val source: String, 
    val timestamp: Long = System.currentTimeMillis()
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

    private val autoCloseHandler = Handler(Looper.getMainLooper())
    private val autoCloseRunnable = Runnable { setExpanded(false) }

    private val list: LinearLayout = LinearLayout(ctx)
    private val scroll: ScrollView = ScrollView(ctx)
    private val root: FrameLayout = FrameLayout(ctx)

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

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(AppConfig.SHELF_BG_COLOR)
            cornerRadii = floatArrayOf(
                (AppConfig.SHELF_CORNER_RADIUS_DP * dp), (AppConfig.SHELF_CORNER_RADIUS_DP * dp), 
                0f, 0f, 0f, 0f, 
                (AppConfig.SHELF_CORNER_RADIUS_DP * dp), (AppConfig.SHELF_CORNER_RADIUS_DP * dp)
            )
            setStroke((1 * dp).toInt(), AppConfig.SHELF_STROKE_COLOR)
        }
        root.background = bg
        
        list.layoutDirection = View.LAYOUT_DIRECTION_RTL
        list.orientation = LinearLayout.VERTICAL
        val pad = (10 * dp).toInt()
        list.setPadding(pad, pad, pad, pad)
        
        scroll.addView(list)
        root.addView(scroll)
        scroll.visibility = View.GONE
        
        render()

        root.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = lp.x
                        initialY = lp.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        autoCloseHandler.removeCallbacks(autoCloseRunnable)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        if (abs(deltaX) > 10 || abs(deltaY) > 10) isDragging = true
                        
                        lp.x = initialX + deltaX
                        lp.y = (initialY + deltaY).coerceIn(0, ctx.resources.displayMetrics.heightPixels - lp.height)
                        if (added) wm.updateViewLayout(root, lp)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            val screenWidth = ctx.resources.displayMetrics.widthPixels
                            val midPoint = screenWidth / 2
                            
                            if (lp.x + (lp.width / 2) > midPoint) {
                                lp.gravity = Gravity.TOP or Gravity.END
                                list.layoutDirection = View.LAYOUT_DIRECTION_RTL 
                            } else {
                                lp.gravity = Gravity.TOP or Gravity.START
                                list.layoutDirection = View.LAYOUT_DIRECTION_LTR 
                            }
                            lp.x = 0 
                            if (added) wm.updateViewLayout(root, lp)
                        } else {
                            if (!expanded) setExpanded(true)
                        }
                        return true
                    }
                    MotionEvent.ACTION_OUTSIDE -> {
                        setExpanded(false)
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
            DragEvent.ACTION_DRAG_STARTED -> {
                val hasText = e.clipDescription?.hasMimeType("text/*") == true
                if (hasText) setExpanded(true)
                return hasText
            }
            DragEvent.ACTION_DROP -> {
                e.clipData?.let { cd ->
                    for (i in 0 until cd.itemCount) {
                        cd.getItemAt(i).text?.toString()?.let { add(it, "تطبيق آخر") }
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

    fun toggleVisibility() {
        if (expanded) {
            setExpanded(false)
        } else {
            setExpanded(true)
        }
    }

    fun add(text: String, source: String = "تطبيق") {
        val t = text.trim()
        if (t.isEmpty()) return
        
        items.removeAll { it.text == t }
        items.add(0, ShelfItem(t, source))
        
        render()
        setExpanded(true) 
    }

    fun setExpanded(on: Boolean) {
        expanded = on
        if (on) {
            lp.width = (AppConfig.SHELF_EXPANDED_WIDTH_DP * dp).toInt()
            lp.height = (AppConfig.SHELF_EXPANDED_HEIGHT_DP * dp).toInt()
            scroll.visibility = View.VISIBLE
            
            autoCloseHandler.removeCallbacks(autoCloseRunnable)
            autoCloseHandler.postDelayed(autoCloseRunnable, AppConfig.AUTO_CLOSE_DELAY_MS)
        } else {
            lp.width = (AppConfig.SHELF_CLOSED_WIDTH_DP * dp).toInt()
            lp.height = (AppConfig.SHELF_CLOSED_HEIGHT_DP * dp).toInt()
            scroll.visibility = View.GONE
            autoCloseHandler.removeCallbacks(autoCloseRunnable)
        }
        if (added) wm.updateViewLayout(root, lp)
    }

    private fun render() {
        list.removeAllViews()
        
        val topBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, (10 * dp).toInt())
            layoutDirection = list.layoutDirection 
        }
        
        val title = TextView(ctx).apply {
            text = "SuperHub"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        val menuBtn = TextView(ctx).apply {
            text = "⋮⋮"
            setTextColor(AppConfig.SHELF_STROKE_COLOR)
            textSize = 20f
            setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)
            setOnClickListener { showProfessionalMenu(it) }
        }
        
        topBar.addView(title)
        topBar.addView(menuBtn)
        list.addView(topBar)

        if (items.isEmpty()) {
            list.addView(TextView(ctx).apply {
                text = "الرف فاضي..."
                setTextColor(AppConfig.CARD_SUBTEXT_COLOR)
                textSize = 13f
            })
        } else {
            for (item in items) {
                list.addView(buildItem(item))
            }
        }
    }

    // دالة القائمة الاحترافية الجديدة
    private fun showProfessionalMenu(anchorView: View) {
        val options = listOf("Select", "Clipboard", "Settings", "Close")
        
        val popup = ListPopupWindow(ctx)
        popup.anchorView = anchorView
        popup.width = (150 * dp).toInt() 
        popup.isModal = true
        popup.setBackgroundDrawable(ColorDrawable(AppConfig.MENU_BG_COLOR))

        val adapter = object : ArrayAdapter<String>(ctx, android.R.layout.simple_list_item_1, options) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(AppConfig.MENU_TEXT_COLOR)
                view.textSize = 16f
                view.setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
                // فصل العناصر بخط شيك
                view.setBackgroundColor(Color.TRANSPARENT)
                return view
            }
        }
        
        popup.adapter = adapter
        popup.divider = ColorDrawable(AppConfig.MENU_DIVIDER_COLOR)
        popup.dividerHeight = (1 * dp).toInt()
        
        popup.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> { /* Select */ }
                1 -> { /* Clipboard */ }
                2 -> { /* Settings */ }
                3 -> { setExpanded(false) }
            }
            popup.dismiss()
        }
        
        // تظبيط اتجاه القائمة حسب مكان الرف
        popup.show()
    }

    private fun buildItem(item: ShelfItem): LinearLayout {
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
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

        val tvText = TextView(ctx)
        tvText.text = item.text
        tvText.maxLines = 3
        tvText.setTextColor(AppConfig.CARD_TEXT_COLOR)
        tvText.textSize = 14f
        tvText.layoutDirection = View.LAYOUT_DIRECTION_LOCALE 
        card.addView(tvText)

        val dateFormat = SimpleDateFormat("hh:mm a - dd MMM", Locale.getDefault())
        val timeStr = dateFormat.format(Date(item.timestamp))
        
        val tvSource = TextView(ctx)
        tvSource.text = "${item.source} • $timeStr"
        tvSource.setTextColor(AppConfig.CARD_SUBTEXT_COLOR)
        tvSource.textSize = 11f
        tvSource.setPadding(0, (4 * dp).toInt(), 0, 0)
        tvSource.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        card.addView(tvSource)
        
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
