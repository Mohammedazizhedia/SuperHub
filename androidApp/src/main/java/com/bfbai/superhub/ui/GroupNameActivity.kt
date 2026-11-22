// Built with BFB.AI
package com.bfbai.superhub.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.bfbai.superhub.utils.HistoryRepository

/** شاشة صغيرة لتسمية مجموعة (الاسم اختياري) أو بدء مجموعة يدوية */
class GroupNameActivity : Activity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_GROUP = "group"
        const val EXTRA_NAME = "name"

        // تسمية مجموعة موجودة
        const val MODE_RENAME = "rename"

        // بدء مجموعة يدوية جديدة
        const val MODE_START = "start"

        // تسمية مجموعة لسه متعملة (اختياري)
        const val MODE_NAME_NEW = "name_new"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        HistoryRepository.init(this)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_RENAME
        val groupId = intent.getStringExtra(EXTRA_GROUP)
        val current = intent.getStringExtra(EXTRA_NAME)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setPadding(dp(20), dp(20), dp(20), dp(16))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(18).toFloat()
        bg.setColor(Color.parseColor("#FF0B1B3A"))
        bg.setStroke(dp(1), Color.parseColor("#554DE1E1"))
        root.background = bg

        val title = TextView(this)
        title.text = if (mode == MODE_START) "مجموعة جديدة" else "اسم المجموعة"
        title.textSize = 18f
        title.setTextColor(Color.WHITE)
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        root.addView(title)

        val hint = TextView(this)
        hint.text = if (mode == MODE_START) {
            "أي نسخ جديد هيتحط فيها لحد ما تنهيها. الاسم اختياري."
        } else {
            "الاسم اختياري. ممكن تسيبه فاضي."
        }
        hint.textSize = 12f
        hint.setTextColor(Color.parseColor("#9FB3D1"))
        hint.setPadding(0, dp(4), 0, dp(12))
        root.addView(hint)

        val input = EditText(this)
        input.hint = "اسم المجموعة (اختياري)"
        input.setHintTextColor(Color.parseColor("#779FB3D1"))
        input.setTextColor(Color.WHITE)
        input.textSize = 15f
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.filters = arrayOf(InputFilter.LengthFilter(40))
        input.setSingleLine(true)
        input.setPadding(dp(14), dp(12), dp(14), dp(12))
        val ibg = GradientDrawable()
        ibg.cornerRadius = dp(12).toFloat()
        ibg.setColor(Color.parseColor("#22FFFFFF"))
        input.background = ibg
        if (!current.isNullOrBlank()) {
            input.setText(current)
            input.setSelection(current.length)
        }
        root.addView(input)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        val rp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rp.topMargin = dp(16)
        row.layoutParams = rp

        fun apply(name: String?) {
            when (mode) {
                MODE_START -> {
                    HistoryRepository.startManualGroup(name)
                    Toast.makeText(
                        this,
                        "المجموعة بدأت. أي نسخ جديد هيدخلها لحد ما تنهيها من قايمة الرف",
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {
                    if (groupId != null) HistoryRepository.setGroupName(groupId, name)
                }
            }
            finish()
        }

        fun button(label: String, primary: Boolean, onClick: () -> Unit): TextView {
            val b = TextView(this)
            b.text = label
            b.textSize = 14f
            b.setPadding(dp(16), dp(10), dp(16), dp(10))
            val bb = GradientDrawable()
            bb.cornerRadius = dp(20).toFloat()
            if (primary) {
                bb.setColor(Color.parseColor("#FF4DE1E1"))
                b.setTextColor(Color.parseColor("#FF0A1730"))
                b.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                bb.setColor(Color.TRANSPARENT)
                bb.setStroke(dp(1), Color.parseColor("#994DE1E1"))
                b.setTextColor(Color.parseColor("#FF4DE1E1"))
            }
            b.background = bb
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginStart = dp(8)
            b.layoutParams = lp
            b.setOnClickListener { onClick() }
            return b
        }

        row.addView(button(if (mode == MODE_START) "ابدأ" else "حفظ", true) {
            apply(input.text.toString())
        })
        row.addView(button("بدون اسم", false) { apply(null) })
        row.addView(button("إلغاء", false) { finish() })
        root.addView(row)

        setContentView(root)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
    }
}
