// Built with BFB.AI
package com.bfbai.superhub.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** سجل تشخيص صغير في الذاكرة (آخر 300 سطر) بنعرضه ونسخه من قايمة الرف */
object DiagLog {
    private val buf = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun d(tag: String, msg: String) {
        buf.addLast(fmt.format(Date()) + " [" + tag + "] " + msg)
        while (buf.size > 300) buf.removeFirst()
        Log.d("HubSync", "[$tag] $msg")
    }

    @Synchronized
    fun dump(n: Int): String = buf.toList().takeLast(n).joinToString("\n")

    @Synchronized
    fun clear() {
        buf.clear()
    }
}

/** أيقونات التطبيقات المصدر (للكروت)، بتتحمل في الخلفية وبتتخزن */
object SourceIcons {
    private val cache = LruCache<String, Bitmap>(64)
    private val exec = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun load(ctx: Context, pkg: String, px: Int, cb: (Bitmap?) -> Unit) {
        val hit = cache.get(pkg)
        if (hit != null) {
            cb(hit)
            return
        }
        val app = ctx.applicationContext
        exec.execute {
            val bmp = try {
                val d = app.packageManager.getApplicationIcon(pkg)
                val s = maxOf(px, 24)
                val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                d.setBounds(0, 0, s, s)
                d.draw(c)
                b
            } catch (t: Throwable) {
                null
            }
            if (bmp != null) cache.put(pkg, bmp)
            main.post { cb(bmp) }
        }
    }
}
