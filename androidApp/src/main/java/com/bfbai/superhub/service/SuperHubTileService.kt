// Built with BFB.AI
package com.bfbai.superhub.service

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class SuperHubTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        setTile(ClipboardAccessibilityService.instance?.shelfShown == true)
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

    private fun setTile(on: Boolean) {
        qsTile?.apply {
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
