// Built with BFB.AI
package com.bfbai.superhub.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.bfbai.superhub.utils.PrivacyPolicy

/** زرار في لوحة الإعدادات السريعة: وضع خاص بيوقف الالتقاط 30 دقيقة */
class PrivateModeTileService : TileService() {

    override fun onCreate() {
        super.onCreate()
        PrivacyPolicy.init(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        PrivacyPolicy.init(this)
        refresh()
    }

    override fun onClick() {
        super.onClick()
        PrivacyPolicy.init(this)
        if (PrivacyPolicy.isPrivateMode()) PrivacyPolicy.stopPrivate() else PrivacyPolicy.startPrivate(30)
        refresh()
    }

    private fun refresh() {
        val t = qsTile ?: return
        val on = PrivacyPolicy.isPrivateMode()
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = if (on) "وضع خاص: شغال" else "وضع خاص"
        t.updateTile()
    }
}
