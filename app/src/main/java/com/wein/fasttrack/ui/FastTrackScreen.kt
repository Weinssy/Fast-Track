package com.wein.fasttrack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wein.fasttrack.data.Expense
import com.wein.fasttrack.viewmodel.ExpenseViewModel
import com.wein.fasttrack.viewmodel.FastTrackUiState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FastTrackScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        HeroDisplay(
            uiState = uiState,
            modifier = Modifier.weight(1f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        HistoryList(
            expenses = uiState.todayExpenses,
            onDelete = { viewModel.deleteExpense(it) },
            modifier = Modifier.weight(1f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        CustomKeypad(
            onDigit = { viewModel.onDigitPressed(it) },
            onBackspace = { viewModel.onBackspacePressed() },
            onSave = { viewModel.onSavePressed() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun HeroDisplay(
    uiState: FastTrackUiState,
    modifier: Modifier = Modifier
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 0
    }
    
    val displayAmount = if (uiState.currentInput.isEmpty()) {
        "0"
    } else {
        currencyFormat.format(uiState.currentInput.toLong()).replace(currencyFormat.currency?.symbol ?: "", "").trim()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Today's Total: ${currencyFormat.format(uiState.todayTotal)}",
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = displayAmount,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryList(
    expenses: List<Expense>,
    onDelete: (Expense) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()).apply { maximumFractionDigits = 0 } }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(expenses, key = { it.id }) { expense ->
            val dismissState = rememberDismissState(
                confirmValueChange = {
                    if (it == DismissValue.DismissedToStart) {
                        onDelete(expense)
                        true
                    } else false
                }
            )

            SwipeToDismiss(
                state = dismissState,
                background = {
                    val color by animateColorAsState(
                        if (dismissState.targetValue == DismissValue.DismissedToStart) Color.Red else Color.Transparent,
                        label = "dismissColor"
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp)
                            .background(color, RoundedCornerShape(12.dp))
                            .padding(end = 16.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (dismissState.targetValue == DismissValue.DismissedToStart) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                        }
                    }
                },
                dismissContent = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = expense.tag ?: "General", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                            Text(text = timeFormat.format(Date(expense.timestamp)), color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                        }
                        Text(
                            text = currencyFormat.format(expense.amount),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                },
                directions = setOf(DismissDirection.EndToStart)
            )
        }
    }
}

@Composable
fun CustomKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("⌫", "0", "Save")
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    KeypadButton(
                        text = key,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                "⌫" -> onBackspace()
                                "Save" -> onSave()
                                else -> onDigit(key.toInt())
                            }
                        },
                        isAction = key == "Save"
                    )
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAction: Boolean = false
) {
    Box(
        modifier = modifier
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isAction) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isAction) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontSize = if (isAction) 20.sp else 24.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
