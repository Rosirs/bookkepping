package com.ryan.bookkeeping.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ryan.bookkeeping.domain.TransactionDirection

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["dedupeKey"], unique = true)],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountCents: Long,
    val direction: TransactionDirection,
    val sourcePackage: String,
    val sourceApp: String,
    val title: String,
    val content: String,
    val merchant: String,
    val remark: String,
    val postedAt: Long,
    val createdAt: Long,
    val dedupeKey: String,
)
