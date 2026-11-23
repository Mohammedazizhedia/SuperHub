// Built with BFB.AI
package com.bfbai.superhub.service

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Icon
import com.bfbai.superhub.utils.PrivacyPolicy
import com.bfbai.superhub.utils.ShelfBehavior
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * سجل واحد لكل الإعدادات: شاشة الإعدادات بتقرا وتكتب بالمفتاح، والرف بيتحدث فوراً.
 * قيم ShelfStyle بتتحفظ هنا، وقيم ShelfBehavior بتتحفظ في ملفها هي.
 */
object ShelfSettings {

    private class Num(val key: String, val def: Float, val read: () -> Float, val write: (Float) -> Unit, val stored: Boolean)
    private class Col(val key: String, val def: Int, val read: () -> Int, val write: (Int) -> Unit)
    private class Flag(val key: String, val def: Boolean, val read: () -> Boolean, val write: (Boolean) -> Unit, val stored: Boolean)
    private class Txt(val key: String, val def: String, val read: () -> String, val write: (String) -> Unit, val stored: Boolean)

    private val nums = ArrayList<Num>()
    private val cols = ArrayList<Col>()
    private val flags = ArrayList<Flag>()
    private val txts = ArrayList<Txt>()

    private var prefs: SharedPreferences? = null
    private val ver = MutableStateFlow(0)
    val changes: StateFlow<Int> = ver.asStateFlow()

    private var appIconKey: String = "shelf"
    private var tileIconKey: String = "shelf"

    // قيم افتراضية من ShelfStyle نفسه (قبل أي تحميل)
    private fun n(key: String, read: () -> Float, write: (Float) -> Unit) {
        nums.add(Num(key, read(), read, write, true))
    }

    // قيمة مشتقة: بتتحفظ من خلال لون تاني
    private fun nv(key: String, read: () -> Float, write: (Float) -> Unit) {
        nums.add(Num(key, read(), read, write, false))
    }

    private fun nb(key: String, def: Float, read: () -> Float, write: (Float) -> Unit) {
        nums.add(Num(key, def, read, write, false))
    }

    private fun c(key: String, read: () -> Int, write: (Int) -> Unit) {
        cols.add(Col(key, read(), read, write))
    }

    private fun f(key: String, read: () -> Boolean, write: (Boolean) -> Unit) {
        flags.add(Flag(key, read(), read, write, true))
    }

    private fun fb(key: String, def: Boolean, read: () -> Boolean, write: (Boolean) -> Unit) {
        flags.add(Flag(key, def, read, write, false))
    }

    private fun t(key: String, read: () -> String, write: (String) -> Unit) {
        txts.add(Txt(key, read(), read, write, true))
    }

    private fun tb(key: String, def: String, read: () -> String, write: (String) -> Unit) {
        txts.add(Txt(key, def, read, write, false))
    }

    init {
        // ---- أرقام (ShelfStyle) ----
        n("cardFillAlpha", { ShelfStyle.cardFillAlpha.toFloat() }, { ShelfStyle.cardFillAlpha = it.toInt() })
        n("cardStrokeAlpha", { ShelfStyle.cardStrokeAlpha.toFloat() }, { ShelfStyle.cardStrokeAlpha = it.toInt() })
        n("cardGradientEndAlpha", { ShelfStyle.cardGradientEndAlpha.toFloat() }, { ShelfStyle.cardGradientEndAlpha = it.toInt() })
        n("cornerDp", { ShelfStyle.cornerDp }, { ShelfStyle.cornerDp = it })
        n("cardCornerDp", { ShelfStyle.cardCornerDp }, { ShelfStyle.cardCornerDp = it })
        n("blurRadiusDp", { ShelfStyle.blurRadiusDp }, { ShelfStyle.blurRadiusDp = it })
        n("closedWidthDp", { ShelfStyle.closedWidthDp }, { ShelfStyle.closedWidthDp = it })
        n("closedHeightDp", { ShelfStyle.closedHeightDp }, { ShelfStyle.closedHeightDp = it })
        n("expandedWidthDp", { ShelfStyle.expandedWidthDp }, { ShelfStyle.expandedWidthDp = it })
        n("expandedHeightDp", { ShelfStyle.expandedHeightDp }, { ShelfStyle.expandedHeightDp = it })
        n("peekHeightDp", { ShelfStyle.peekHeightDp }, { ShelfStyle.peekHeightDp = it })
        n("dropZoneWidthDp", { ShelfStyle.dropZoneWidthDp }, { ShelfStyle.dropZoneWidthDp = it })
        n("dropZoneHeightDp", { ShelfStyle.dropZoneHeightDp }, { ShelfStyle.dropZoneHeightDp = it })
        n("snapThresholdDp", { ShelfStyle.snapThresholdDp }, { ShelfStyle.snapThresholdDp = it })
        n("swipeThreshold", { ShelfStyle.swipeThreshold }, { ShelfStyle.swipeThreshold = it })
        n("throwMinSpeedDp", { ShelfStyle.throwMinSpeedDp }, { ShelfStyle.throwMinSpeedDp = it })
        n("pastedAlpha", { ShelfStyle.pastedAlpha }, { ShelfStyle.pastedAlpha = it })
        n("previewChars", { ShelfStyle.previewChars.toFloat() }, { ShelfStyle.previewChars = it.toInt() })
        n("textScale", { ShelfStyle.textScale }, { ShelfStyle.textScale = it })
        n("autoCloseSec", { ShelfStyle.autoCloseMs / 1000f }, { ShelfStyle.autoCloseMs = (it * 1000f).toLong() })
        n("otpTtlSec", { ShelfStyle.otpTtlMs / 1000f }, { ShelfStyle.otpTtlMs = (it * 1000f).toLong() })
        nv(
            "panelAlpha",
            { Color.alpha(ShelfStyle.panelColor) / 255f },
            { ShelfStyle.panelColor = withAlpha(ShelfStyle.panelColor, (it * 255f).toInt().coerceIn(0, 255)) }
        )

        // ---- أرقام (ShelfBehavior) ----
        nb("groupWindowSec", 120f, { ShelfBehavior.groupWindowSec.toFloat() }, { ShelfBehavior.groupWindowSec = it.toInt() })
        nb("manualIdleMin", 30f, { ShelfBehavior.manualIdleMin.toFloat() }, { ShelfBehavior.manualIdleMin = it.toInt() })
        nb("peekMs", 2500f, { ShelfBehavior.peekMs.toFloat() }, { ShelfBehavior.peekMs = it.toInt() })
        nb("idleDriftSec", 8f, { ShelfBehavior.idleDriftSec.toFloat() }, { ShelfBehavior.idleDriftSec = it.toInt() })

        // ---- ألوان ----
        c("panelColor", { ShelfStyle.panelColor }, { ShelfStyle.panelColor = it })
        c("panelStroke", { ShelfStyle.panelStroke }, { ShelfStyle.panelStroke = it })
        c("handleColor", { ShelfStyle.handleColor }, { ShelfStyle.handleColor = it })
        c("cardFillColor", { ShelfStyle.cardFillColor }, { ShelfStyle.cardFillColor = it })
        c("cardStrokeColor", { ShelfStyle.cardStrokeColor }, { ShelfStyle.cardStrokeColor = it })
        c("accent", { ShelfStyle.accent }, { ShelfStyle.accent = it })
        c("textColor", { ShelfStyle.textColor }, { ShelfStyle.textColor = it })
        c("subTextColor", { ShelfStyle.subTextColor }, { ShelfStyle.subTextColor = it })
        c("metaColor", { ShelfStyle.metaColor }, { ShelfStyle.metaColor = it })
        c("menuColor", { ShelfStyle.menuColor }, { ShelfStyle.menuColor = it })
        c("deleteColorStart", { ShelfStyle.deleteColorStart }, { ShelfStyle.deleteColorStart = it })
        c("deleteColorEnd", { ShelfStyle.deleteColorEnd }, { ShelfStyle.deleteColorEnd = it })
        c("neutralColor", { ShelfStyle.neutralColor }, { ShelfStyle.neutralColor = it })
        c("imageColor", { ShelfStyle.imageColor }, { ShelfStyle.imageColor = it })

        // ---- مفاتيح (ShelfStyle) ----
        f("panelGradient", { ShelfStyle.panelGradient }, { ShelfStyle.panelGradient = it })
        f("cardGradient", { ShelfStyle.cardGradient }, { ShelfStyle.cardGradient = it })
        f("cardTintByKind", { ShelfStyle.cardTintByKind }, { ShelfStyle.cardTintByKind = it })
        f("rotateTextColors", { ShelfStyle.rotateTextColors }, { ShelfStyle.rotateTextColors = it })
        f("boldText", { ShelfStyle.boldText }, { ShelfStyle.boldText = it })
        f("blurEnabled", { ShelfStyle.blurEnabled }, { ShelfStyle.blurEnabled = it })
        f("dragWakesShelf", { ShelfStyle.dragWakesShelf }, { ShelfStyle.dragWakesShelf = it })
        f("throwEnabled", { ShelfStyle.throwEnabled }, { ShelfStyle.throwEnabled = it })
        f("debug", { ShelfStyle.debug }, { ShelfStyle.debug = it })

        // ---- مفاتيح (ShelfBehavior) ----
        fb("autoGroup", true, { ShelfBehavior.autoGroup }, { ShelfBehavior.autoGroup = it })
        fb("groupSameApp", true, { ShelfBehavior.groupSameApp }, { ShelfBehavior.groupSameApp = it })
        fb("groupCodeFile", true, { ShelfBehavior.groupCodeFile }, { ShelfBehavior.groupCodeFile = it })
        fb("groupAutoName", true, { ShelfBehavior.groupAutoName }, { ShelfBehavior.groupAutoName = it })
        fb("dragToGroup", true, { ShelfBehavior.dragToGroup }, { ShelfBehavior.dragToGroup = it })
        fb("groupsStartCollapsed", true, { ShelfBehavior.groupsStartCollapsed }, { ShelfBehavior.groupsStartCollapsed = it })
        fb("pasteDim", true, { ShelfBehavior.pasteDim }, { ShelfBehavior.pasteDim = it })
        fb("autoRemoveAfterPaste", false, { ShelfBehavior.autoRemoveAfterPaste }, { ShelfBehavior.autoRemoveAfterPaste = it })
        fb("sortUnpastedFirst", false, { ShelfBehavior.sortUnpastedFirst }, { ShelfBehavior.sortUnpastedFirst = it })
        fb("idleDrift", true, { ShelfBehavior.idleDrift }, { ShelfBehavior.idleDrift = it })
        fb("autoCapture", true, { ShelfBehavior.autoCapture }, { ShelfBehavior.autoCapture = it })
        fb("ignoreSensitive", true, { ShelfBehavior.ignoreSensitive }, { ShelfBehavior.ignoreSensitive = it })
        fb("otpAutoDelete", true, { ShelfBehavior.otpAutoDelete }, { ShelfBehavior.otpAutoDelete = it })

        // ---- نصوص ----
        t("throwAction", { ShelfStyle.throwAction }, { ShelfStyle.throwAction = it })
        t("fontFamily", { ShelfStyle.fontFamily }, { ShelfStyle.fontFamily = it })
        tb("openOnCopy", ShelfBehavior.OPEN_PEEK, { ShelfBehavior.openOnCopy }, { ShelfBehavior.openOnCopy = it })
    }

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        val app = ctx.applicationContext
        ShelfBehavior.init(app)
        prefs = app.getSharedPreferences("hubsync_style", Context.MODE_PRIVATE)
        load()
    }

    private fun load() {
        val p = prefs ?: return
        for (e in nums) if (e.stored && p.contains(e.key)) e.write(p.getFloat(e.key, e.def))
        for (e in cols) if (p.contains(e.key)) e.write(p.getInt(e.key, e.def))
        for (e in flags) if (e.stored && p.contains(e.key)) e.write(p.getBoolean(e.key, e.def))
        for (e in txts) if (e.stored && p.contains(e.key)) e.write(p.getString(e.key, e.def) ?: e.def)
        appIconKey = p.getString("appIcon", "shelf") ?: "shelf"
        tileIconKey = p.getString("tileIcon", "shelf") ?: "shelf"
    }

    private fun persistAll() {
        val ed = prefs?.edit() ?: return
        for (e in nums) if (e.stored) ed.putFloat(e.key, e.read())
        for (e in cols) ed.putInt(e.key, e.read())
        for (e in flags) if (e.stored) ed.putBoolean(e.key, e.read())
        for (e in txts) if (e.stored) ed.putString(e.key, e.read())
        ed.putString("appIcon", appIconKey)
        ed.putString("tileIcon", tileIconKey)
        ed.apply()
    }

    private fun bump() {
        ver.value = ver.value + 1
    }

    fun notifyChanged() = bump()

    // ---------- قراءة ----------
    fun num(key: String): Float {
        val e = nums.firstOrNull { it.key == key } ?: return 0f
        return e.read()
    }

    fun col(key: String): Int {
        val e = cols.firstOrNull { it.key == key } ?: return 0
        return e.read()
    }

    fun flag(key: String): Boolean {
        val e = flags.firstOrNull { it.key == key } ?: return false
        return e.read()
    }

    fun txt(key: String): String {
        val e = txts.firstOrNull { it.key == key } ?: return ""
        return e.read()
    }

    // ---------- كتابة ----------
    fun setNum(key: String, v: Float) {
        val e = nums.firstOrNull { it.key == key } ?: return
        e.write(v)
        persistAll()
        bump()
    }

    fun setCol(key: String, v: Int) {
        val e = cols.firstOrNull { it.key == key } ?: return
        e.write(v)
        persistAll()
        bump()
    }

    fun setFlag(key: String, v: Boolean) {
        val e = flags.firstOrNull { it.key == key } ?: return
        e.write(v)
        persistAll()
        bump()
    }

    fun setTxt(key: String, v: String) {
        val e = txts.firstOrNull { it.key == key } ?: return
        e.write(v)
        persistAll()
        bump()
    }

    // ---------- ثيمات جاهزة ----------
    fun applyPreset(name: String) {
        ShelfStyle.applyPreset(name)
        persistAll()
        bump()
    }

    fun reset() {
        for (e in nums) e.write(e.def)
        for (e in cols) e.write(e.def)
        for (e in flags) e.write(e.def)
        for (e in txts) e.write(e.def)
        appIconKey = "shelf"
        tileIconKey = "shelf"
        prefs?.edit()?.clear()?.apply()
        bump()
    }

    // ---------- الأيقونات المختارة ----------
    fun appIcon(): String = appIconKey

    fun setAppIcon(k: String) {
        appIconKey = k
        persistAll()
        bump()
    }

    fun tileIcon(): String = tileIconKey

    fun setTileIcon(k: String) {
        tileIconKey = k
        persistAll()
        bump()
    }

    // ---------- نسخ احتياطي (JSON) ----------
    fun exportJson(): String {
        val o = JSONObject()
        o.put("hubsync", 1)
        for (e in nums) o.put(e.key, e.read().toDouble())
        for (e in cols) o.put(e.key, e.read())
        for (e in flags) o.put(e.key, e.read())
        for (e in txts) o.put(e.key, e.read())
        o.put("appIcon", appIconKey)
        o.put("tileIcon", tileIconKey)
        val priv = JSONObject()
        priv.put("mode", PrivacyPolicy.mode())
        priv.put("blocked", JSONArray(PrivacyPolicy.blockedApps().toList()))
        priv.put("allowed", JSONArray(PrivacyPolicy.allowedApps().toList()))
        priv.put("wipeDays", PrivacyPolicy.autoWipeDays())
        o.put("privacy", priv)
        return o.toString(2)
    }

    fun importJson(text: String): Boolean {
        try {
            val o = JSONObject(text.trim())
            if (!o.has("hubsync")) return false
            for (e in nums) if (o.has(e.key)) e.write(o.getDouble(e.key).toFloat())
            for (e in cols) if (o.has(e.key)) e.write(o.getInt(e.key))
            for (e in flags) if (o.has(e.key)) e.write(o.getBoolean(e.key))
            for (e in txts) if (o.has(e.key)) e.write(o.getString(e.key))
            if (o.has("appIcon")) appIconKey = o.getString("appIcon")
            if (o.has("tileIcon")) tileIconKey = o.getString("tileIcon")
            val priv = o.optJSONObject("privacy")
            if (priv != null) {
                PrivacyPolicy.setMode(priv.optString("mode", PrivacyPolicy.MODE_ALL))
                val b = HashSet<String>()
                val a = HashSet<String>()
                val ba = priv.optJSONArray("blocked")
                val aa = priv.optJSONArray("allowed")
                if (ba != null) for (i in 0 until ba.length()) b.add(ba.getString(i))
                if (aa != null) for (i in 0 until aa.length()) a.add(aa.getString(i))
                PrivacyPolicy.setBlockedApps(b)
                PrivacyPolicy.setAllowedApps(a)
                PrivacyPolicy.setAutoWipeDays(priv.optInt("wipeDays", 0))
            }
            persistAll()
            bump()
            return true
        } catch (e: Exception) {
            return false
        }
    }
}

/** اختيار أيقونة التطبيق (عن طريق activity-alias) وأيقونة الـ Tile */
object IconOptions {

    // (المفتاح، الاسم، مورد الأيقونة)
    val apps: List<Triple<String, String, String>> = listOf(
        Triple("shelf", "الرف (الحالي)", "ic_hubsync"),
        Triple("hub", "هب أزرق", "ic_hub_blue"),
        Triple("aurora", "هب بنفسجي", "ic_hub_aurora"),
        Triple("clip", "حافظة", "ic_hub_clip")
    )

    val tiles: List<Triple<String, String, String>> = listOf(
        Triple("shelf", "الرف", "ic_tile_shelf"),
        Triple("hub", "هب", "ic_tile_hub"),
        Triple("clip", "حافظة", "ic_tile_clip")
    )

    private fun aliasName(key: String): String =
        "com.bfbai.superhub.Alias" + key.replaceFirstChar { it.uppercase() }

    fun applyApp(ctx: Context, key: String) {
        val pm = ctx.packageManager
        for (a in apps) {
            val state = if (a.first == key) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            try {
                pm.setComponentEnabledSetting(
                    ComponentName(ctx.packageName, aliasName(a.first)),
                    state,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
            }
        }
    }

    fun tileIcon(ctx: Context): Icon? {
        val key = ShelfSettings.tileIcon()
        val name = tiles.firstOrNull { it.first == key }?.third ?: "ic_tile_shelf"
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        return if (id != 0) Icon.createWithResource(ctx, id) else null
    }
}
