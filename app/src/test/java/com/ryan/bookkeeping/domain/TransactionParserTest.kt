package com.ryan.bookkeeping.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionParserTest {
    private val parser = TransactionParser()

    @Test
    fun parsesExpenseNotificationWithYuanSymbol() {
        val result = parser.parse(
            RawNotification(
                sourcePackage = "com.eg.android.AlipayGphone",
                sourceApp = "支付宝",
                title = "支付宝付款",
                content = "你向便利店支付￥12.34",
                postedAt = 1_700_000_000_000L,
            ),
        )

        requireNotNull(result)
        assertEquals(1234L, result.amountCents)
        assertEquals(TransactionDirection.EXPENSE, result.direction)
        assertEquals("便利店", result.merchant)
        assertTrue(result.remark.contains("支付宝"))
        assertTrue(result.remark.contains("支付"))
    }

    @Test
    fun parsesIncomeNotificationWithAmountAfterYuan() {
        val result = parser.parse(
            RawNotification(
                sourcePackage = "com.tencent.mm",
                sourceApp = "微信",
                title = "微信收款到账",
                content = "二维码收款到账 88元，来自张三",
                postedAt = 1_700_000_060_000L,
            ),
        )

        requireNotNull(result)
        assertEquals(8_800L, result.amountCents)
        assertEquals(TransactionDirection.INCOME, result.direction)
        assertEquals("张三", result.merchant)
    }

    @Test
    fun parsesRenminbiAmountFormat() {
        val result = parser.parse(
            RawNotification(
                sourcePackage = "com.icbc",
                sourceApp = "银行",
                title = "账户变动",
                content = "您的储蓄卡收入人民币1,234.50元，余额充足",
                postedAt = 1_700_000_120_000L,
            ),
        )

        requireNotNull(result)
        assertEquals(123_450L, result.amountCents)
        assertEquals(TransactionDirection.INCOME, result.direction)
    }

    @Test
    fun refundWordingWinsOverExpenseWords() {
        val result = parser.parse(
            RawNotification(
                sourcePackage = "com.eg.android.AlipayGphone",
                sourceApp = "电商",
                title = "退款成功",
                content = "您之前支付的订单已退款 ¥39.90",
                postedAt = 1_700_000_180_000L,
            ),
        )

        requireNotNull(result)
        assertEquals(3_990L, result.amountCents)
        assertEquals(TransactionDirection.INCOME, result.direction)
    }

    @Test
    fun ignoresNotificationsWithoutMoneyMovement() {
        val result = parser.parse(
            RawNotification(
                sourcePackage = "chat.example",
                sourceApp = "聊天",
                title = "新消息",
                content = "晚上一起吃饭吗？",
                postedAt = 1_700_000_240_000L,
            ),
        )

        assertNull(result)
    }

    @Test
    fun formatsSignedMoneyForDisplay() {
        assertEquals("¥12.34", MoneyFormatter.format(1234L))
        assertEquals("-¥12.34", MoneyFormatter.format(-1234L))
        assertEquals("¥0.05", MoneyFormatter.format(5L))
    }
}
