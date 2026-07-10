package com.ryan.bookkeeping.domain

data class ParsedTransaction(
    val amountCents: Long,
    val direction: TransactionDirection,
    val sourcePackage: String,
    val sourceApp: String,
    val title: String,
    val content: String,
    val merchant: String,
    val remark: String,
    val postedAt: Long,
    val dedupeKey: String,
)
