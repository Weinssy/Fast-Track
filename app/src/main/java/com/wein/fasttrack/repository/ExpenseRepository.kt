package com.wein.fasttrack.repository

import com.wein.fasttrack.data.Expense
import com.wein.fasttrack.data.ExpenseDao
import com.wein.fasttrack.data.TagDao
import com.wein.fasttrack.data.TagEntity
import com.wein.fasttrack.data.TagTotal
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

import com.wein.fasttrack.data.AppDatabase
import androidx.room.withTransaction

class ExpenseRepository(
    private val database: AppDatabase,
    private val expenseDao: ExpenseDao,
    private val tagDao: TagDao
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

    fun getTodayExpenses(): Flow<List<Expense>> {
        return expenseDao.getTodayExpenses(getStartOfDay(), getEndOfDay())
    }

    suspend fun getAllExpenses(): List<Expense> {
        return expenseDao.getAllExpenses()
    }

    suspend fun getExpensesBetween(startTime: Long, endTime: Long): List<Expense> {
        return expenseDao.getExpensesBetween(startTime, endTime)
    }

    suspend fun getTagTotalsBetween(startTime: Long, endTime: Long): List<TagTotal> {
        return expenseDao.getTagTotalsBetween(startTime, endTime)
    }

    fun getTodayTotal(): Flow<Long?> {
        return expenseDao.getTodayTotal(getStartOfDay(), getEndOfDay())
    }

    suspend fun insertExpense(expense: Expense): Long {
        return expenseDao.insert(expense)
    }

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.delete(expense)
    }

    suspend fun getAllTagsSync(): List<TagEntity> = tagDao.getAllTagsSync()

    suspend fun restoreDatabase(tags: List<TagEntity>, expenses: List<Expense>) {
        database.withTransaction {
            expenseDao.deleteAllExpenses()
            tagDao.deleteNonPresetTags()
            
            tags.forEach { tagDao.insertTag(it) }
            expenses.forEach { expenseDao.insert(it) }
        }
    }
}
