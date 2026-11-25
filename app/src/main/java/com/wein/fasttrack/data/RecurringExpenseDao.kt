package com.wein.fasttrack.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringExpenseDao {
    @Query("SELECT * FROM recurring_expenses ORDER BY dayOfMonth ASC, id ASC")
    fun getAllFlow(): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses ORDER BY dayOfMonth ASC, id ASC")
    suspend fun getAllSync(): List<RecurringExpenseEntity>

    @Query("SELECT * FROM recurring_expenses WHERE isEnabled = 1 ORDER BY dayOfMonth ASC, id ASC")
    suspend fun getEnabledSync(): List<RecurringExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: RecurringExpenseEntity): Long

    @Update
    suspend fun update(item: RecurringExpenseEntity)

    @Delete
    suspend fun delete(item: RecurringExpenseEntity)

    @Query("DELETE FROM recurring_expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE recurring_expenses SET lastAppliedYearMonth = :yearMonth WHERE id = :id")
    suspend fun markApplied(id: Long, yearMonth: String)

    @Query("DELETE FROM recurring_expenses")
    suspend fun deleteAll()
}
