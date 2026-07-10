package com.ryan.bookkeeping.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {
    @Test
    fun allowsKnownPaymentAndBankPackages() {
        assertTrue(NotificationPolicy.shouldInspectPackage("com.eg.android.AlipayGphone"))
        assertTrue(NotificationPolicy.shouldInspectPackage("com.tencent.mm"))
        assertTrue(NotificationPolicy.shouldInspectPackage("com.icbc"))
        assertTrue(NotificationPolicy.shouldInspectPackage("com.cmbchina.ccd.pluto.cmbActivity"))
    }

    @Test
    fun rejectsUnrelatedPackagesBeforeParsing() {
        assertFalse(NotificationPolicy.shouldInspectPackage("com.example.chat"))
        assertFalse(NotificationPolicy.shouldInspectPackage("com.random.news"))
    }

    @Test
    fun sanitizesLongNotificationTextAndSensitiveCodes() {
        val sensitive = "付款成功，银行卡尾号1234，验证码 987654，https://pay.example.com/very/long/link?token=abcdef" +
            "x".repeat(200)

        val sanitized = NotificationPolicy.sanitizeForStorage(sensitive)

        assertFalse(sanitized.contains("987654"))
        assertFalse(sanitized.contains("https://pay.example.com"))
        assertTrue(sanitized.length <= 120)
        assertTrue(sanitized.contains("验证码 ******"))
        assertTrue(sanitized.contains("[链接]"))
    }

    @Test
    fun parserReturnsNullForUntrustedPackage() {
        val parser = TransactionParser()

        val result = parser.parse(
            RawNotification(
                sourcePackage = "com.example.chat",
                sourceApp = "聊天",
                title = "支付提醒",
                content = "向便利店支付 ¥12.34",
                postedAt = 1_700_000_000_000L,
            ),
        )

        assertNull(result)
    }

    @Test
    fun parserStoresSanitizedContentAndRemark() {
        val parser = TransactionParser()
        val result = parser.parse(
            RawNotification(
                sourcePackage = "com.tencent.mm",
                sourceApp = "微信",
                title = "微信支付",
                content = "向便利店支付 ¥12.34，验证码 123456，https://pay.example.com/token/secret" + "很长".repeat(100),
                postedAt = 1_700_000_000_000L,
            ),
        )

        requireNotNull(result)
        assertEquals("微信支付", result.title)
        assertFalse(result.content.contains("123456"))
        assertFalse(result.remark.contains("https://pay.example.com"))
        assertTrue(result.content.length <= 120)
        assertTrue(result.remark.length <= 96)
    }
}
