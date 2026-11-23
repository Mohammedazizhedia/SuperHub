// Built with BFB.AI
package com.bfbai.superhub.service

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color

/** إعدادات التطبيق العامة: اللغة ووضع الثيم (تلقائي/داكن/نهاري) والستايل الداكن */
object AppPrefs {
    private var p: SharedPreferences? = null

    @Synchronized
    fun init(ctx: Context) {
        if (p == null) p = ctx.applicationContext.getSharedPreferences("hubsync_app", Context.MODE_PRIVATE)
    }

    private fun s(k: String, d: String): String = p?.getString(k, d) ?: d
    private fun put(k: String, v: String) {
        p?.edit()?.putString(k, v)?.apply()
    }

    var lang: String
        get() = s("lang", "ar")
        set(v) = put("lang", v)

    var themeMode: String
        get() = s("theme_mode", "system")
        set(v) = put("theme_mode", v)

    var darkStyle: String
        get() = s("dark_style", "classic")
        set(v) = put("dark_style", v)

    var applied: String
        get() = s("applied", "")
        set(v) = put("applied", v)
}

object Lang {
    fun isAr(): Boolean = AppPrefs.lang != "en"
}

/** ترجمة خارج الواجهات (رسائل Toast وغيره) */
fun tr(ar: String, en: String): String = if (Lang.isAr()) ar else en

class Pal(
    val bg: Int,
    val card: Int,
    val card2: Int,
    val stroke: Int,
    val text: Int,
    val sub: Int,
    val accent: Int,
    val onAccent: Int,
    val danger: Int,
    val dark: Boolean
)

object ThemeEngine {

    fun init(ctx: Context) {
        AppPrefs.init(ctx)
        ShelfSettings.init(ctx)
        val n = presetName(ctx)
        // أول مرة: نسجّل الثيم الحالي من غير ما نمسح ألوانك المخصصة
        if (AppPrefs.applied.isEmpty()) AppPrefs.applied = n else ensure(ctx)
    }

    fun systemDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun resolveDark(ctx: Context): Boolean = when (AppPrefs.themeMode) {
        "dark" -> true
        "day" -> false
        else -> systemDark(ctx)
    }

    fun presetName(ctx: Context): String = if (resolveDark(ctx)) AppPrefs.darkStyle else "day"

    // بيطبّق الثيم بس لو اتغيّر (يعني ألوانك المخصصة بتفضل طالما الثيم هو هو)
    fun ensure(ctx: Context, force: Boolean = false) {
        val n = presetName(ctx)
        if (force || AppPrefs.applied != n) {
            AppPrefs.applied = n
            ShelfSettings.applyPreset(n)
        }
    }
}

object Palette {

    private fun over(top: Int, under: Int): Int {
        val a = Color.alpha(top) / 255f
        val r = (Color.red(top) * a + Color.red(under) * (1 - a)).toInt()
        val g = (Color.green(top) * a + Color.green(under) * (1 - a)).toInt()
        val b = (Color.blue(top) * a + Color.blue(under) * (1 - a)).toInt()
        return Color.rgb(r, g, b)
    }

    fun luminance(c: Int): Float =
        (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

    // ألوان الإعدادات والسجل مأخوذة من نفس ألوان الرف، فالمظهر موحّد
    fun get(ctx: Context): Pal {
        val dark = ThemeEngine.resolveDark(ctx)
        val pc = ShelfStyle.panelColor
        val bg = Color.rgb(Color.red(pc), Color.green(pc), Color.blue(pc))
        val cardA = maxOf(ShelfStyle.cardFillAlpha, 0x40)
        val card = over(withAlpha(ShelfStyle.cardFillColor, cardA), bg)
        val card2 = over(withAlpha(if (dark) Color.WHITE else Color.BLACK, 0x0C), card)
        val stroke = withAlpha(ShelfStyle.cardStrokeColor, maxOf(ShelfStyle.cardStrokeAlpha, 0x33))
        val accent = withAlpha(ShelfStyle.accent, 0xFF)
        val onAccent = if (luminance(accent) > 0.6f) Color.parseColor("#0A1730") else Color.WHITE
        val text = withAlpha(ShelfStyle.textColor, 0xFF)
        val sub = over(ShelfStyle.subTextColor, bg)
        val danger = if (dark) Color.parseColor("#FF6B6B") else Color.parseColor("#D93036")
        return Pal(bg, card, card2, stroke, text, sub, accent, onAccent, danger, dark)
    }
}
