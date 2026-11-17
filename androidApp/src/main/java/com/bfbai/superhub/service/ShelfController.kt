// Built with BFB.AI
package com.bfbai.superhub.service

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import com.bfbai.superhub.AppConfig
import kotlin.math.abs

data class ShelfItem(val text: String, val source: String)

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

    // القفل التلقائي
    private val autoCloseHandler = Handler(Looper.getMainLooper())
    private val autoCloseRunnable = Runnable { setExpanded(false) }

    private val list: LinearLayout = LinearLayout(ctx)
    private val scroll: ScrollView = ScrollView(ctx)
    private val root: FrameLayout = FrameLayout(ctx)
    private lateinit var topBar: LinearLayout
    private lateinit var lp: WindowManager.LayoutParams

    init {
        lp = WindowManager.LayoutParams(
            (AppConfig.shelfClosedWidth * dp).toInt(),
            (AppConfig.shelfClosedHeight * dp).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 400
        }

        // خلفية الستارة
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(AppConfig.shelfBackgroundColor))
            cornerRadii = floatArrayOf((16 * dp), (16 * dp), 0f, 0f, 0f, 0f, (16 * dp), (16 * dp))
        }
        root.background = bg
        
        list.orientation = LinearLayout.VERTICAL
        val pad = (12 * dp).toInt()
        list.setPadding(pad, pad, pad, pad)
        
        scroll.addView(list)
        root.addView(scroll)
        scroll.visibility = View.GONE
        
        render()

        // شريط التحكم (الأزرار)
        setupTopBar()

        // السحب والتحكم
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
                            // الالتصاق بالحافة
                            val screenWidth = ctx.resources.displayMetrics.widthPixels
                            val midPoint = screenWidth / 2
                            
                            if (lp.x + (lp.width / 2) > midPoint) {
                                lp.gravity = Gravity.TOP or Gravity.END
                            } else {
                                lp.gravity = Gravity.TOP or Gravity.START
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

    private fun setupTopBar() {
        topBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, (10 * dp).toInt())
        }
        
        // زرار الإعدادات
        val settingsBtn = TextView(ctx).apply {
            text = AppConfig.textOptions
            setTextColor(Color.parseColor(AppConfig.accentColor))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, (15 * dp).toInt(), 0)
            setOnClickListener { showOptionsMenu(it) }
        }
        
        // زرار الإغلاق
        val closeBtn = TextView(ctx).apply {
            text = AppConfig.textClose
            setTextColor(Color.parseColor("#FF5A5F"))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { setExpanded(false) }
        }

        topBar.addView(settingsBtn)
        topBar.addView(closeBtn)
    }

    private fun showOptionsMenu(anchor: View) {
        val popup = PopupMenu(ctx, anchor)
        popup.menu.add(0, 1, 0, AppConfig.textSettings)
        popup.menu.add(0, 2, 1, AppConfig.textClearAll)
        popup.menu.add(0, 3, 2, AppConfig.textSelect)
        popup.menu.add(0, 4, 3, AppConfig.textCopy)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { /* فتح الإعدادات */ }
                2 -> { items.clear(); render(); setExpanded(false) }
                3 -> { /* اختيار */ }
                4 -> { if (items.isNotEmpty()) onInsert(items[0].text) }
            }
            true
        }
        popup.show()
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
                        cd.getItemAt(i).text?.toString()?.let { add(it, "سحب") }
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

    fun add(text: String, source: String = AppConfig.defaultSource) {
        val t: String = text.trim()
        if (t.isEmpty()) return
        
        items.removeAll { it.text == t }
        items.add(0, ShelfItem(t, source))
        while (items.size > 30) items.removeAt(items.size - 1)
        
        render()
        setExpanded(true) // فتح تلقائي عند النسخ
    }

    fun setExpanded(on: Boolean) {
        expanded = on
        if (on) {
            lp.width = (AppConfig.shelfOpenWidth * dp).toInt()
            lp.height = (AppConfig.shelfOpenHeight * dp).toInt()
            scroll.visibility = View.VISIBLE
            
            autoCloseHandler.removeCallbacks(autoCloseRunnable)
            autoCloseHandler.postDelayed(autoCloseRunnable, 5000) // قفل بعد 5 ثواني
        } else {
            lp.width = (AppConfig.shelfClosedWidth * dp).toInt()
            lp.height = (AppConfig.shelfClosedHeight * dp).toInt()
            scroll.visibility = View.GONE
            autoCloseHandler.removeCallbacks(autoCloseRunnable)
        }
        if (added) wm.updateViewLayout(root, lp)
    }

    private fun render() {
        list.removeAllViews()
        list.addView(topBar)

        if (items.isEmpty()) {
            list.addView(TextView(ctx).apply {
                text = AppConfig.textEmpty
                setTextColor(Color.parseColor("#99FFFFFF"))
                textSize = 13f
            })
        } else {
            for (item in items) {
                list.addView(buildItem(item))
            }
        }
    }

    private fun buildItem(item: ShelfItem): LinearLayout {
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        val pad = (14 * dp).toInt()
        card.setPadding(pad, pad, pad, pad)
        
        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(AppConfig.cardBackgroundColor))
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
        tvText.setTextColor(Color.parseColor(AppConfig.cardTextColor))
        tvText.textSize = 14f
        card.addView(tvText)

        val tvSource = TextView(ctx)
        tvSource.text = "من: ${item.source}"
        tvSource.setTextColor(Color.parseColor(AppConfig.accentColor))
        tvSource.textSize = 11f
        tvSource.setPadding(0, (4 * dp).toInt(), 0, 0)
        card.addView(tvSource)
        
        card.setOnClickListener { 
            onInsert(item.text)
            setExpanded(false)
        }
        
        return card
    }
}
