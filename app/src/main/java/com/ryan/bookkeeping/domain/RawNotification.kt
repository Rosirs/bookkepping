package com.ryan.bookkeeping.domain

data class RawNotification(
    val sourcePackage: String,
    val sourceApp: String,
    val title: String,
    val content: String,
    val postedAt: Long,
)
