// Built with BFB.AI
package com.bfbai.superhub.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * سياسة الخصوصية: مين يتلقط منه ومين لأ. الواجهة بتاعتها هتيجي مع شاشة الإعدادات.
 * الافتراضي: يلتقط من كل التطبيقات، ومفيش استثناءات، والمسح التلقائي مقفول.
 */
object PrivacyPolicy {

    // يلتقط من الكل ما عدا قائمة المستثنى
    const val MODE_ALL = "all"

    // يلتقط من تطبيقات القائمة المسموحة بس
    const val MODE_ALLOW_ONLY = "allow_only"

    private lateinit var prefs: SharedPreferences

    @Volatile
    private var privateUntil: Long = 0L

    @Synchronized
    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("hubsync_privacy", Context.MODE_PRIVATE)
        privateUntil = prefs.getLong("private_until", 0L)
    }

    // ---------- الوضع الخاص ----------

    fun isPrivateMode(): Boolean = System.currentTimeMillis() < privateUntil

    fun privateRemainingMs(): Long = maxOf(0L, privateUntil - System.currentTimeMillis())

    fun startPrivate(minutes: Int) {
        privateUntil = System.currentTimeMillis() + minutes * 60_000L
        if (::prefs.isInitialized) prefs.edit().putLong("private_until", privateUntil).apply()
    }

    fun stopPrivate() {
        privateUntil = 0L
        if (::prefs.isInitialized) prefs.edit().putLong("private_until", 0L).apply()
    }

    // ---------- التطبيقات المستثناة / المسموحة ----------

    fun mode(): String {
        if (!::prefs.isInitialized) return MODE_ALL
        return prefs.getString("mode", MODE_ALL) ?: MODE_ALL
    }

    fun setMode(m: String) {
        if (::prefs.isInitialized) prefs.edit().putString("mode", m).apply()
    }

    fun blockedApps(): Set<String> {
        if (!::prefs.isInitialized) return emptySet()
        return HashSet<String>(prefs.getStringSet("blocked", emptySet()) ?: emptySet())
    }

    fun allowedApps(): Set<String> {
        if (!::prefs.isInitialized) return emptySet()
        return HashSet<String>(prefs.getStringSet("allowed", emptySet()) ?: emptySet())
    }

    fun setBlockedApps(s: Set<String>) {
        if (::prefs.isInitialized) prefs.edit().putStringSet("blocked", HashSet<String>(s)).apply()
    }

    fun setAllowedApps(s: Set<String>) {
        if (::prefs.isInitialized) prefs.edit().putStringSet("allowed", HashSet<String>(s)).apply()
    }

    fun blockApp(pkg: String) {
        val s = HashSet<String>(blockedApps())
        s.add(pkg)
        setBlockedApps(s)
    }

    fun unblockApp(pkg: String) {
        val s = HashSet<String>(blockedApps())
        s.remove(pkg)
        setBlockedApps(s)
    }

    // ---------- المسح التلقائي ----------

    // 0 = مقفول
    fun autoWipeDays(): Int {
        if (!::prefs.isInitialized) return 0
        return prefs.getInt("auto_wipe_days", 0)
    }

    fun setAutoWipeDays(days: Int) {
        if (::prefs.isInitialized) prefs.edit().putInt("auto_wipe_days", days).apply()
    }

    // ---------- القرار ----------

    // هل مسموح نلتقط نسخ جاي من التطبيق ده؟
    fun allowsCapture(pkg: String?): Boolean {
        if (!::prefs.isInitialized) return true
        if (isPrivateMode()) return false
        return if (mode() == MODE_ALLOW_ONLY) {
            pkg != null && pkg in allowedApps()
        } else {
            pkg == null || pkg !in blockedApps()
        }
    }
}
