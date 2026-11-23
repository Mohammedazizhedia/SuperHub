// Built with BFB.AI
package com.bfbai.superhub.utils

import android.content.Context
import android.content.SharedPreferences

/** كل خيارات سلوك الرف (التجميع، اللصق، الفتح، الالتقاط، مكان الرف) */
object ShelfBehavior {

    const val OPEN_PEEK = "peek"
    const val OPEN_FULL = "full"
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
    var autoGroup: Boolean
        get() = bool("auto_group", true)
        set(v) = putBool("auto_group", v)

    var groupWindowSec: Int
        get() = int("group_window_sec", 120)
        set(v) = putInt("group_window_sec", v)

    var groupSameApp: Boolean
        get() = bool("group_same_app", true)
        set(v) = putBool("group_same_app", v)

    var groupCodeFile: Boolean
        get() = bool("group_code_file", true)
        set(v) = putBool("group_code_file", v)

    var groupAutoName: Boolean
        get() = bool("group_auto_name", true)
        set(v) = putBool("group_auto_name", v)

    var manualIdleMin: Int
        get() = int("manual_idle_min", 30)
        set(v) = putInt("manual_idle_min", v)

    // السحب داخل الرف بيضم الكارت لمجموعة
    var dragToGroup: Boolean
        get() = bool("drag_to_group", true)
        set(v) = putBool("drag_to_group", v)

    // المجموعات بتبدأ مطوية
    var groupsStartCollapsed: Boolean
        get() = bool("groups_start_collapsed", true)
        set(v) = putBool("groups_start_collapsed", v)

    // ---------- اللصق ----------
    var pasteDim: Boolean
        get() = bool("paste_dim", true)
        set(v) = putBool("paste_dim", v)

    var autoRemoveAfterPaste: Boolean
        get() = bool("auto_remove_after_paste", false)
        set(v) = putBool("auto_remove_after_paste", v)

    var sortUnpastedFirst: Boolean
        get() = bool("sort_unpasted_first", false)
        set(v) = putBool("sort_unpasted_first", v)

    // ---------- الفتح ----------
    var openOnCopy: String
        get() = str("open_on_copy", OPEN_PEEK)
        set(v) = putStr("open_on_copy", v)

    var peekMs: Int
        get() = int("peek_ms", 2500)
        set(v) = putInt("peek_ms", v)

    // الرف يروح لأقرب حافة (يمين أو شمال) بعد فترة من غير لمس
    var idleDrift: Boolean
        get() = bool("idle_drift", true)
        set(v) = putBool("idle_drift", v)

    var idleDriftSec: Int
        get() = int("idle_drift_sec", 8)
        set(v) = putInt("idle_drift_sec", v)

    // ---------- الالتقاط ----------
    var autoCapture: Boolean
        get() = bool("auto_capture", true)
        set(v) = putBool("auto_capture", v)

    var ignoreSensitive: Boolean
        get() = bool("ignore_sensitive", true)
        set(v) = putBool("ignore_sensitive", v)

    // أكواد التحقق (OTP) بتتمسح لوحدها بعد المدة
    var otpAutoDelete: Boolean
        get() = bool("otp_auto_delete", true)
        set(v) = putBool("otp_auto_delete", v)

    // ---------- طي المجموعات ----------
    fun isCollapsed(id: String): Boolean {
        val p = prefs ?: return groupsStartCollapsed
        val ex = p.getStringSet("expanded_groups", emptySet()) ?: emptySet()
        if (ex.contains(id)) return false
        val co = p.getStringSet("collapsed_groups", emptySet()) ?: emptySet()
        if (co.contains(id)) return true
        return groupsStartCollapsed
    }

    fun setCollapsed(id: String, collapsed: Boolean) {
        val p = prefs ?: return
        val co = HashSet<String>(p.getStringSet("collapsed_groups", emptySet()) ?: emptySet())
        val ex = HashSet<String>(p.getStringSet("expanded_groups", emptySet()) ?: emptySet())
        if (collapsed) {
            co.add(id)
            ex.remove(id)
        } else {
            ex.add(id)
            co.remove(id)
        }
        p.edit().putStringSet("collapsed_groups", co).putStringSet("expanded_groups", ex).apply()
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
