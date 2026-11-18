// Built with BFB.AI
package com.bfbai.superhub.service

import android.content.Context
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
            
            // تغيير اللون فوراً
            val prefs = getSharedPreferences("SuperHubPrefs", Context.MODE_PRIVATE)
            val isVisible = prefs.getBoolean("shelf_visible", true)
            if (isVisible) {
                qsTile?.state = Tile.STATE_ACTIVE
            } else {
                qsTile?.state = Tile.STATE_INACTIVE
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
        if (isAccessibilityServiceEnabled()) {
            // بنقرا الحالة من الـ SharedPreferences
            val prefs = getSharedPreferences("SuperHubPrefs", Context.MODE_PRIVATE)
            val isVisible = prefs.getBoolean("shelf_visible", true)
            
            if (isVisible) {
                tile.state = Tile.STATE_ACTIVE
            } else {
                tile.state = Tile.STATE_INACTIVE
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
