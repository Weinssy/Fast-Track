package com.wein.fasttrack.repository

import com.wein.fasttrack.data.Expense
import com.wein.fasttrack.data.ExpenseDao
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class ExpenseRepository(private val expenseDao: ExpenseDao) {

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

    fun getTodayTotal(): Flow<Long?> {
        return expenseDao.getTodayTotal(getStartOfDay(), getEndOfDay())
    }

    suspend fun insertExpense(expense: Expense) {
        expenseDao.insert(expense)
    }

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.delete(expense)
    }
}
