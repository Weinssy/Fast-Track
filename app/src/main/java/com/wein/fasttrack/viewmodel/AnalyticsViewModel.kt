package com.wein.fasttrack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wein.fasttrack.data.Expense
import com.wein.fasttrack.data.TagTotal
import com.wein.fasttrack.repository.ExpenseRepository
import com.wein.fasttrack.repository.UserPreferencesRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

enum class AnalyticsPeriod(val label: String) {
    SEVEN_DAYS("7 Hari"),
    THIS_MONTH("Bulan Ini"),
    LAST_MONTH("Bulan Lalu")
}

data class DailyTotal(
    val dayLabel: String,
    val totalAmount: Long
)

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val selectedPeriod: AnalyticsPeriod = AnalyticsPeriod.SEVEN_DAYS,
    val periodTotal: Long = 0L,
    val previousPeriodTotal: Long = 0L,
    val comparisonPercent: Int? = null,
    val dailyAverage: Long = 0L,
    val dailyTotals: List<DailyTotal> = emptyList(),
    val tagTotals: List<TagTotal> = emptyList(),
    val maxDailyAmount: Long = 0L,
    val currentMonthTotal: Long = 0L,
    val monthlyBudgetCap: Long = 0L
)

private data class PeriodWindow(
    val startTime: Long,
    val endTime: Long,
    val prevStartTime: Long,
    val prevEndTime: Long,
    val averageDivisorDays: Int
)

class AnalyticsViewModel(
    private val repository: ExpenseRepository,
    private val userPrefs: UserPreferencesRepository? = null
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(AnalyticsPeriod.SEVEN_DAYS)
    private val _refreshTick = MutableStateFlow(0)

    private val monthlyCapFlow = userPrefs?.monthlyBudgetCap ?: flowOf(0L)

    val uiState: StateFlow<AnalyticsUiState> = combine(
        repository.getAllExpensesFlow(),
        repository.getCurrentMonthTotal(),
        monthlyCapFlow,
        _selectedPeriod,
        _refreshTick
    ) { allExpenses, currentMonthTotal, monthlyCap, period, _ ->
        computeAnalyticsState(
            allExpenses = allExpenses,
            currentMonthTotal = currentMonthTotal ?: 0L,
            monthlyCap = monthlyCap,
            period = period
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyticsUiState(isLoading = true)
    )

    fun selectPeriod(period: AnalyticsPeriod) {
        _selectedPeriod.value = period
    }

    fun loadAnalytics() {
        _refreshTick.value += 1
    }

    private fun computeAnalyticsState(
        allExpenses: List<Expense>,
        currentMonthTotal: Long,
        monthlyCap: Long,
        period: AnalyticsPeriod
    ): AnalyticsUiState {
        val window = buildPeriodWindow(period)
        val currentExpenses = allExpenses.filter {
            it.timestamp in window.startTime..window.endTime
        }
        val prevExpenses = allExpenses.filter {
            it.timestamp in window.prevStartTime..window.prevEndTime
        }

        val periodTotal = currentExpenses.sumOf { it.amount }
        val previousPeriodTotal = prevExpenses.sumOf { it.amount }
        val comparisonPercent = if (previousPeriodTotal > 0L) {
            val diff = periodTotal - previousPeriodTotal
            ((diff.toDouble() / previousPeriodTotal.toDouble()) * 100.0).roundToInt()
        } else {
            null
        }

        val dailyAverage = if (window.averageDivisorDays > 0) {
            periodTotal / window.averageDivisorDays
        } else {
            0L
        }

        val dailyTotals = when (period) {
            AnalyticsPeriod.SEVEN_DAYS -> buildSevenDayBuckets(window.startTime, currentExpenses)
            AnalyticsPeriod.THIS_MONTH,
            AnalyticsPeriod.LAST_MONTH -> buildMonthWeekBuckets(window.startTime, currentExpenses)
        }

        val tagTotals = currentExpenses
            .groupBy { it.tag?.ifBlank { "Umum" } ?: "Umum" }
            .map { (tag, items) ->
                TagTotal(tag = tag, totalAmount = items.sumOf { it.amount })
            }
            .sortedByDescending { it.totalAmount }

        val maxDailyAmount = dailyTotals.maxOfOrNull { it.totalAmount } ?: 0L

        return AnalyticsUiState(
            isLoading = false,
            selectedPeriod = period,
            periodTotal = periodTotal,
            previousPeriodTotal = previousPeriodTotal,
            comparisonPercent = comparisonPercent,
            dailyAverage = dailyAverage,
            dailyTotals = dailyTotals,
            tagTotals = tagTotals,
            maxDailyAmount = maxDailyAmount,
            currentMonthTotal = currentMonthTotal,
            monthlyBudgetCap = monthlyCap
        )
    }

    private fun buildPeriodWindow(period: AnalyticsPeriod): PeriodWindow {
        return when (period) {
            AnalyticsPeriod.SEVEN_DAYS -> {
                val endCal = Calendar.getInstance().apply { setToEndOfDay() }
                val startCal = (endCal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -6)
                    setToStartOfDay()
                }
                val prevEndCal = (startCal.clone() as Calendar).apply {
                    add(Calendar.MILLISECOND, -1)
                }
                val prevStartCal = (startCal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -7)
                    setToStartOfDay()
                }
                PeriodWindow(
                    startTime = startCal.timeInMillis,
                    endTime = endCal.timeInMillis,
                    prevStartTime = prevStartCal.timeInMillis,
                    prevEndTime = prevEndCal.timeInMillis,
                    averageDivisorDays = 7
                )
            }
            AnalyticsPeriod.THIS_MONTH -> {
                val nowCal = Calendar.getInstance()
                val daysElapsed = nowCal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
                val startCal = (nowCal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    setToStartOfDay()
                }
                val endCal = (nowCal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    setToEndOfDay()
                }
                val prevStartCal = (startCal.clone() as Calendar).apply {
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, 1)
                    setToStartOfDay()
                }
                val prevEndCal = (prevStartCal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    setToEndOfDay()
                }
                PeriodWindow(
                    startTime = startCal.timeInMillis,
                    endTime = endCal.timeInMillis,
                    prevStartTime = prevStartCal.timeInMillis,
                    prevEndTime = prevEndCal.timeInMillis,
                    averageDivisorDays = daysElapsed
                )
            }
            AnalyticsPeriod.LAST_MONTH -> {
                val startCal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -1)
                    setToStartOfDay()
                }
                val daysInLastMonth = startCal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
                val endCal = (startCal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, daysInLastMonth)
                    setToEndOfDay()
                }
                val prevStartCal = (startCal.clone() as Calendar).apply {
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, 1)
                    setToStartOfDay()
                }
                val prevEndCal = (prevStartCal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    setToEndOfDay()
                }
                PeriodWindow(
                    startTime = startCal.timeInMillis,
                    endTime = endCal.timeInMillis,
                    prevStartTime = prevStartCal.timeInMillis,
                    prevEndTime = prevEndCal.timeInMillis,
                    averageDivisorDays = daysInLastMonth
                )
            }
        }
    }

    private fun buildSevenDayBuckets(startTime: Long, expenses: List<Expense>): List<DailyTotal> {
        val idLocale = Locale("id", "ID")
        val dayFormat = SimpleDateFormat("EEE", idLocale)
        val keys = ArrayList<String>(7)
        val labels = HashMap<String, String>(7)
        val totals = HashMap<String, Long>(7)

        val cal = Calendar.getInstance().apply { timeInMillis = startTime }
        repeat(7) {
            val key = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
            keys.add(key)
            labels[key] = dayFormat.format(Date(cal.timeInMillis))
            totals[key] = 0L
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        for (expense in expenses) {
            cal.timeInMillis = expense.timestamp
            val key = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
            if (totals.containsKey(key)) {
                totals[key] = (totals[key] ?: 0L) + expense.amount
            }
        }

        return keys.map { key ->
            DailyTotal(
                dayLabel = labels[key] ?: "",
                totalAmount = totals[key] ?: 0L
            )
        }
    }

    private fun buildMonthWeekBuckets(monthStartTime: Long, expenses: List<Expense>): List<DailyTotal> {
        val cal = Calendar.getInstance().apply { timeInMillis = monthStartTime }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val ranges = buildList {
            add(1..7 to "1-7")
            add(8..14 to "8-14")
            add(15..21 to "15-21")
            add(22..28 to "22-28")
            if (maxDay >= 29) {
                val label = if (maxDay == 29) "29" else "29-$maxDay"
                add(29..maxDay to label)
            }
        }
        val bucketTotals = LongArray(ranges.size)
        for (expense in expenses) {
            cal.timeInMillis = expense.timestamp
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val idx = ((day - 1) / 7).coerceIn(0, ranges.lastIndex)
            bucketTotals[idx] += expense.amount
        }

        return ranges.mapIndexed { index, (_, label) ->
            DailyTotal(
                dayLabel = label,
                totalAmount = bucketTotals[index]
            )
        }
    }

    private fun Calendar.setToStartOfDay() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun Calendar.setToEndOfDay() {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }
}

class AnalyticsViewModelFactory(
    private val repository: ExpenseRepository,
    private val userPrefs: UserPreferencesRepository? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalyticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AnalyticsViewModel(repository, userPrefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
