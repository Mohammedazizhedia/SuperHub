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
        live = null
        super.onStopListening()
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
        setTile(ClipboardAccessibilityService.instance?.shelfShown == true)
    }

    private fun setTile(on: Boolean) {
        qsTile?.apply {
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
