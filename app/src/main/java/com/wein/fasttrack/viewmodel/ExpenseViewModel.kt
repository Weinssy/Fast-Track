package com.wein.fasttrack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wein.fasttrack.data.Expense
import com.wein.fasttrack.repository.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FastTrackUiState(
    val currentInput: String = "",
    val todayTotal: Long = 0L,
    val todayExpenses: List<Expense> = emptyList()
)

class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val _currentInput = MutableStateFlow("")

    val uiState: StateFlow<FastTrackUiState> = combine(
        _currentInput,
        repository.getTodayTotal(),
        repository.getTodayExpenses()
    ) { input, total, expenses ->
        FastTrackUiState(
            currentInput = input,
            todayTotal = total ?: 0L,
            todayExpenses = expenses
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FastTrackUiState()
    )

    fun onDigitPressed(digit: Int) {
        _currentInput.update { current ->
            if (current.length < 10) { // Limit to reasonable amount
                if (current == "0") digit.toString() else current + digit
            } else {
                current
            }
        }
    }

    fun onBackspacePressed() {
        _currentInput.update { current ->
            if (current.isNotEmpty()) current.dropLast(1) else ""
        }
    }
    
    fun onClearInput() {
        _currentInput.value = ""
    }

    fun onSavePressed() {
        val amount = _currentInput.value.toLongOrNull()
        if (amount != null && amount > 0) {
            viewModelScope.launch {
                repository.insertExpense(Expense(amount = amount))
                _currentInput.value = ""
            }
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }
}

class ExpenseViewModelFactory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExpenseViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
