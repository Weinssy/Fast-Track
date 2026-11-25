package com.wein.fasttrack.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitBillDao {
    @Transaction
    @Query("SELECT * FROM split_bill_sessions ORDER BY updatedAt DESC, id DESC")
    fun observeAllSessions(): Flow<List<SplitBillSessionFull>>

    @Transaction
    @Query("SELECT * FROM split_bill_sessions ORDER BY updatedAt DESC, id DESC")
    suspend fun getAllSessionsSync(): List<SplitBillSessionFull>

    @Transaction
    @Query("SELECT * FROM split_bill_sessions WHERE id = :sessionId LIMIT 1")
    fun observeSession(sessionId: Long): Flow<SplitBillSessionFull?>

    @Transaction
    @Query("SELECT * FROM split_bill_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionFullSync(sessionId: Long): SplitBillSessionFull?

    @Query("SELECT * FROM split_bill_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): SplitBillSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SplitBillSessionEntity): Long

    @Update
    suspend fun updateSession(session: SplitBillSessionEntity)

    @Query("DELETE FROM split_bill_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("DELETE FROM split_bill_sessions")
    suspend fun deleteAllSessions()

    @Query("UPDATE split_bill_sessions SET updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun touchSession(sessionId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE split_bill_sessions SET syncedExpenseId = :expenseId WHERE id = :sessionId")
    suspend fun updateSyncedExpenseId(sessionId: Long, expenseId: Long?)

    @Query("SELECT * FROM split_bill_participants WHERE sessionId = :sessionId ORDER BY sortOrder ASC, id ASC")
    suspend fun getParticipantsForSession(sessionId: Long): List<SplitBillParticipantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipant(participant: SplitBillParticipantEntity): Long

    @Update
    suspend fun updateParticipant(participant: SplitBillParticipantEntity)

    @Query("DELETE FROM split_bill_participants WHERE id = :participantId")
    suspend fun deleteParticipantById(participantId: Long)

    @Query("UPDATE split_bill_participants SET isSelf = CASE WHEN id = :participantId THEN 1 ELSE 0 END WHERE sessionId = :sessionId")
    suspend fun setSelfParticipant(sessionId: Long, participantId: Long)

    @Query("SELECT * FROM split_bill_items WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getItemsForSession(sessionId: Long): List<SplitBillItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: SplitBillItemEntity): Long

    @Update
    suspend fun updateItem(item: SplitBillItemEntity)

    @Query("DELETE FROM split_bill_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long)

    @Query("SELECT * FROM split_bill_settled WHERE sessionId = :sessionId")
    suspend fun getSettledForSession(sessionId: Long): List<SplitBillSettledEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSettled(settled: SplitBillSettledEntity)

    @Query("DELETE FROM split_bill_settled WHERE sessionId = :sessionId AND settlementKey = :key")
    suspend fun deleteSettled(sessionId: Long, key: String)

    @Query("DELETE FROM split_bill_settled WHERE sessionId = :sessionId")
    suspend fun deleteAllSettledForSession(sessionId: Long)
}
