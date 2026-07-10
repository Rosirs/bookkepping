package com.ryan.bookkeeping.data

import android.database.sqlite.SQLiteConstraintException
import com.ryan.bookkeeping.domain.TransactionDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionRepositoryTest {
    @Test
    fun updateTransactionReturnsTrueWhenDaoUpdateSucceeds() = runBlocking {
        val repository = TransactionRepository(FakeTransactionDao())

        val updated = repository.updateTransaction(transaction())

        assertTrue(updated)
    }

    @Test
    fun updateTransactionReturnsFalseWhenDedupeKeyConflicts() = runBlocking {
        val repository = TransactionRepository(FakeTransactionDao(throwOnUpdate = true))

        val updated = repository.updateTransaction(transaction())

        assertFalse(updated)
    }

    private fun transaction() = TransactionEntity(
        id = 1,
        amountCents = 1234,
        direction = TransactionDirection.EXPENSE,
        sourcePackage = "com.tencent.mm",
        sourceApp = "微信",
        title = "微信支付",
        content = "向便利店支付 ¥12.34",
        merchant = "便利店",
        remark = "微信：微信支付 向便利店支付 ¥12.34",
        postedAt = 1_700_000_000_000L,
        createdAt = 1_700_000_000_000L,
        dedupeKey = "dedupe",
    )

    private class FakeTransactionDao(
        private val throwOnUpdate: Boolean = false,
    ) : TransactionDao {
        override fun observeTransactions(): Flow<List<TransactionEntity>> = emptyFlow()

        override suspend fun insert(entity: TransactionEntity): Long = entity.id

        override suspend fun update(entity: TransactionEntity) {
            if (throwOnUpdate) throw SQLiteConstraintException("duplicate dedupeKey")
        }

        override suspend fun deleteById(id: Long) = Unit
    }
}
