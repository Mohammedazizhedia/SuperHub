// Built with BFB.AI
package com.bfbai.superhub.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.bfbai.superhub.ui.SettingsActivity

/** تبديل أيقونة التطبيق (بالأسماء المستعارة) + اختصار بالأيقونة المختارة على الشاشة الرئيسية */
object AppIconSwitcher {

    class Opt(val key: String, val ar: String, val en: String, val res: String)

    val options: List<Opt> = listOf(
        Opt("shelf", "الرف", "Shelf", "ic_hubsync"),
        Opt("hub", "هب أزرق", "Blue hub", "ic_hub_blue"),
        Opt("aurora", "هب بنفسجي", "Aurora hub", "ic_hub_aurora"),
        Opt("clip", "حافظة", "Clipboard", "ic_hub_clip")
    )

    private fun alias(ctx: Context, key: String): ComponentName =
        ComponentName(ctx.packageName, "com.bfbai.superhub.Alias" + key.replaceFirstChar { it.uppercase() })

    fun current(ctx: Context): String {
        val pm = ctx.packageManager
        for (o in options) {
            val st = try {
                pm.getComponentEnabledSetting(alias(ctx, o.key))
            } catch (e: Exception) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            if (st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (st == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && o.key == "shelf")
            ) return o.key
        }
        return "shelf"
    }

    // بنفعّل الجديدة الأول وبعدين نعطّل الباقي، عشان ما يبقاش في لحظة من غير أيقونة
    fun apply(ctx: Context, key: String): Boolean {
        val pm = ctx.packageManager
        try {
            pm.setComponentEnabledSetting(
                alias(ctx, key),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            return false
        }
        for (o in options) {
            if (o.key == key) continue
            try {
                pm.setComponentEnabledSetting(
                    alias(ctx, o.key),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
            }
        }
        return true
    }

    // الأنشطة اللي اللانشر شايفها للتطبيق (للتشخيص)
    fun visibleLaunchers(ctx: Context): List<String> {
        return try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(ctx.packageName)
            ctx.packageManager.queryIntentActivities(i, 0).map { it.activityInfo.name.substringAfterLast('.') }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // اختصار على الشاشة الرئيسية بالأيقونة المختارة (بيشتغل فوراً من غير كاش اللانشر)
    fun pinShortcut(ctx: Context, key: String): Boolean {
        return try {
            val o = options.firstOrNull { it.key == key } ?: return false
            val id = ctx.resources.getIdentifier(o.res, "mipmap", ctx.packageName)
            val d = ctx.getDrawable(id) ?: return false
            val size = (96 * ctx.resources.displayMetrics.density).toInt()
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            d.setBounds(0, 0, size, size)
            d.draw(c)
            if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) return false
            val intent = Intent(ctx, SettingsActivity::class.java).setAction(Intent.ACTION_MAIN)
            val info = ShortcutInfoCompat.Builder(ctx, "hubsync_open_" + key)
                .setShortLabel("HubSync")
                .setIcon(IconCompat.createWithBitmap(bmp))
                .setIntent(intent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
        } catch (e: Exception) {
            false
        }
    }
}
