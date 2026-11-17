// Built with BFB.AI
package com.bfbai.superhub.service

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.TextUtils

class SuperHubTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        
        if (isAccessibilityServiceEnabled()) {
            val intent = Intent(this, ClipboardAccessibilityService::class.java)
            intent.action = "TOGGLE_SHELF"
            startService(intent)
            
            // بنغير لون الزرار هنا (Active / Inactive)
            if (qsTile?.state == Tile.STATE_ACTIVE) {
                qsTile?.state = Tile.STATE_INACTIVE
            } else {
                qsTile?.state = Tile.STATE_ACTIVE
            }
            qsTile?.updateTile()
        } else {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(intent)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        var accessibilityEnabled = 0
        val service = packageName + "/" + ClipboardAccessibilityService::class.java.canonicalName
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
            val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')
            if (accessibilityEnabled == 1) {
                val settingValue = Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                if (settingValue != null) {
                    mStringColonSplitter.setString(settingValue)
                    while (mStringColonSplitter.hasNext()) {
                        val accessibilityService = mStringColonSplitter.next()
                        if (accessibilityService.equals(service, ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
        }
        return false
    }

    private fun updateTileState() {
        val tile: Tile = qsTile ?: return
        // لما التطبيق يشتغل لأول مرة، بنخلي لونه شغال (أزرق)
        if (isAccessibilityServiceEnabled()) {
            tile.state = Tile.STATE_ACTIVE
        } else {
            tile.state = Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
