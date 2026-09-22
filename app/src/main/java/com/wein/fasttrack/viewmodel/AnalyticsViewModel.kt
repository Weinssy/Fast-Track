package com.wein.fasttrack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wein.fasttrack.data.TagTotal
import com.wein.fasttrack.repository.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class DailyTotal(
    val dayLabel: String,
    val totalAmount: Long
)

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val weeklyTotal: Long = 0L,
    val dailyAverage: Long = 0L,
    val dailyTotals: List<DailyTotal> = emptyList(),
    val tagTotals: List<TagTotal> = emptyList(),
    val maxDailyAmount: Long = 0L
)

class AnalyticsViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    fun loadAnalytics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Calculate past 7 days range
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endTime = calendar.timeInMillis

            calendar.add(Calendar.DAY_OF_YEAR, -6)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis

            // Fetch data
            val expenses = repository.getExpensesBetween(startTime, endTime)
            val tagTotals = repository.getTagTotalsBetween(startTime, endTime)

            // Aggregate daily totals
            val dailyMap = mutableMapOf<Int, Long>() // Day of year to total
            val dayLabels = mutableMapOf<Int, String>()
            
            val tempCalendar = Calendar.getInstance()
            tempCalendar.timeInMillis = startTime
            val dayFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())

            for (i in 0..6) {
                val dayOfYear = tempCalendar.get(Calendar.DAY_OF_YEAR)
                dailyMap[dayOfYear] = 0L
                dayLabels[dayOfYear] = dayFormat.format(tempCalendar.time)
                tempCalendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            var weeklyTotal = 0L
            expenses.forEach { expense ->
                tempCalendar.timeInMillis = expense.timestamp
                val dayOfYear = tempCalendar.get(Calendar.DAY_OF_YEAR)
                val currentTotal = dailyMap[dayOfYear] ?: 0L
                dailyMap[dayOfYear] = currentTotal + expense.amount
                weeklyTotal += expense.amount
            }

            val dailyTotals = dailyMap.map { (dayOfYear, total) ->
                DailyTotal(
                    dayLabel = dayLabels[dayOfYear] ?: "",
                    totalAmount = total
                )
            }

            val maxDailyAmount = dailyTotals.maxOfOrNull { it.totalAmount } ?: 0L
            val dailyAverage = if (dailyTotals.isNotEmpty()) weeklyTotal / 7 else 0L

            _uiState.value = AnalyticsUiState(
                isLoading = false,
                weeklyTotal = weeklyTotal,
                dailyAverage = dailyAverage,
                dailyTotals = dailyTotals,
                tagTotals = tagTotals,
                maxDailyAmount = maxDailyAmount
            )
        }
    }
}

class AnalyticsViewModelFactory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalyticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AnalyticsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
