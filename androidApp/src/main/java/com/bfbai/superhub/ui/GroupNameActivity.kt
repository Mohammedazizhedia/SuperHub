// Built with BFB.AI
package com.bfbai.superhub.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
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
import com.bfbai.superhub.service.Lang
import com.bfbai.superhub.service.Palette
import com.bfbai.superhub.service.ThemeEngine
import com.bfbai.superhub.service.tr
import com.bfbai.superhub.utils.HistoryRepository

/** شاشة صغيرة لتسمية مجموعة (الاسم اختياري) أو بدء مجموعة يدوية */
class GroupNameActivity : Activity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_GROUP = "group"
        const val EXTRA_NAME = "name"
        const val MODE_RENAME = "rename"
        const val MODE_START = "start"
        const val MODE_NAME_NEW = "name_new"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        ThemeEngine.init(this)
        HistoryRepository.init(this)

        val pal = Palette.get(this)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_RENAME
        val groupId = intent.getStringExtra(EXTRA_GROUP)
        val current = intent.getStringExtra(EXTRA_NAME)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = if (Lang.isAr()) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        root.setPadding(dp(20), dp(20), dp(20), dp(16))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(20).toFloat()
        bg.setColor(pal.bg)
        bg.setStroke(dp(1), pal.stroke)
        root.background = bg

        val title = TextView(this)
        title.text = if (mode == MODE_START) tr("مجموعة جديدة", "New group") else tr("اسم المجموعة", "Group name")
        title.textSize = 18f
        title.setTextColor(pal.text)
        title.setTypeface(null, Typeface.BOLD)
        root.addView(title)

        val hint = TextView(this)
        hint.text = if (mode == MODE_START) {
            tr("أي نسخ جديد هيتحط فيها لحد ما تنهيها. الاسم اختياري.", "Every new copy goes into it until you end it. The name is optional.")
        } else {
            tr("الاسم اختياري. ممكن تسيبه فاضي.", "The name is optional. You can leave it empty.")
        }
        hint.textSize = 12f
        hint.setTextColor(pal.sub)
        hint.setPadding(0, dp(4), 0, dp(12))
        root.addView(hint)

        val input = EditText(this)
        input.hint = tr("اسم المجموعة (اختياري)", "Group name (optional)")
        input.setHintTextColor(pal.sub)
        input.setTextColor(pal.text)
        input.textSize = 15f
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.filters = arrayOf(InputFilter.LengthFilter(40))
        input.setSingleLine(true)
        input.setPadding(dp(14), dp(12), dp(14), dp(12))
        val ibg = GradientDrawable()
        ibg.cornerRadius = dp(14).toFloat()
        ibg.setColor(pal.card2)
        ibg.setStroke(dp(1), pal.stroke)
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
            if (mode == MODE_START) {
                HistoryRepository.startManualGroup(name)
                Toast.makeText(
                    this,
                    tr("المجموعة بدأت. أي نسخ جديد هيدخلها لحد ما تنهيها من قايمة الرف", "Group started. New copies go into it until you end it from the shelf menu"),
                    Toast.LENGTH_LONG
                ).show()
            } else if (groupId != null) {
                HistoryRepository.setGroupName(groupId, name)
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
                bb.setColor(pal.accent)
                b.setTextColor(pal.onAccent)
                b.setTypeface(null, Typeface.BOLD)
            } else {
                bb.setColor(Color.TRANSPARENT)
                bb.setStroke(dp(1), pal.stroke)
                b.setTextColor(pal.accent)
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

        row.addView(button(if (mode == MODE_START) tr("ابدأ", "Start") else tr("حفظ", "Save"), true) {
            apply(input.text.toString())
        })
        row.addView(button(tr("بدون اسم", "No name"), false) { apply(null) })
        row.addView(button(tr("إلغاء", "Cancel"), false) { finish() })
        root.addView(row)

        setContentView(root)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
    }
}
