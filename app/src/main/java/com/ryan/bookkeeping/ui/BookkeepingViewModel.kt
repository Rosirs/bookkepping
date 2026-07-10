package com.ryan.bookkeeping.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.bookkeeping.data.TransactionEntity
import com.ryan.bookkeeping.data.TransactionRepository
import com.ryan.bookkeeping.data.TransactionSummary
import com.ryan.bookkeeping.data.calculateSummary
import com.ryan.bookkeeping.domain.TransactionDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BookkeepingViewModel(
    private val repository: TransactionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookkeepingUiState())
    val uiState: StateFlow<BookkeepingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeTransactions().collect { transactions ->
                _uiState.value = _uiState.value.copy(
                    transactions = transactions,
                    summary = calculateSummary(transactions),
                )
            }
        }
    }

    fun updateTransaction(
        original: TransactionEntity,
        amountCents: Long,
        direction: TransactionDirection,
        merchant: String,
        remark: String,
    ) {
        if (amountCents <= 0) return
        viewModelScope.launch {
            repository.updateTransaction(
                original.copy(
                    amountCents = amountCents,
                    direction = direction,
                    merchant = merchant.trim(),
                    remark = remark.trim(),
                ),
            )
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
        }
    }
}

data class BookkeepingUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val summary: TransactionSummary = TransactionSummary(
        incomeCents = 0,
        expenseCents = 0,
        netCents = 0,
        count = 0,
    ),
)
