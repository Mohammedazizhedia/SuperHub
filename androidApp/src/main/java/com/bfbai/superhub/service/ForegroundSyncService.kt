// Built with BFB.AI
package com.bfbai.superhub.service
import android.app.*
import android.content.Intent
import android.os.IBinder
class ForegroundSyncService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
}
