// Built with BFB.AI
package com.bfbai.superhub.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class SuperHubTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile.state = Tile.STATE_INACTIVE
        qsTile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, OverlayService::class.java)
        if (qsTile.state == Tile.STATE_INACTIVE) {
            startService(intent)
            qsTile.state = Tile.STATE_ACTIVE
        } else {
            stopService(intent)
            qsTile.state = Tile.STATE_INACTIVE
        }
        qsTile.updateTile()
    }
}
