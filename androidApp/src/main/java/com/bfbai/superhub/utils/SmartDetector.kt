// Built with BFB.AI
package com.bfbai.superhub.utils

enum class ItemKind { OTP, LINK, PHONE, EMAIL, TEXT }

/**
 * kind  = نوع النص
 * value = الجزء القابل للتنفيذ: الكود نفسه، الرابط (بـ https://)، الرقم، أو الإيميل
 */
data class Detection(val kind: ItemKind, val value: String)

object SmartDetector {

    private val otpOnly = Regex("^\\d{4,8}$")
    private val otpInside = Regex("(?<!\\d)\\d{4,8}(?!\\d)")
    private val linkRe = Regex("^(https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE)
    private val emailRe = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val phoneRe = Regex("^\\+?[0-9\\s\\-().]{9,20}$")

    private val otpWords = listOf(
        "otp", "code", "verification", "verify", "passcode", "pin",
        "رمز", "كود", "تحقق", "التحقق"
    )

    // يحوّل الأرقام العربية والفارسية لأرقام إنجليزية
    private fun normalizeDigits(s: String): String = buildString {
        for (ch in s) {
            when (ch) {
                in '٠'..'٩' -> append('0' + (ch - '٠'))
                in '۰'..'۹' -> append('0' + (ch - '۰'))
                else -> append(ch)
            }
        }
    }

    fun detect(raw: String): Detection {
        val text = normalizeDigits(raw.trim())
        if (text.isEmpty()) return Detection(ItemKind.TEXT, raw)

        // 1) رقم من 4 لـ 8 خانات لوحده
        if (otpOnly.matches(text)) return Detection(ItemKind.OTP, text)

        // 2) رسالة قصيرة فيها كلمة زي "كود" أو "OTP" ورقم من 4 لـ 8 خانات
        if (text.length <= 300) {
            val lower = text.lowercase()
            if (otpWords.any { lower.contains(it) }) {
                val m = otpInside.find(text)
                if (m != null) return Detection(ItemKind.OTP, m.value)
            }
        }

        // 3) رابط لوحده
        if (linkRe.matches(text)) {
            val url = if (text.startsWith("www.", ignoreCase = true)) "https://$text" else text
            return Detection(ItemKind.LINK, url)
        }

        // 4) إيميل لوحده
        if (emailRe.matches(text)) return Detection(ItemKind.EMAIL, text)

        // 5) رقم هاتف (9 لـ 15 رقم، ويقبل + في الأول)
        if (phoneRe.matches(text)) {
            val digits = text.filter { it.isDigit() }
            if (digits.length in 9..15) {
                val clean = if (text.startsWith("+")) "+$digits" else digits
                return Detection(ItemKind.PHONE, clean)
            }
        }

        return Detection(ItemKind.TEXT, raw)
    }
}
