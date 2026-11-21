// Built with BFB.AI
package com.bfbai.superhub.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/** شفافة: بتطلب صلاحية قراءة الصور (عشان نقبل الصور المسحوبة من المعرض) وبتقفل */
class MediaPermissionActivity : Activity() {

    private fun perm(): String =
        if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(perm()) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(
                this,
                "الصلاحية شغالة. لو الصورة لسه مش بتتقبل، اختار \"السماح بكل الصور\" من إعدادات التطبيق",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }
        Toast.makeText(
            this,
            "HubSync محتاج صلاحية الصور عشان يقبل الصور المسحوبة. اختار السماح",
            Toast.LENGTH_LONG
        ).show()
        requestPermissions(arrayOf(perm()), 77)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (ok) {
            Toast.makeText(this, "تمام. اسحب الصورة تاني", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(
                this,
                "الصلاحية مرفوضة. فعّلها من إعدادات التطبيق ثم الأذونات ثم الصور",
                Toast.LENGTH_LONG
            ).show()
            try {
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                i.data = Uri.parse("package:$packageName")
                startActivity(i)
            } catch (e: Exception) {
            }
        }
        finish()
    }
}
