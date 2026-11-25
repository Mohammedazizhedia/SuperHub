// Built with BFB.AI
package com.bfbai.superhub.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * سياسة الخصوصية: مين يتلقط منه ومين لأ.
 * الافتراضي: يلتقط من كل التطبيقات، ومفيش استثناءات، والمسح التلقائي مقفول.
 */
object PrivacyPolicy {

    // يلتقط من الكل ما عدا قائمة المستثنى
    const val MODE_ALL = "all"

    // يلتقط من تطبيقات القائمة المسموحة بس
    const val MODE_ALLOW_ONLY = "allow_only"

    private var prefs: SharedPreferences? = null

    @Volatile
    private var privateUntil: Long = 0L

    // أي تغيير هنا بيتبلّغ للشاشات اللي بتعرضه (الإعدادات) فتتحدث فوراً
    private val ver = MutableStateFlow(0)
    val changes: StateFlow<Int> = ver.asStateFlow()

    private fun bump() {
        ver.value = ver.value + 1
    }

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("hubsync_privacy", Context.MODE_PRIVATE)
        prefs = p
        privateUntil = p.getLong("private_until", 0L)
    }

    // ---------- الوضع الخاص ----------

    fun isPrivateMode(): Boolean = System.currentTimeMillis() < privateUntil

    fun privateRemainingMs(): Long = maxOf(0L, privateUntil - System.currentTimeMillis())

    fun startPrivate(minutes: Int) {
        privateUntil = System.currentTimeMillis() + minutes * 60_000L
        prefs?.edit()?.putLong("private_until", privateUntil)?.apply()
        bump()
    }

    fun stopPrivate() {
        privateUntil = 0L
        prefs?.edit()?.putLong("private_until", 0L)?.apply()
        bump()
    }

    // ---------- التطبيقات المستثناة / المسموحة ----------

    fun mode(): String = prefs?.getString("mode", MODE_ALL) ?: MODE_ALL

    fun setMode(m: String) {
        prefs?.edit()?.putString("mode", m)?.apply()
        bump()
    }

    fun blockedApps(): Set<String> =
        HashSet<String>(prefs?.getStringSet("blocked", emptySet()) ?: emptySet())

    fun allowedApps(): Set<String> =
        HashSet<String>(prefs?.getStringSet("allowed", emptySet()) ?: emptySet())

    fun setBlockedApps(s: Set<String>) {
        prefs?.edit()?.putStringSet("blocked", HashSet<String>(s))?.apply()
        bump()
    }

    fun setAllowedApps(s: Set<String>) {
        prefs?.edit()?.putStringSet("allowed", HashSet<String>(s))?.apply()
        bump()
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

    // ---------- المصدر المجهول ----------

    // تطبيقات بتمنع الإتاحة (زي البنوك) ما بنقدرش نعرف اسمها. لو في قواعد خصوصية، نتجاهل نسخها
    fun blockUnknown(): Boolean = prefs?.getBoolean("block_unknown", true) ?: true

    fun setBlockUnknown(v: Boolean) {
        prefs?.edit()?.putBoolean("block_unknown", v)?.apply()
        bump()
    }

    // هل فيه قواعد خصوصية أصلاً؟
    fun hasRules(): Boolean = mode() == MODE_ALLOW_ONLY || blockedApps().isNotEmpty()

    // ---------- المسح التلقائي ----------

    // 0 = مقفول
    fun autoWipeDays(): Int = prefs?.getInt("auto_wipe_days", 0) ?: 0

    fun setAutoWipeDays(days: Int) {
        prefs?.edit()?.putInt("auto_wipe_days", days)?.apply()
        bump()
    }

    // ---------- القرار ----------

    // هل مسموح نلتقط نسخ جاي من التطبيق ده؟ (pkg = null معناها مصدر مجهول)
    fun allowsCapture(pkg: String?): Boolean {
        if (prefs == null) return true
        if (isPrivateMode()) return false
        if (mode() == MODE_ALLOW_ONLY) {
            return pkg != null && pkg in allowedApps()
        }
        if (pkg == null) {
            return !(blockUnknown() && blockedApps().isNotEmpty())
        }
        return pkg !in blockedApps()
    }
}
