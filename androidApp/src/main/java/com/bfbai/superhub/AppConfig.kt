// Built with BFB.AI
package com.bfbai.superhub

import android.graphics.Color

object AppConfig {
    // ألوان الرف والكروت
    val SHELF_BG_COLOR: Int = Color.parseColor("#990B1B3A")
    val SHELF_STROKE_COLOR: Int = Color.parseColor("#804DE1E1")
    val CARD_BG_COLOR: Int = Color.parseColor("#B31E3A6D")
    val CARD_TEXT_COLOR: Int = Color.WHITE
    val CARD_SUBTEXT_COLOR: Int = Color.parseColor("#CCFFFFFF")

    // ألوان القائمة
    val MENU_BG_COLOR: Int = Color.parseColor("#D90B1B3A")
    val MENU_TEXT_COLOR: Int = Color.WHITE
    val MENU_DIVIDER_COLOR: Int = Color.parseColor("#4DE1E1")

    // ألوان زراير الرف
    val ACTION_ICON_COLOR: Int = Color.parseColor("#4DE1E1")
    val PIN_ACTIVE_COLOR: Int = Color.parseColor("#FFD700")

    // الحجم
    val SHELF_CLOSED_WIDTH_DP: Float = 24f
    val SHELF_CLOSED_HEIGHT_DP: Float = 96f
    val SHELF_EXPANDED_WIDTH_DP: Float = 260f
    val SHELF_EXPANDED_HEIGHT_DP: Float = 320f
    val SHELF_CORNER_RADIUS_DP: Float = 16f

    // الوقت
    val AUTO_CLOSE_DELAY_MS: Long = 20_000L
    val DOUBLE_TAP_TIMEOUT_MS: Long = 300

    // أقصى عدد عناصر في الرف
    val MAX_HISTORY_ITEMS: Int = 30
}
