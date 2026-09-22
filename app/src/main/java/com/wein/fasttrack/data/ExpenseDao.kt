package com.wein.fasttrack.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.wein.fasttrack.data.TagTotal

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay ORDER BY timestamp DESC")
    fun getTodayExpenses(startOfDay: Long, endOfDay: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    suspend fun getAllExpenses(): List<Expense>

    @Query("SELECT SUM(amount) FROM expenses WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay")
    fun getTodayTotal(startOfDay: Long, endOfDay: Long): Flow<Long?>

    @Query("SELECT * FROM expenses WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    suspend fun getExpensesBetween(startTime: Long, endTime: Long): List<Expense>

    @Query("SELECT tag, SUM(amount) as totalAmount FROM expenses WHERE timestamp >= :startTime AND timestamp <= :endTime GROUP BY tag ORDER BY totalAmount DESC")
    suspend fun getTagTotalsBetween(startTime: Long, endTime: Long): List<TagTotal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)
}
