package com.wein.fasttrack.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wein.fasttrack.data.Expense
import com.wein.fasttrack.repository.ExpenseRepository
import com.wein.fasttrack.repository.UserPreferencesRepository
import com.wein.fasttrack.data.TagEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.wein.fasttrack.widget.FastTrackWidget
import androidx.glance.appwidget.updateAll

data class FastTrackUiState(
    val currentInput: String = "",
    val todayTotal: Long = 0L,
    val todayExpenses: List<Expense> = emptyList(),
    val isExporting: Boolean = false,
    val exportData: List<Expense>? = null,
    val selectedTag: String = "Umum",
    val availableTags: List<TagEntity> = emptyList(),
    val showAddTagDialog: Boolean = false,
    val tagToDelete: TagEntity? = null,
    val budgetCap: Long = 0L,
    val spendingStatus: SpendingStatus = SpendingStatus.NORMAL,
    val undoExpenseEvent: Expense? = null
)

enum class SpendingStatus {
    NORMAL, WARNING, EXCEEDED
}

class ExpenseViewModel(
    application: Application,
    private val repository: ExpenseRepository,
    private val userPrefs: UserPreferencesRepository
) : AndroidViewModel(application) {

    private val _currentInput = MutableStateFlow("")
    private val _isExporting = MutableStateFlow(false)
    private val _exportData = MutableStateFlow<List<Expense>?>(null)
    private val _selectedTag = MutableStateFlow("Umum")
    private val _showAddTagDialog = MutableStateFlow(false)
    private val _tagToDelete = MutableStateFlow<TagEntity?>(null)
    private val _undoExpenseEvent = MutableStateFlow<Expense?>(null)
    private var lastInsertedExpense: Expense? = null

    val uiState: StateFlow<FastTrackUiState> = combine(
        _currentInput,
        repository.getTodayTotal(),
        repository.getTodayExpenses(),
        _isExporting,
        _exportData,
        _selectedTag,
        repository.getAllTags(),
        _showAddTagDialog,
        _tagToDelete,
        userPrefs.dailyBudgetCap,
        _undoExpenseEvent
    ) { inputs ->
        val total = inputs[1] as? Long ?: 0L
        val cap = inputs[9] as Long
        
        val status = when {
            cap == 0L -> SpendingStatus.NORMAL
            total >= cap -> SpendingStatus.EXCEEDED
            total >= cap * 0.8 -> SpendingStatus.WARNING
            else -> SpendingStatus.NORMAL
        }

        FastTrackUiState(
            currentInput = inputs[0] as String,
            todayTotal = total,
            todayExpenses = inputs[2] as List<Expense>,
            isExporting = inputs[3] as Boolean,
            exportData = inputs[4] as List<Expense>?,
            selectedTag = inputs[5] as String,
            availableTags = inputs[6] as List<TagEntity>,
            showAddTagDialog = inputs[7] as Boolean,
            tagToDelete = inputs[8] as TagEntity?,
            budgetCap = cap,
            spendingStatus = status,
            undoExpenseEvent = inputs[10] as Expense?
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
                val expenseToInsert = Expense(amount = amount, tag = _selectedTag.value)
                val id = repository.insertExpense(expenseToInsert)
                val insertedExpense = expenseToInsert.copy(id = id)
                
                lastInsertedExpense = insertedExpense
                _undoExpenseEvent.value = insertedExpense
                
                _currentInput.value = ""
                _selectedTag.value = "Umum"
                FastTrackWidget().updateAll(getApplication())
            }
        }
    }

    fun onUndoEventHandled() {
        _undoExpenseEvent.value = null
    }

    fun onUndoLastExpense() {
        lastInsertedExpense?.let { expense ->
            viewModelScope.launch {
                repository.deleteExpense(expense)
                _currentInput.value = expense.amount.toString()
                _selectedTag.value = expense.tag ?: "Umum"
                lastInsertedExpense = null
                _undoExpenseEvent.value = null
                FastTrackWidget().updateAll(getApplication())
            }
        }
    }

    fun onTagSelected(tag: String) {
        _selectedTag.value = if (_selectedTag.value == tag) "Umum" else tag
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            FastTrackWidget().updateAll(getApplication())
        }
    }

    fun setShowAddTagDialog(show: Boolean) {
        _showAddTagDialog.value = show
    }

    fun addNewTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            viewModelScope.launch {
                repository.insertTag(TagEntity(name = trimmed))
                _selectedTag.value = trimmed
                _showAddTagDialog.value = false
            }
        }
    }

    fun setTagToDelete(tag: TagEntity?) {
        _tagToDelete.value = tag
    }

    fun deleteCustomTag(tag: TagEntity) {
        viewModelScope.launch {
            repository.deleteTag(tag)
            if (_selectedTag.value == tag.name) {
                _selectedTag.value = "Umum"
            }
            _tagToDelete.value = null
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

    fun setDailyBudgetCap(amount: Long) {
        viewModelScope.launch {
            userPrefs.setDailyBudgetCap(amount)
            FastTrackWidget().updateAll(getApplication())
        }
    }
}

class ExpenseViewModelFactory(
    private val application: Application,
    private val repository: ExpenseRepository,
    private val userPrefs: UserPreferencesRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExpenseViewModel(application, repository, userPrefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
