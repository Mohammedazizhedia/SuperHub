// Built with BFB.AI
package com.bfbai.superhub.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executors

/** حفظ الصور جوه التطبيق + نسخها ومشاركتها */
object ImageStore {

    private fun dir(ctx: Context): File {
        val d = File(ctx.applicationContext.filesDir, "hubsync_images")
        d.mkdirs()
        return d
    }

    // بينسخ الصورة من Uri لملف جوه التطبيق. بيرجع المسار أو null لو فشل (مثلاً مفيش صلاحية قراءة)
    fun saveFromUri(ctx: Context, uri: Uri): String? {
        val app = ctx.applicationContext
        var out: File? = null
        try {
            val mime = app.contentResolver.getType(uri)
            val ext = when (mime) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> "jpg"
            }
            val f = File(
                dir(app),
                "img_" + System.currentTimeMillis() + "_" + (Math.random() * 10000).toInt() + "." + ext
            )
            out = f
            val input = app.contentResolver.openInputStream(uri) ?: return null
            input.use { ins -> f.outputStream().use { os -> ins.copyTo(os) } }

            // نتأكد إنه فعلاً صورة، مش بيانات تالفة
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                f.delete()
                return null
            }
            return f.absolutePath
        } catch (e: Exception) {
            try {
                out?.delete()
            } catch (ignored: Exception) {
            }
            return null
        }
    }

    fun delete(path: String) {
        try {
            File(path).delete()
        } catch (e: Exception) {
        }
    }

    fun uriFor(ctx: Context, path: String): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", File(path))

    fun copyImage(ctx: Context, path: String): Boolean {
        return try {
            HistoryRepository.ignoreClipUntil = System.currentTimeMillis() + 1500
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newUri(ctx.contentResolver, "hubsync-image", uriFor(ctx, path)))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun shareIntent(ctx: Context, path: String): Intent {
        val i = Intent(Intent.ACTION_SEND)
        i.type = "image/*"
        i.putExtra(Intent.EXTRA_STREAM, uriFor(ctx, path))
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(i, "مشاركة الصورة")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return chooser
    }

    fun decodeSampled(path: String, targetPx: Int): Bitmap? {
        return try {
            val b = BitmapFactory.Options()
            b.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, b)
            var sample = 1
            while (b.outWidth / (sample * 2) >= targetPx && b.outHeight / (sample * 2) >= targetPx) {
                sample *= 2
            }
            val o = BitmapFactory.Options()
            o.inSampleSize = sample
            BitmapFactory.decodeFile(path, o)
        } catch (t: Throwable) {
            null
        }
    }
}

/** تحميل مصغّرات الصور في الخلفية مع كاش */
object Thumbs {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 12).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val exec = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun load(path: String, targetPx: Int, cb: (Bitmap?) -> Unit) {
        val key = "$path@$targetPx"
        val hit = cache.get(key)
        if (hit != null) {
            cb(hit)
            return
        }
        exec.execute {
            val bmp = ImageStore.decodeSampled(path, targetPx)
            if (bmp != null) cache.put(key, bmp)
            main.post { cb(bmp) }
        }
    }
}
