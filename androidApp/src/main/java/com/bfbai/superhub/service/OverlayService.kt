// Built with BFB.AI
package com.bfbai.superhub.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class OverlayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // تم إيقاف هذه الخدمة، الرف الجديد (ShelfController) هو اللي شغال
        return START_NOT_STICKY
    }
}
