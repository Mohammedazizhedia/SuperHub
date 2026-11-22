// Built with BFB.AI
package com.bfbai.superhub.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * كل خيارات سلوك الرف (التجميع، اللصق، طريقة الفتح، مكان الرف).
 * الواجهة بتاعتها هتيجي مع شاشة الإعدادات.
 */
object ShelfBehavior {

    // كارت واحد صغير يظهر ويختفي لوحده
    const val OPEN_PEEK = "peek"

    // الرف يتفتح كامل
    const val OPEN_FULL = "full"

    // نبضة على المقبض من غير ما يتفتح
    const val OPEN_PULSE = "pulse"

    data class SavedPosition(
        val x: Int,
        val y: Int,
        val snapL: Boolean,
        val snapR: Boolean,
        val snapT: Boolean,
        val snapB: Boolean
    )

    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(ctx: Context) {
        if (prefs == null) {
            prefs = ctx.applicationContext.getSharedPreferences("hubsync_behavior", Context.MODE_PRIVATE)
        }
    }

    private fun bool(k: String, d: Boolean): Boolean = prefs?.getBoolean(k, d) ?: d
    private fun int(k: String, d: Int): Int = prefs?.getInt(k, d) ?: d
    private fun str(k: String, d: String): String = prefs?.getString(k, d) ?: d
    private fun putBool(k: String, v: Boolean) { prefs?.edit()?.putBoolean(k, v)?.apply() }
    private fun putInt(k: String, v: Int) { prefs?.edit()?.putInt(k, v)?.apply() }
    private fun putStr(k: String, v: String) { prefs?.edit()?.putString(k, v)?.apply() }

    // ---------- التجميع ----------

    // تجميع تلقائي لنسخ متقارب
    var autoGroup: Boolean
        get() = bool("auto_group", true)
        set(v) = putBool("auto_group", v)

    // المدة (ثواني) اللي بين نسختين عشان يتجمعوا
    var groupWindowSec: Int
        get() = int("group_window_sec", 120)
        set(v) = putInt("group_window_sec", v)

    // يجمّع بس لو النسختين من نفس التطبيق
    var groupSameApp: Boolean
        get() = bool("group_same_app", true)
        set(v) = putBool("group_same_app", v)

    // لو في المجموعة اسم ملف كود: المدة بتتسع لـ 5 دقايق
    var groupCodeFile: Boolean
        get() = bool("group_code_file", true)
        set(v) = putBool("group_code_file", v)

    // اسم المجموعة من اسم الملف تلقائي (لو ما اتسماش يدوي)
    var groupAutoName: Boolean
        get() = bool("group_auto_name", true)
        set(v) = putBool("group_auto_name", v)

    // المجموعة اليدوية بتتقفل لوحدها بعد (دقايق) من آخر نسخ. 0 = أبداً
    var manualIdleMin: Int
        get() = int("manual_idle_min", 30)
        set(v) = putInt("manual_idle_min", v)

    // ---------- اللصق ----------

    // يخفّت الكارت اللي اتلصق
    var pasteDim: Boolean
        get() = bool("paste_dim", true)
        set(v) = putBool("paste_dim", v)

    // يحذف العنصر بعد ما يتلصق
    var autoRemoveAfterPaste: Boolean
        get() = bool("auto_remove_after_paste", false)
        set(v) = putBool("auto_remove_after_paste", v)

    // اللي لسه ما اتلصقش يبقى قبل اللي اتلصق
    var sortUnpastedFirst: Boolean
        get() = bool("sort_unpasted_first", false)
        set(v) = putBool("sort_unpasted_first", v)

    // ---------- الفتح ----------

    // لما يجي نسخ جديد: peek (معاينة) أو full (كامل) أو pulse (نبضة)
    var openOnCopy: String
        get() = str("open_on_copy", OPEN_PEEK)
        set(v) = putStr("open_on_copy", v)

    // مدة المعاينة (ملي ثانية)
    var peekMs: Int
        get() = int("peek_ms", 2500)
        set(v) = putInt("peek_ms", v)

    // ---------- طي المجموعات ----------

    fun isCollapsed(id: String): Boolean {
        val s = prefs?.getStringSet("collapsed_groups", emptySet()) ?: return false
        return s.contains(id)
    }

    fun setCollapsed(id: String, collapsed: Boolean) {
        val p = prefs ?: return
        val s = HashSet<String>(p.getStringSet("collapsed_groups", emptySet()) ?: emptySet())
        if (collapsed) s.add(id) else s.remove(id)
        p.edit().putStringSet("collapsed_groups", s).apply()
    }

    // ---------- مكان الرف ----------

    fun savePosition(x: Int, y: Int, l: Boolean, r: Boolean, t: Boolean, b: Boolean) {
        prefs?.edit()
            ?.putInt("pos_x", x)
            ?.putInt("pos_y", y)
            ?.putBoolean("snap_l", l)
            ?.putBoolean("snap_r", r)
            ?.putBoolean("snap_t", t)
            ?.putBoolean("snap_b", b)
            ?.putBoolean("pos_saved", true)
            ?.apply()
    }

    fun loadPosition(): SavedPosition? {
        val p = prefs ?: return null
        if (!p.getBoolean("pos_saved", false)) return null
        return SavedPosition(
            p.getInt("pos_x", 0),
            p.getInt("pos_y", 0),
            p.getBoolean("snap_l", false),
            p.getBoolean("snap_r", true),
            p.getBoolean("snap_t", false),
            p.getBoolean("snap_b", false)
        )
    }
}
