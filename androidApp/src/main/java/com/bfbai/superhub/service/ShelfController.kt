// Built with BFB.AI
package com.bfbai.superhub.service

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

data class ShelfItem(val content: String, val isImage: Boolean, val source: String, val color: Int)

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

    private var lastOutsideTapTime: Long = 0
    private val doubleTapTimeout: Long = 300

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
        lp.gravity = Gravity.TOP or Gravity.END // يبدأ على اليمين
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
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        if (abs(deltaX) > 10 || abs(deltaY) > 10) isDragging = true
                        
                        // سحب في كل الاتجاهات
                        lp.x = initialX + deltaX
                        lp.y = (initialY + deltaY).coerceIn(0, ctx.resources.displayMetrics.heightPixels - lp.height)
                        if (added) wm.updateViewLayout(root, lp)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            // خاصية الالتصاق (Snap to Edge)
                            val screenWidth = ctx.resources.displayMetrics.widthPixels
                            val midPoint = screenWidth / 2
                            
                            // لو الرف في نص الشاشة اليمين، يروح اليمين. غير كده يروح الشمال
                            if (lp.x + (lp.width / 2) > midPoint) {
                                lp.gravity = Gravity.TOP or Gravity.END
                                lp.x = 0
                            } else {
                                lp.gravity = Gravity.TOP or Gravity.START
                                lp.x = 0
                            }
                            if (added) wm.updateViewLayout(root, lp)
                        } else {
                            if (!expanded) setExpanded(true)
                        }
                        return true
                    }
                    MotionEvent.ACTION_OUTSIDE -> {
                        if (expanded) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastOutsideTapTime < doubleTapTimeout) {
                                setExpanded(false)
                                lastOutsideTapTime = 0
                            } else {
                                lastOutsideTapTime = currentTime
                            }
                        }
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
                val hasImage = e.clipDescription?.hasMimeType("image/*") == true
                if (hasText || hasImage) setExpanded(true)
                return hasText || hasImage
            }
            DragEvent.ACTION_DROP -> {
                e.clipData?.let { cd ->
                    for (i in 0 until cd.itemCount) {
                        val item = cd.getItemAt(i)
                        val uri = item.uri
                        val text = item.text?.toString()

                        if (uri != null) {
                            add(uri.toString(), true, "صورة")
                        } else if (text != null) {
                            add(text, false, "سحب")
                        }
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

    fun add(content: String, isImage: Boolean = false, source: String = "تطبيق") {
        if (content.trim().isEmpty()) return
        
        items.removeAll { it.content == content }
        
        val cardColor = when(source.lowercase()) {
            "whatsapp", "واتساب" -> Color.parseColor("#1B4D3E") 
            "chrome", "كروم" -> Color.parseColor("#4A2C6B") 
            else -> Color.parseColor("#1E3A6D") 
        }
        
        items.add(0, ShelfItem(content, isImage, source, cardColor))
        while (items.size > 30) items.removeAt(items.size - 1)
        render()
    }

    fun setExpanded(on: Boolean) {
        expanded = on
        // لما يفتح بنكبره، ولما يقفل بنصغره
        lp.width = if (on) (260 * dp).toInt() else (24 * dp).toInt()
        lp.height = if (on) (350 * dp).toInt() else (160 * dp).toInt()
        scroll.visibility = if (on) View.VISIBLE else View.GONE
        if (added) wm.updateViewLayout(root, lp)
    }

    private fun render() {
        list.removeAllViews()
        
        // شريط الإغلاق فقط
        val topBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, (10 * dp).toInt())
        }
        
        val closeBtn = TextView(ctx).apply {
            text = "✕ إغلاق"
            setTextColor(Color.parseColor("#FF5A5F"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { setExpanded(false) }
        }

        topBar.addView(closeBtn)
        list.addView(topBar)

        if (items.isEmpty()) {
            list.addView(TextView(ctx).apply {
                text = "الرف فاضي، اسحب نص أو صورة هنا"
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

        if (item.isImage) {
            val img = ImageView(ctx)
            img.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (150 * dp).toInt()
            )
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            try {
                img.setImageURI(Uri.parse(item.content))
            } catch (e: Exception) {
                // لو حصل خطأ في تحميل الصورة
            }
            card.addView(img)
        } else {
            val tvText = TextView(ctx)
            tvText.text = item.content
            tvText.maxLines = 3
            tvText.setTextColor(Color.WHITE)
            tvText.textSize = 14f
            tvText.setTypeface(null, Typeface.BOLD)
            card.addView(tvText)
        }

        val tvSource = TextView(ctx)
        tvSource.text = "من: ${item.source}"
        tvSource.setTextColor(Color.parseColor("#4DE1E1"))
        tvSource.textSize = 11f
        tvSource.setPadding(0, (4 * dp).toInt(), 0, 0)
        card.addView(tvSource)
        
        card.setOnClickListener { 
            if (item.isImage) {
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newUri(ctx.contentResolver, "image", Uri.parse(item.content))
                clipboard.setPrimaryClip(clip)
            } else {
                onInsert(item.content)
            }
            setExpanded(false)
        }
        
        card.setOnLongClickListener { v: View ->
            val clip = if (item.isImage) {
                ClipData.newUri(ctx.contentResolver, "image", Uri.parse(item.content))
            } else {
                ClipData.newPlainText("shelf", item.content)
            }
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
