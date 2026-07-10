package com.ryan.bookkeeping.domain

import java.math.BigDecimal
import java.math.RoundingMode

object MoneyFormatter {
    fun format(amountCents: Long): String {
        val sign = if (amountCents < 0) "-" else ""
        val abs = kotlin.math.abs(amountCents)
        return "$sign¥${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    fun yuanTextToCents(text: String): Long? = runCatching {
        BigDecimal(text.replace(",", ""))
            .setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact()
    }.getOrNull()
}
