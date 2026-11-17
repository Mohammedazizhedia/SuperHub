// Built with BFB.AI
package com.bfbai.superhub

import android.graphics.Color

object AppConfig {
    // 🎨 الألوان (زجاجي)
    val SHELF_BG_COLOR: Int = Color.parseColor("#990B1B3A") 
    val SHELF_STROKE_COLOR: Int = Color.parseColor("#804DE1E1")
    val CARD_BG_COLOR: Int = Color.parseColor("#B31E3A6D")
    val CARD_WHATSAPP_COLOR: Int = Color.parseColor("#B31B4D3E")
    val CARD_CHROME_COLOR: Int = Color.parseColor("#B34A2C6B")
    val CARD_TEXT_COLOR: Int = Color.WHITE
    val CARD_SUBTEXT_COLOR: Int = Color.parseColor("#CCFFFFFF")
    val CLOSE_BTN_COLOR: Int = Color.parseColor("#FF5A5F")

    // 📏 الحجم (كبرنا المقفول شوية عشان يبقى سهل المسك)
    val SHELF_CLOSED_WIDTH_DP: Float = 14f 
    val SHELF_CLOSED_HEIGHT_DP: Float = 80f 
    val SHELF_EXPANDED_WIDTH_DP: Float = 260f // كبرنا العرض شوية عشان الكروت تبقى أعرض
    val SHELF_EXPANDED_HEIGHT_DP: Float = 320f
    val SHELF_CORNER_RADIUS_DP: Float = 16f

    // ⏱️ الوقت
    val AUTO_CLOSE_DELAY_MS: Long = 5000
    val DOUBLE_TAP_TIMEOUT_MS: Long = 300

    // 🗂️ الهيستوري
    val MAX_HISTORY_ITEMS: Int = 0 
}
