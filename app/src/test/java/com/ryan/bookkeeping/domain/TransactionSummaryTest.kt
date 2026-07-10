package com.ryan.bookkeeping.domain

import com.ryan.bookkeeping.data.TransactionEntity
import com.ryan.bookkeeping.data.calculateSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionSummaryTest {
    @Test
    fun calculatesIncomeExpenseAndNetTotals() {
        val summary = calculateSummary(
            listOf(
                transaction(id = 1, amountCents = 10_000, direction = TransactionDirection.INCOME),
                transaction(id = 2, amountCents = 2_500, direction = TransactionDirection.EXPENSE),
                transaction(id = 3, amountCents = 1_200, direction = TransactionDirection.EXPENSE),
            ),
        )

        assertEquals(10_000L, summary.incomeCents)
        assertEquals(3_700L, summary.expenseCents)
        assertEquals(6_300L, summary.netCents)
        assertEquals(3, summary.count)
    }

    @Test
    fun dedupeKeyIsStableWithinSameMinute() {
        val parser = TransactionParser()
        val raw = RawNotification(
            sourcePackage = "com.tencent.mm",
            sourceApp = "微信",
            title = "微信支付",
            content = "向便利店支付 ¥9.90",
            postedAt = 1_700_000_020_001L,
        )

        val first = requireNotNull(parser.parse(raw))
        val second = requireNotNull(parser.parse(raw.copy(postedAt = 1_700_000_030_001L)))

        assertEquals(first.dedupeKey, second.dedupeKey)
    }

    @Test
    fun dedupeKeyChangesAcrossMinuteBucket() {
        val parser = TransactionParser()
        val raw = RawNotification(
            sourcePackage = "com.tencent.mm",
            sourceApp = "微信",
            title = "微信支付",
            content = "向便利店支付 ¥9.90",
            postedAt = 1_700_000_000_001L,
        )

        val first = requireNotNull(parser.parse(raw))
        val second = requireNotNull(parser.parse(raw.copy(postedAt = 1_700_000_060_001L)))

        assert(first.dedupeKey != second.dedupeKey)
    }

    private fun transaction(
        id: Long,
        amountCents: Long,
        direction: TransactionDirection,
    ) = TransactionEntity(
        id = id,
        amountCents = amountCents,
        direction = direction,
        sourcePackage = "source.package",
        sourceApp = "来源",
        title = "title",
        content = "content",
        merchant = "merchant",
        remark = "remark",
        postedAt = 1_700_000_000_000L,
        createdAt = 1_700_000_000_000L,
        dedupeKey = "dedupe-$id",
    )
}
