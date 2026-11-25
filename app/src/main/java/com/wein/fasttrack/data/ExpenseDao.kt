package com.wein.fasttrack.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay ORDER BY timestamp DESC")
    fun getTodayExpenses(startOfDay: Long, endOfDay: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    suspend fun getAllExpenses(): List<Expense>

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpensesFlow(): Flow<List<Expense>>

    @Query("SELECT SUM(amount) FROM expenses WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay")
    fun getTodayTotal(startOfDay: Long, endOfDay: Long): Flow<Long?>

    @Query("SELECT * FROM expenses WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    suspend fun getExpensesBetween(startTime: Long, endTime: Long): List<Expense>

    @Query("SELECT * FROM expenses WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getExpensesBetweenFlow(startTime: Long, endTime: Long): Flow<List<Expense>>

    @Query("SELECT SUM(amount) FROM expenses WHERE timestamp >= :startTime AND timestamp <= :endTime")
    fun getTotalBetweenFlow(startTime: Long, endTime: Long): Flow<Long?>

    @Query(
        "SELECT * FROM expenses " +
            "WHERE (:query = '' OR COALESCE(note, '') LIKE '%' || :query || '%' OR COALESCE(tag, '') LIKE '%' || :query || '%') " +
            "AND (:tag IS NULL OR tag = :tag) " +
            "AND timestamp >= :startTime AND timestamp <= :endTime " +
            "ORDER BY timestamp DESC"
    )
    fun searchExpensesFlow(
        query: String,
        tag: String?,
        startTime: Long,
        endTime: Long
    ): Flow<List<Expense>>

    @Query("SELECT tag, SUM(amount) as totalAmount FROM expenses WHERE timestamp >= :startTime AND timestamp <= :endTime GROUP BY tag ORDER BY totalAmount DESC")
    suspend fun getTagTotalsBetween(startTime: Long, endTime: Long): List<TagTotal>

    @Query("SELECT tag, SUM(amount) as totalAmount FROM expenses WHERE timestamp >= :startTime AND timestamp <= :endTime GROUP BY tag ORDER BY totalAmount DESC")
    fun getTagTotalsBetweenFlow(startTime: Long, endTime: Long): Flow<List<TagTotal>>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: Long): Expense?

    @Query("SELECT * FROM expenses WHERE splitBillSessionId = :sessionId LIMIT 1")
    suspend fun getExpenseBySplitBillSessionId(sessionId: Long): Expense?

    @Query("DELETE FROM expenses WHERE splitBillSessionId = :sessionId")
    suspend fun deleteBySplitBillSessionId(sessionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()
}
