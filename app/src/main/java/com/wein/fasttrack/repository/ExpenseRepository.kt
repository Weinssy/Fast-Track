package com.wein.fasttrack.repository

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.withTransaction
import com.wein.fasttrack.data.AppDatabase
import com.wein.fasttrack.data.Expense
import com.wein.fasttrack.data.ExpenseDao
import com.wein.fasttrack.data.RecurringExpenseDao
import com.wein.fasttrack.data.RecurringExpenseEntity
import com.wein.fasttrack.data.SplitBillDao
import com.wein.fasttrack.data.SplitBillSessionFull
import com.wein.fasttrack.data.TagDao
import com.wein.fasttrack.data.TagEntity
import com.wein.fasttrack.data.TagTotal
import com.wein.fasttrack.widget.FastTrackWidget
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.Locale

class ExpenseRepository(
    private val database: AppDatabase,
    private val expenseDao: ExpenseDao,
    private val tagDao: TagDao,
    private val recurringExpenseDao: RecurringExpenseDao = database.recurringExpenseDao(),
    private val splitBillDao: SplitBillDao = database.splitBillDao()
) {

    fun getAllTags(): Flow<List<TagEntity>> = tagDao.getAllTags()

    suspend fun insertTag(tag: TagEntity) = tagDao.insertTag(tag)

    suspend fun deleteTag(tag: TagEntity) {
        if (!tag.isPreset) {
            tagDao.deleteTag(tag)
        }
    }

    private fun getStartOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getEndOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    private fun getStartOfMonth(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getEndOfMonth(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    fun getTodayExpenses(): Flow<List<Expense>> {
        return expenseDao.getTodayExpenses(getStartOfDay(), getEndOfDay())
    }

    suspend fun getAllExpenses(): List<Expense> {
        return expenseDao.getAllExpenses()
    }

    fun getAllExpensesFlow(): Flow<List<Expense>> {
        return expenseDao.getAllExpensesFlow()
    }

    suspend fun getExpenseById(id: Long): Expense? {
        return expenseDao.getExpenseById(id)
    }

    suspend fun getExpensesBetween(startTime: Long, endTime: Long): List<Expense> {
        return expenseDao.getExpensesBetween(startTime, endTime)
    }

    fun getExpensesBetweenFlow(startTime: Long, endTime: Long): Flow<List<Expense>> {
        return expenseDao.getExpensesBetweenFlow(startTime, endTime)
    }

    fun getTotalBetweenFlow(startTime: Long, endTime: Long): Flow<Long?> {
        return expenseDao.getTotalBetweenFlow(startTime, endTime)
    }

    fun getCurrentMonthTotal(): Flow<Long?> {
        return expenseDao.getTotalBetweenFlow(getStartOfMonth(), getEndOfMonth())
    }

    fun searchExpensesFlow(
        query: String,
        tag: String?,
        startTime: Long,
        endTime: Long
    ): Flow<List<Expense>> {
        return expenseDao.searchExpensesFlow(query.trim(), tag, startTime, endTime)
    }

    suspend fun getTagTotalsBetween(startTime: Long, endTime: Long): List<TagTotal> {
        return expenseDao.getTagTotalsBetween(startTime, endTime)
    }

    fun getTagTotalsBetweenFlow(startTime: Long, endTime: Long): Flow<List<TagTotal>> {
        return expenseDao.getTagTotalsBetweenFlow(startTime, endTime)
    }

    fun getTodayTotal(): Flow<Long?> {
        return expenseDao.getTodayTotal(getStartOfDay(), getEndOfDay())
    }

    suspend fun insertExpense(expense: Expense): Long {
        return expenseDao.insert(expense)
    }

    suspend fun updateExpense(expense: Expense) {
        expenseDao.update(expense)
    }

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.delete(expense)
    }

    suspend fun getAllTagsSync(): List<TagEntity> = tagDao.getAllTagsSync()

    fun getAllRecurringExpenses(): Flow<List<RecurringExpenseEntity>> {
        return recurringExpenseDao.getAllFlow()
    }

    suspend fun getAllRecurringExpensesSync(): List<RecurringExpenseEntity> {
        return recurringExpenseDao.getAllSync()
    }

    suspend fun insertRecurringExpense(item: RecurringExpenseEntity): Long {
        return recurringExpenseDao.insert(item)
    }

    suspend fun updateRecurringExpense(item: RecurringExpenseEntity) {
        recurringExpenseDao.update(item)
    }

    suspend fun deleteRecurringExpense(item: RecurringExpenseEntity) {
        recurringExpenseDao.delete(item)
    }

    suspend fun deleteRecurringExpenseById(id: Long) {
        recurringExpenseDao.deleteById(id)
    }

    suspend fun applyDueRecurringExpenses(
        context: Context? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val currentYearMonth = String.format(Locale.US, "%04d-%02d", year, month)
        val actualMaxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val todayDay = calendar.get(Calendar.DAY_OF_MONTH)

        var appliedCount = 0
        database.withTransaction {
            val enabled = recurringExpenseDao.getEnabledSync()
            for (item in enabled) {
                if (item.amount <= 0L) continue
                if (item.lastAppliedYearMonth == currentYearMonth) continue
                val effectiveDay = item.dayOfMonth.coerceIn(1, actualMaxDay)
                if (todayDay >= effectiveDay) {
                    val note = item.note?.takeIf { it.isNotBlank() } ?: "Pengeluaran rutin"
                    val tag = item.tag.ifBlank { "Tagihan" }
                    expenseDao.insert(
                        Expense(
                            amount = item.amount,
                            timestamp = nowMillis,
                            tag = tag,
                            note = note
                        )
                    )
                    recurringExpenseDao.markApplied(item.id, currentYearMonth)
                    appliedCount += 1
                }
            }
        }

        if (appliedCount > 0 && context != null) {
            try {
                FastTrackWidget().updateAll(context.applicationContext)
            } catch (_: Exception) {
                // Ignore widget update failures when running outside an active launcher host
            }
        }
        return appliedCount
    }

    suspend fun restoreDatabase(
        tags: List<TagEntity>,
        expenses: List<Expense>,
        recurringExpenses: List<RecurringExpenseEntity> = emptyList(),
        splitBillSessions: List<SplitBillSessionFull> = emptyList()
    ) {
        database.withTransaction {
            expenseDao.deleteAllExpenses()
            recurringExpenseDao.deleteAll()
            splitBillDao.deleteAllSessions()
            tagDao.deleteNonPresetTags()

            tags.forEach { tagDao.insertTag(it) }
            expenses.forEach { expenseDao.insert(it) }
            recurringExpenses.forEach { recurringExpenseDao.insert(it) }

            for (full in splitBillSessions) {
                val sessionId = splitBillDao.insertSession(full.session)
                full.participants.forEach { participant ->
                    splitBillDao.insertParticipant(participant.copy(sessionId = sessionId))
                }
                full.items.forEach { item ->
                    splitBillDao.insertItem(item.copy(sessionId = sessionId))
                }
                full.settledKeys.forEach { settled ->
                    splitBillDao.insertSettled(settled.copy(sessionId = sessionId))
                }
            }
        }
    }
}
