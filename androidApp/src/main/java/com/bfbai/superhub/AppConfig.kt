// Built with BFB.AI
package com.bfbai.superhub

import android.graphics.Color

object AppConfig {
    // ==========================================
    // 🎨 إعدادات الألوان والشفافية (زجاجي)
    // ==========================================
    // لون خلفية الرف (شفاف عالي - زجاجي)
    val SHELF_BG_COLOR: Int = Color.parseColor("#990B1B3A") 
    // لون حدود الرف
    val SHELF_STROKE_COLOR: Int = Color.parseColor("#804DE1E1")
    
    // ألوان الكروت (شفافة عالية عشان تبقى زجاجية)
    val CARD_BG_COLOR: Int = Color.parseColor("#B31E3A6D")
    val CARD_WHATSAPP_COLOR: Int = Color.parseColor("#B31B4D3E")
    val CARD_CHROME_COLOR: Int = Color.parseColor("#B34A2C6B")
    
    // ألوان النصوص
    val CARD_TEXT_COLOR: Int = Color.WHITE
    val CARD_SUBTEXT_COLOR: Int = Color.parseColor("#CCFFFFFF")
    val CLOSE_BTN_COLOR: Int = Color.parseColor("#FF5A5F")

    // ==========================================
    // 📏 إعدادات الحجم والشكل
    // ==========================================
    // عرض وطول الرف وهو مقفول (شريط رفيع جداً ومش بارز)
    val SHELF_CLOSED_WIDTH_DP: Float = 6f
    val SHELF_CLOSED_HEIGHT_DP: Float = 50f
    
    // عرض وطول الرف وهو مفتوح (أصغر ومش واخد الشاشة كلها)
    val SHELF_EXPANDED_WIDTH_DP: Float = 220f
    val SHELF_EXPANDED_HEIGHT_DP: Float = 280f
    
    // زوايا الانحناء للرف
    val SHELF_CORNER_RADIUS_DP: Float = 16f

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
