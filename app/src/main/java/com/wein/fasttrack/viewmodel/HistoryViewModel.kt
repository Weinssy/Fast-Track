package com.wein.fasttrack.viewmodel

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wein.fasttrack.data.Expense
import com.wein.fasttrack.data.TagEntity
import com.wein.fasttrack.repository.ExpenseRepository
import com.wein.fasttrack.widget.FastTrackWidget
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExpenseDateGroup(
    val dateKey: String,
    val headerLabel: String,
    val subtotal: Long,
    val expenses: List<Expense>
)

data class HistoryUiState(
    val searchQuery: String = "",
    val selectedTag: String? = null,
    val availableTags: List<TagEntity> = emptyList(),
    val groups: List<ExpenseDateGroup> = emptyList(),
    val totalFilteredAmount: Long = 0L,
    val totalFilteredCount: Int = 0,
    val undoDeletedExpense: Expense? = null
)

class HistoryViewModel(
    application: Application,
    private val repository: ExpenseRepository
) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedTag = MutableStateFlow<String?>(null)
    private val _undoDeletedExpense = MutableStateFlow<Expense?>(null)
    private var lastDeletedExpense: Expense? = null

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.getAllExpensesFlow(),
        repository.getAllTags(),
        _searchQuery,
        _selectedTag,
        _undoDeletedExpense
    ) { expenses, tags, query, selectedTag, undoEvent ->
        val filtered = filterExpenses(expenses, query, selectedTag)
        val groups = groupExpensesByDate(filtered)
        HistoryUiState(
            searchQuery = query,
            selectedTag = selectedTag,
            availableTags = tags,
            groups = groups,
            totalFilteredAmount = filtered.sumOf { it.amount },
            totalFilteredCount = filtered.size,
            undoDeletedExpense = undoEvent
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onTagFilterSelected(tag: String?) {
        _selectedTag.value = if (_selectedTag.value == tag) null else tag
    }

    fun clearTagFilter() {
        _selectedTag.value = null
    }

    fun updateExpense(expense: Expense, newAmount: Long, newTag: String, newNote: String?) {
        if (newAmount <= 0L) return
        viewModelScope.launch {
            val cleanedTag = newTag.trim().ifBlank { "Umum" }
            val cleanedNote = newNote?.trim()?.takeIf { it.isNotEmpty() }
            repository.updateExpense(
                expense.copy(
                    amount = newAmount,
                    tag = cleanedTag,
                    note = cleanedNote
                )
            )
            refreshWidget()
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            lastDeletedExpense = expense
            repository.deleteExpense(expense)
            _undoDeletedExpense.value = expense
            refreshWidget()
        }
    }

    fun undoDelete() {
        val deleted = lastDeletedExpense ?: return
        viewModelScope.launch {
            repository.insertExpense(deleted)
            lastDeletedExpense = null
            _undoDeletedExpense.value = null
            refreshWidget()
        }
    }

    fun onUndoHandled() {
        _undoDeletedExpense.value = null
    }

    private fun filterExpenses(
        expenses: List<Expense>,
        query: String,
        selectedTag: String?
    ): List<Expense> {
        val trimmedQuery = query.trim()
        val queryDigits = trimmedQuery.filter { it.isDigit() }

        return expenses.filter { expense ->
            val effectiveTag = expense.tag?.ifBlank { "Umum" } ?: "Umum"
            val tagMatches = selectedTag == null || effectiveTag == selectedTag
            if (!tagMatches) return@filter false

            if (trimmedQuery.isEmpty()) return@filter true

            val matchesNote = expense.note?.contains(trimmedQuery, ignoreCase = true) == true
            val matchesTag = effectiveTag.contains(trimmedQuery, ignoreCase = true)
            val matchesAmount = queryDigits.isNotEmpty() && expense.amount.toString().contains(queryDigits)
            val matchesSplitBadge = expense.splitBillSessionId != null &&
                "patungan".contains(trimmedQuery, ignoreCase = true)

            matchesNote || matchesTag || matchesAmount || matchesSplitBadge
        }
    }

    private fun groupExpensesByDate(expenses: List<Expense>): List<ExpenseDateGroup> {
        if (expenses.isEmpty()) return emptyList()

        val idLocale = Locale("id", "ID")
        val dateHeaderFormat = SimpleDateFormat("EEEE, d MMM yyyy", idLocale)

        val todayCal = Calendar.getInstance()
        val todayYear = todayCal.get(Calendar.YEAR)
        val todayDay = todayCal.get(Calendar.DAY_OF_YEAR)

        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayYear = yesterdayCal.get(Calendar.YEAR)
        val yesterdayDay = yesterdayCal.get(Calendar.DAY_OF_YEAR)

        val cal = Calendar.getInstance()
        val buckets = LinkedHashMap<String, MutableList<Expense>>()
        val labels = HashMap<String, String>()

        for (expense in expenses) {
            cal.timeInMillis = expense.timestamp
            val year = cal.get(Calendar.YEAR)
            val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
            val key = "$year-$dayOfYear"

            if (!labels.containsKey(key)) {
                labels[key] = when {
                    year == todayYear && dayOfYear == todayDay -> "Hari Ini"
                    year == yesterdayYear && dayOfYear == yesterdayDay -> "Kemarin"
                    else -> dateHeaderFormat.format(Date(expense.timestamp)).replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(idLocale) else it.toString()
                    }
                }
            }
            buckets.getOrPut(key) { mutableListOf() }.add(expense)
        }

        return buckets.map { (key, list) ->
            ExpenseDateGroup(
                dateKey = key,
                headerLabel = labels[key] ?: "",
                subtotal = list.sumOf { it.amount },
                expenses = list
            )
        }
    }

    private suspend fun refreshWidget() {
        runCatching {
            FastTrackWidget().updateAll(getApplication())
        }
    }
}

class HistoryViewModelFactory(
    private val application: Application,
    private val repository: ExpenseRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
