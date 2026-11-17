// Built with BFB.AI
package com.bfbai.superhub

import android.graphics.Color

object AppConfig {
    // ==========================================
    // 🎨 إعدادات الألوان (الثيم والكروت)
    // ==========================================
    val SHELF_BG_COLOR: Int = Color.parseColor("#F20B1B3A")
    val SHELF_STROKE_COLOR: Int = Color.parseColor("#4DE1E1")
    val CARD_BG_COLOR: Int = Color.parseColor("#1E3A6D")
    val CARD_WHATSAPP_COLOR: Int = Color.parseColor("#1B4D3E")
    val CARD_CHROME_COLOR: Int = Color.parseColor("#4A2C6B")
    val CARD_TEXT_COLOR: Int = Color.WHITE
    val CARD_SUBTEXT_COLOR: Int = Color.parseColor("#99FFFFFF")
    val CLOSE_BTN_COLOR: Int = Color.parseColor("#FF5A5F")

    // ==========================================
    // 📏 إعدادات الحجم والشكل
    // ==========================================
    val SHELF_CLOSED_WIDTH_DP: Float = 12f
    val SHELF_CLOSED_HEIGHT_DP: Float = 80f
    val SHELF_EXPANDED_WIDTH_DP: Float = 260f
    val SHELF_EXPANDED_HEIGHT_DP: Float = 350f
    val SHELF_CORNER_RADIUS_DP: Float = 20f

    // ==========================================
    // ⏱️ إعدادات الوقت
    // ==========================================
    val AUTO_CLOSE_DELAY_MS: Long = 5000
    val DOUBLE_TAP_TIMEOUT_MS: Long = 300

    // ==========================================
    // 🗂️ إعدادات الهيستوري
    // ==========================================
    val MAX_HISTORY_ITEMS: Int = 0 
}
