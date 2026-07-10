package com.ryan.bookkeeping.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ryan.bookkeeping.data.TransactionEntity
import com.ryan.bookkeeping.domain.MoneyFormatter
import com.ryan.bookkeeping.domain.TransactionDirection
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BookKeepingApp(viewModel: BookkeepingViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF7F8FA)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "自动记账",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                PermissionCard()
                Spacer(modifier = Modifier.height(12.dp))
                SummaryCard(state = uiState)
                Spacer(modifier = Modifier.height(12.dp))
                if (uiState.transactions.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.transactions, key = { it.id }) { transaction ->
                            TransactionRow(
                                transaction = transaction,
                                onEdit = { editing = transaction },
                                onDelete = { viewModel.deleteTransaction(transaction.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { transaction ->
        EditTransactionDialog(
            transaction = transaction,
            onDismiss = { editing = null },
            onSave = { amountCents, direction, merchant, remark ->
                viewModel.updateTransaction(transaction, amountCents, direction, merchant, remark)
                editing = null
            },
        )
    }
}

@Composable
private fun PermissionCard() {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("开启通知读取", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("用于识别支付宝、微信、银行等通知中的收支信息。数据只保存在本机。")
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) {
                Text("去开启权限")
            }
        }
    }
}

@Composable
private fun SummaryCard(state: BookkeepingUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("账单概览", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryItem("收入", MoneyFormatter.format(state.summary.incomeCents), Color(0xFF2E7D32))
                SummaryItem("支出", MoneyFormatter.format(state.summary.expenseCents), Color(0xFFC62828))
                SummaryItem("结余", MoneyFormatter.format(state.summary.netCents), Color(0xFF1565C0))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("共 ${state.summary.count} 条记录", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyState() {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text("暂无账单", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("开启通知读取权限后，收到支持的支付或银行通知时会自动记录。")
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: TransactionEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(transaction.merchant.ifBlank { transaction.sourceApp }, fontWeight = FontWeight.Bold)
                    Text(
                        transaction.remark,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val signedAmount = if (transaction.direction == TransactionDirection.EXPENSE) {
                    -transaction.amountCents
                } else {
                    transaction.amountCents
                }
                Text(
                    MoneyFormatter.format(signedAmount),
                    color = if (transaction.direction == TransactionDirection.EXPENSE) Color(0xFFC62828) else Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(formatTime(transaction.postedAt), style = MaterialTheme.typography.bodySmall)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

@Composable
private fun EditTransactionDialog(
    transaction: TransactionEntity,
    onDismiss: () -> Unit,
    onSave: (Long, TransactionDirection, String, String) -> Unit,
) {
    var amountText by remember { mutableStateOf(centsToYuan(transaction.amountCents)) }
    var direction by remember { mutableStateOf(transaction.direction) }
    var merchant by remember { mutableStateOf(transaction.merchant) }
    var remark by remember { mutableStateOf(transaction.remark) }
    val amountCents = parseAmountCents(amountText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑账单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("金额") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { direction = TransactionDirection.EXPENSE }) { Text("支出") }
                    Button(onClick = { direction = TransactionDirection.INCOME }) { Text("收入") }
                }
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("商户/对方") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("备注") },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = amountCents != null && amountCents > 0,
                onClick = { onSave(requireNotNull(amountCents), direction, merchant, remark) },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun parseAmountCents(text: String): Long? = runCatching {
    BigDecimal(text.trim())
        .setScale(2, RoundingMode.HALF_UP)
        .movePointRight(2)
        .longValueExact()
}.getOrNull()

private fun centsToYuan(cents: Long): String {
    return BigDecimal(cents).movePointLeft(2).setScale(2).toPlainString()
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}
