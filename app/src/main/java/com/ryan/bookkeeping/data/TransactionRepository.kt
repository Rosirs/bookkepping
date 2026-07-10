package com.ryan.bookkeeping.data

import android.database.sqlite.SQLiteConstraintException
import com.ryan.bookkeeping.domain.ParsedTransaction
import com.ryan.bookkeeping.domain.TransactionDirection
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val dao: TransactionDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun observeTransactions(): Flow<List<TransactionEntity>> = dao.observeTransactions()

    suspend fun recordParsed(parsed: ParsedTransaction): Boolean {
        val insertedId = dao.insert(parsed.toEntity(createdAt = clock()))
        return insertedId != -1L
    }

    suspend fun updateTransaction(entity: TransactionEntity): Boolean {
        return try {
            dao.update(entity)
            true
        } catch (_: SQLiteConstraintException) {
            false
        }
    }

    suspend fun deleteTransaction(id: Long) {
        dao.deleteById(id)
    }

    private fun ParsedTransaction.toEntity(createdAt: Long): TransactionEntity = TransactionEntity(
        amountCents = amountCents,
        direction = direction,
        sourcePackage = sourcePackage,
        sourceApp = sourceApp,
        title = title,
        content = content,
        merchant = merchant,
        remark = remark,
        postedAt = postedAt,
        createdAt = createdAt,
        dedupeKey = dedupeKey,
    )
}

data class TransactionSummary(
    val incomeCents: Long,
    val expenseCents: Long,
    val netCents: Long,
    val count: Int,
)

fun calculateSummary(transactions: List<TransactionEntity>): TransactionSummary {
    val income = transactions
        .filter { it.direction == TransactionDirection.INCOME }
        .sumOf { it.amountCents }
    val expense = transactions
        .filter { it.direction == TransactionDirection.EXPENSE }
        .sumOf { it.amountCents }
    return TransactionSummary(
        incomeCents = income,
        expenseCents = expense,
        netCents = income - expense,
        count = transactions.size,
    )
}
