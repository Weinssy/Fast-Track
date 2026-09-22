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
    val todayExpenses: List<Expense> = emptyList(),
    val isExporting: Boolean = false,
    val exportData: List<Expense>? = null,
    val selectedTag: String = "General"
)

class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val _currentInput = MutableStateFlow("")
    private val _isExporting = MutableStateFlow(false)
    private val _exportData = MutableStateFlow<List<Expense>?>(null)
    private val _selectedTag = MutableStateFlow("General")

    val uiState: StateFlow<FastTrackUiState> = combine(
        _currentInput,
        repository.getTodayTotal(),
        repository.getTodayExpenses(),
        _isExporting,
        _exportData,
        _selectedTag
    ) { inputs ->
        FastTrackUiState(
            currentInput = inputs[0] as String,
            todayTotal = inputs[1] as? Long ?: 0L,
            todayExpenses = inputs[2] as List<Expense>,
            isExporting = inputs[3] as Boolean,
            exportData = inputs[4] as List<Expense>?,
            selectedTag = inputs[5] as String
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
                repository.insertExpense(Expense(amount = amount, tag = _selectedTag.value))
                _currentInput.value = ""
                _selectedTag.value = "General"
            }
        }
    }

    fun onTagSelected(tag: String) {
        _selectedTag.value = if (_selectedTag.value == tag) "General" else tag
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    fun onExportTriggered() {
        viewModelScope.launch {
            _isExporting.value = true
            val allExpenses = repository.getAllExpenses()
            _exportData.value = allExpenses
            _isExporting.value = false
        }
    }

    fun onExportHandled() {
        _exportData.value = null
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
