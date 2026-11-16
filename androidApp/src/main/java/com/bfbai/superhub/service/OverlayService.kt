// Built with BFB.AI
package com.bfbai.superhub.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

// الستارة القديمة اتلغت، والرف الذكي هو اللي شغال دلوقتي
class OverlayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
