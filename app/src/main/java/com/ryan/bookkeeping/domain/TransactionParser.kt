package com.ryan.bookkeeping.domain

import java.security.MessageDigest
import java.util.Locale

class TransactionParser {
    private val amountPatterns = listOf(
        Regex("(?:人民币|RMB|CNY)?\\s*[¥￥]\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE),
        Regex("(?:人民币|RMB|CNY)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元", RegexOption.IGNORE_CASE),
        Regex("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元"),
    )

    private val strongIncomeWords = listOf("退款", "返现", "到账", "收入", "收款", "转入")
    private val expenseWords = listOf("支付", "付款", "消费", "扣款", "支出", "转出")

    fun parse(raw: RawNotification): ParsedTransaction? {
        if (!NotificationPolicy.shouldInspectPackage(raw.sourcePackage)) return null

        val safeTitle = NotificationPolicy.sanitizeForStorage(raw.title)
        val safeContent = NotificationPolicy.sanitizeForStorage(raw.content)
        val combined = listOf(safeTitle, safeContent).joinToString(" ").trim()
        val amount = extractAmountCents(combined) ?: return null
        val direction = detectDirection(combined) ?: return null
        val merchant = NotificationPolicy.sanitizeForStorage(extractMerchant(combined, direction))
        val remark = buildRemark(raw.sourceApp, safeTitle, safeContent)
        val dedupeKey = buildDedupeKey(raw.copy(title = safeTitle, content = safeContent), amount, direction)

        return ParsedTransaction(
            amountCents = amount,
            direction = direction,
            sourcePackage = raw.sourcePackage,
            sourceApp = raw.sourceApp,
            title = safeTitle,
            content = safeContent,
            merchant = merchant,
            remark = remark,
            postedAt = raw.postedAt,
            dedupeKey = dedupeKey,
        )
    }

    fun buildDedupeKey(
        raw: RawNotification,
        amountCents: Long,
        direction: TransactionDirection,
    ): String {
        val minuteBucket = raw.postedAt / 60_000L
        val normalizedText = normalizeText("${raw.title} ${raw.content}")
        val base = listOf(
            raw.sourcePackage.lowercase(Locale.ROOT),
            amountCents.toString(),
            direction.name,
            minuteBucket.toString(),
            normalizedText,
        ).joinToString("|")
        return sha256(base)
    }

    private fun extractAmountCents(text: String): Long? {
        for (pattern in amountPatterns) {
            val match = pattern.find(text) ?: continue
            val amountText = match.groupValues[1]
            return MoneyFormatter.yuanTextToCents(amountText)
        }
        return null
    }

    private fun detectDirection(text: String): TransactionDirection? {
        if (strongIncomeWords.any { text.contains(it) }) {
            return TransactionDirection.INCOME
        }
        if (expenseWords.any { text.contains(it) }) {
            return TransactionDirection.EXPENSE
        }
        return null
    }

    private fun extractMerchant(text: String, direction: TransactionDirection): String {
        val patterns = when (direction) {
            TransactionDirection.EXPENSE -> listOf(
                Regex("向([^，。,.\\n\\s]{2,20}?)(?:支付|付款|消费)"),
                Regex("在([^，。,.\\n\\s]{2,20}?)(?:消费|支付)"),
            )
            TransactionDirection.INCOME -> listOf(
                Regex("来自([^，。,.\\n\\s]{1,20})"),
                Regex("收到([^，。,.\\n\\s]{1,20})"),
            )
        }
        for (pattern in patterns) {
            val value = pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!value.isNullOrBlank()) return value
        }
        return ""
    }

    private fun buildRemark(sourceApp: String, title: String, content: String): String {
        val excerpt = normalizeWhitespace(listOf(title, content).joinToString(" "))
            .take(MAX_REMARK_EXCERPT)
        return if (excerpt.isBlank()) sourceApp else "$sourceApp：$excerpt"
    }

    private fun normalizeText(text: String): String = normalizeWhitespace(text)
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), "")

    private fun normalizeWhitespace(text: String): String = text
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val MAX_REMARK_EXCERPT = 80
    }
}
