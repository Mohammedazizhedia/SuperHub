// Built with BFB.AI
package com.bfbai.superhub.service

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class SuperHubTileService : TileService() {

    companion object {
        @Volatile
        var live: SuperHubTileService? = null
    }

    override fun onCreate() {
        super.onCreate()
        ShelfSettings.init(this)
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refresh()
    }

    override fun onStartListening() {
        super.onStartListening()
        live = this
        refresh()
        Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 300)
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onDestroy() {
        live = null
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val svc = ClipboardAccessibilityService.instance
        if (svc != null) {
            setTile(svc.toggleShelf())
        } else {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(intent)
        }
    }

    fun refresh() {
        try {
            setTile(ClipboardAccessibilityService.instance?.shelfShown == true)
        } catch (e: Exception) {
        }
    }

    private fun setTile(on: Boolean) {
        val t = qsTile ?: return
        val ic = IconOptions.tileIcon(this)
        if (ic != null) t.icon = ic
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.updateTile()
    }
}
